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
    suspend fun getAllBreeds(): DogResponseDto<Map<String, List<String>>>

    @GET("api/breed/{breed}/images/random")
    suspend fun getBreedImage(@Path("breed") breed: String): DogResponseDto<String>
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

## Wire-format DTO — `DogResponseDto<T>`

Every dog.ceo endpoint returns the same envelope — a `message` payload plus a
`status` field — so one generic DTO covers them all:

```kotlin
@Serializable
data class DogResponseDto<T>(
    val message: T,
    val status: String = "",
) {
    companion object {
        const val STATUS_SUCCESS = "success"
    }
}
```

For `getAllBreeds`, `T` is `Map<String, List<String>>` and maps directly to
the API's JSON shape:

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
  For `getBreedImage`, `T` is `String` — the URL of one random photo.
- `message` has no default, so a body without one fails parsing and surfaces
  as `AppError.Serialization` instead of masquerading as an empty result;
  a missing `status` falls back to `""` (which then fails the status check).
- This DTO lives in `data/remote/dto/` and never leaves the data layer —
  it is unwrapped and converted to the domain `Breed` model in
  `BreedRepositoryImpl`.

---

## Exception mapping — `safeApiCall`

Every API call (`getAllBreeds()` and `getBreedImage()`) is wrapped in `safeApiCall`:

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
        safeApiCall { api.getAllBreeds() }
            .unwrap()
            .map { ... }   // DTO payload → sorted List<Breed>
            .onSuccess { breeds -> breedsCache.value = breeds }
            .onFailure { error -> Log.w(TAG, "Breed refresh failed: $error") }
    }
```

The network call is always executed on the IO dispatcher, regardless of which
dispatcher the caller uses. `DispatcherProvider` is injected so tests can
substitute `TestCoroutineDispatcher`.

### 2. DTO → domain mapping

`unwrap()` validates the envelope after `safeApiCall` returns:

```kotlin
// check the API's own status field even on HTTP 2xx
private fun <T> AppResult<DogResponseDto<T>>.unwrap(): AppResult<T> =
    when (this) {
        is AppResult.Failure -> this
        is AppResult.Success ->
            if (value.status != DogResponseDto.STATUS_SUCCESS) {
                AppResult.Failure(AppError.ApiStatus(value.status))
            } else {
                AppResult.Success(value.message)
            }
    }
```

- A `"status": "error"` body becomes `AppError.ApiStatus` instead of
  silently-empty content. Being generic, the same function serves both
  endpoints.
- `refreshBreeds()` then uses `AppResult.map` to turn the unwrapped breed map
  into domain `Breed` objects, **sorted alphabetically** by name so the list
  is always in a consistent order regardless of the API's map iteration order.
- `fetchBreedImageUrl()` just unwraps — the payload already is the URL string.
  Image fetches are **not cached** in the repository — each call may return a
  different random photo; Coil's own memory/disk cache prevents re-downloading
  the same URL.

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
DogResponseDto<Map<String, List<String>>>  (kotlinx.serialization)
        │
        ▼
.unwrap().map { … }  →  AppResult<List<Breed>>
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
| `DogResponseDtoTest` | Valid JSON parsing (map and string payloads), unknown-key tolerance, missing/malformed input |
| `SafeApiCallTest` | Each exception type → correct `AppError`; `CancellationException` rethrown |
| `BreedRepositoryImplTest` | Success mapping + sort order + cache update; HTTP 500 → `Http(500)`; garbage body → `Serialization`; `"status":"error"` → `ApiStatus`; connection refused → `NoConnection`; timeout → `Timeout`; image fetch success / `"status":"error"` / HTTP 404 |

MockWebServer is created fresh per test and started on a random port; the
Retrofit `BASE_URL` is pointed at `http://localhost:<port>/` so no real network
traffic leaves the test process.

---

## Image loading — Coil

The detail screen's breed photo is rendered by Coil 3 (`AsyncImage`). The
repository only fetches the photo's **URL** through the Retrofit stack above;
downloading and caching the image bytes is Coil's job. The
`coil-network-okhttp` artifact registers its network fetcher automatically via
ServiceLoader, so no custom `ImageLoader` or DI wiring exists — Coil's default
memory and disk caches apply.

---

## Known local environment caveat

On the development machine Avast Antivirus Web Shield HTTPS scanning intercepts
TLS connections from the Android emulator with its own root CA (trusted by
Windows but not by the Android trust store). This causes `ERR_CERT_AUTHORITY_INVALID`
/ `AppError.NoConnection` in the emulator even though the app's network code
is correct. The same interception breaks Gradle dependency downloads on the
host (`PKIX path building failed`). See `CLAUDE.md` for both workarounds.
