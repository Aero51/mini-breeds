# Network Layer

How the Mini-Breeds app reaches the Dog API — from the Retrofit service
definition down to the in-memory breed cache — with every failure converted
to a typed value before it leaves the data layer.

---

## Library stack

| Library | Version | Role |
|---|---|---|
| Retrofit | 3.0.0 | HTTP client / coroutine adapter |
| OkHttp (BOM) | 5.4.0 | Transport, connection pooling, interceptors |
| OkHttp `logging-interceptor` | 5.4.0 (BOM) | Request/response logging in debug builds |
| kotlinx.serialization JSON | 1.11.0 | JSON → Kotlin deserialization |
| `converter-kotlinx-serialization` | 3.0.0 | Retrofit ↔ kotlinx.serialization bridge |

Retrofit 3 ships its own coroutine support natively — no separate
`retrofit2-kotlin-coroutines-adapter` is needed.

---

## Dependency graph

```
DogApiService  (Retrofit-generated implementation)
      │
      ▼
OkHttpClient   (connection/read timeout, optional logging interceptor)
      │
      ▼
Retrofit       (base URL, converter factory)
      │
      ▼
Json           (kotlinx.serialization, ignoreUnknownKeys = true)
```

All four are Koin singletons in `networkModule` (`di/AppModules.kt`).

---

## Configuration — `networkModule`

```kotlin
private const val BASE_URL = "https://dog.ceo/"
private const val TIMEOUT_SECONDS = 10L
```

### `Json`

```kotlin
Json {
    ignoreUnknownKeys = true   // future API fields don't break parsing
    coerceInputValues = true   // null for non-nullable fields uses the default
}
```

`ignoreUnknownKeys` is the most important setting: the Dog API can add new
fields to the response without causing a `SerializationException` in older
app versions.

### `OkHttpClient`

```kotlin
OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(10, TimeUnit.SECONDS)
    .apply {
        if (BuildConfig.DEBUG) {
            addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
        }
    }
    .build()
```

- Both `connectTimeout` and `readTimeout` are set to **10 seconds**.
  Exceeding either maps to `AppError.Timeout` via `safeApiCall`.
- The `HttpLoggingInterceptor` is added **only in debug builds** — it logs
  the request method, URL, response code, and body size to Logcat.
  It is stripped entirely from the release APK by the R8 dead-code
  removal pass because the `BuildConfig.DEBUG` branch evaluates to `false`.

### `Retrofit`

```kotlin
Retrofit.Builder()
    .baseUrl(BASE_URL)
    .client(get())      // the OkHttpClient singleton
    .addConverterFactory(
        get<Json>().asConverterFactory("application/json".toMediaType())
    )
    .build()
```

`asConverterFactory` comes from the `converter-kotlinx-serialization` artifact.
It wires the `Json` singleton as the body deserializer, so the same
`ignoreUnknownKeys` / `coerceInputValues` settings apply everywhere.

---

## Service interface — `DogApiService`

```kotlin
interface DogApiService {
    @GET("api/breeds/list/all")
    suspend fun getAllBreeds(): BreedsResponseDto

    @GET("api/breed/{breed}/images/random")
    suspend fun getBreedImage(@Path("breed") breed: String): BreedImageDto
}
```

- Two `suspend fun` endpoints — Retrofit 3 handles coroutine dispatch
  natively without a `CallAdapter`.
- `getAllBreeds` resolves to `https://dog.ceo/api/breeds/list/all`;
  `getBreedImage` returns the URL of one random photo for the breed
  (used by the detail screen header).
- Retrofit generates the implementation at runtime:
  `get<Retrofit>().create(DogApiService::class.java)`.

---

## Wire-format DTO — `BreedsResponseDto`

```kotlin
@Serializable
data class BreedsResponseDto(
    val message: Map<String, List<String>> = emptyMap(),
    val status: String = "",
) {
    companion object {
        const val STATUS_SUCCESS = "success"
    }
}
```

Maps directly to the API's JSON shape:

```json
{
  "message": {
    "bulldog": ["boston", "french"],
    "hound":   ["afghan", "basset"]
  },
  "status": "success"
}
```

- `message` keys are breed names; values are (possibly empty) sub-breed lists.
- Default values (`emptyMap()`, `""`) let `coerceInputValues` handle
  missing or null fields without a crash.
- This DTO lives in `data/remote/dto/` and never leaves the data layer —
  it is converted to the domain `Breed` model in `BreedRepositoryImpl`.

---

## Exception mapping — `safeApiCall`

Every call to `getAllBreeds()` is wrapped in `safeApiCall`:

```kotlin
suspend fun <T> safeApiCall(block: suspend () -> T): AppResult<T> =
    try {
        AppResult.Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        AppResult.Failure(t.toAppError())
    }
```

`toAppError()` maps transport exceptions to typed `AppError` cases:

| Exception | `AppError` |
|---|---|
| `UnknownHostException` | `NoConnection` |
| `ConnectException` | `NoConnection` |
| `SocketTimeoutException` | `Timeout` |
| `InterruptedIOException` | `Timeout` |
| `HttpException` (Retrofit) | `Http(code)` |
| `SerializationException` | `Serialization` |
| Other `IOException` | `NoConnection` |
| Anything else | `Unknown(cause)` |

`CancellationException` is **never caught** — it is rethrown immediately so
structured concurrency and job cancellation work correctly.

---

## Repository — `BreedRepositoryImpl`

`BreedRepositoryImpl` is the only consumer of `DogApiService`. It sits between
the network and the ViewModels and handles three things:

### 1. Threading

```kotlin
override suspend fun refreshBreeds(): AppResult<List<Breed>> =
    withContext(dispatchers.io) {
        safeApiCall { api.getAllBreeds() }.toBreeds()
    }
```

The network call is always executed on the IO dispatcher, regardless of which
dispatcher the caller uses. `DispatcherProvider` is injected so tests can
substitute `TestCoroutineDispatcher`.

### 2. DTO → domain mapping

`toBreeds()` converts the raw DTO after `safeApiCall` returns:

```kotlin
// check the API's own status field even on HTTP 2xx
if (value.status != BreedsResponseDto.STATUS_SUCCESS) {
    AppResult.Failure(AppError.ApiStatus(value.status))
} else {
    AppResult.Success(
        value.message
            .map { (name, subBreeds) -> Breed(name = name, subBreeds = subBreeds) }
            .sortedBy { it.name }
    )
}
```

- An `"status": "error"` body becomes `AppError.ApiStatus` instead of
  silently-empty content.
- Breeds are **sorted alphabetically** by name at this step so the list is
  always in a consistent order regardless of the API's map iteration order.

### 3. In-memory cache

```kotlin
private val breedsCache = MutableStateFlow<List<Breed>?>(null)
override val cachedBreeds: StateFlow<List<Breed>?> = breedsCache.asStateFlow()
```

A successful fetch updates `breedsCache`. While the process is alive, subsequent
navigations (list → detail → back → detail) read from the cache without a
second network call. `cachedBreeds` is exposed as a read-only `StateFlow` so
ViewModels can react to updates without holding a direct reference to the
mutable state.

---

## Request lifecycle

```
BreedListViewModel.load()
        │  viewModelScope.launch
        ▼
BreedRepositoryImpl.refreshBreeds()
        │  withContext(Dispatchers.IO)
        ▼
safeApiCall { api.getAllBreeds() }
        │  Retrofit + OkHttp
        ▼
GET https://dog.ceo/api/breeds/list/all
        │
        ▼
BreedsResponseDto  (kotlinx.serialization)
        │
        ▼
.toBreeds()  →  AppResult<List<Breed>>
        │
        ▼
breedsCache.value = list       (on Success)
        │  or
refreshError / loadState       (on Failure)
        │
        ▼
uiState StateFlow  →  Compose screen recompose
```

---

## Test coverage

Network behaviour is tested against the **real** Retrofit + OkHttp + kotlinx.serialization
stack using `MockWebServer` (OkHttp 5's `mockwebserver3` package, builder-style API).
No mocking of the HTTP layer — converter and error-mapping behaviour are exercised
with actual bytes.

| Test class | Scenarios covered |
|---|---|
| `BreedsResponseDtoTest` | Valid JSON parsing, unknown-key tolerance, malformed input |
| `SafeApiCallTest` | Each exception type → correct `AppError`; `CancellationException` rethrown |
| `BreedRepositoryImplTest` | Success mapping + sort order + cache update; HTTP 500 → `Http(500)`; garbage body → `Serialization`; `"status":"error"` → `ApiStatus`; connection refused → `NoConnection`; timeout → `Timeout` |

MockWebServer is created fresh per test and started on a random port; the
Retrofit `BASE_URL` is pointed at `http://localhost:<port>/` so no real network
traffic leaves the test process.

---

## Known local environment caveat

On the development machine Avast Antivirus Web Shield HTTPS scanning intercepts
TLS connections from the Android emulator with its own root CA (trusted by
Windows but not by the Android trust store). This causes `ERR_CERT_AUTHORITY_INVALID`
/ `AppError.NoConnection` in the emulator even though the app's network code
is correct. See `CLAUDE.md` for the workaround.
