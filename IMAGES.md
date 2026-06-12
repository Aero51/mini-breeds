# Dog Images — Enhancement Guide

> **Status: implemented (2026-06-11).** The sections below were the design plan;
> the shipped code follows it with three simplifications: no custom Coil
> `ImageLoader` (the `coil-network-okhttp` artifact registers itself via
> ServiceLoader, so the default singleton works with zero DI changes), the URL
> is passed straight to `AsyncImage` without an `ImageRequest` builder, and the
> image fetch lives in its own `loadImage()` function called from `init` and
> `retry()` so it also runs when the breed cache is already warm.

How to extend the breed detail screen with real dog photos fetched from the
Dog CEO API. The existing architecture requires minimal changes — one new
dependency, one new DTO, one new service method, and a small addition to the
detail ViewModel and screen.

---

## Why it fits this app

The Dog CEO API provides image endpoints per breed at no extra cost — no
new backend, no authentication, no API key. Adding images turns the detail
screen from a plain text list into a visually engaging screen and demonstrates
async image loading with Coil, a standard real-world Android skill.

---

## API endpoints

### Single random image for a breed

```
GET https://dog.ceo/api/breed/{breed}/images/random
```

Response:

```json
{
  "message": "https://images.dog.ceo/breeds/bulldog-boston/n02096585_11195.jpg",
  "status": "success"
}
```

### Multiple random images for a breed

```
GET https://dog.ceo/api/breed/{breed}/images/random/{count}
```

Response:

```json
{
  "message": [
    "https://images.dog.ceo/breeds/bulldog-boston/n02096585_11195.jpg",
    "https://images.dog.ceo/breeds/bulldog-boston/n02096585_12345.jpg"
  ],
  "status": "success"
}
```

### Single random image for a sub-breed

```
GET https://dog.ceo/api/breed/{breed}/{subbreed}/images/random
```

---

## Implementation plan

### 1. Add Coil 3

```toml
# gradle/libs.versions.toml
[versions]
coil = "3.2.0"

[libraries]
coil-compose         = { group = "io.coil-kt.coil3", name = "coil-compose",         version.ref = "coil" }
coil-network-okhttp  = { group = "io.coil-kt.coil3", name = "coil-network-okhttp",  version.ref = "coil" }
```

```kotlin
// app/build.gradle.kts
implementation(libs.coil.compose)
implementation(libs.coil.network.okhttp)
```

`coil-network-okhttp` makes Coil fetch over OkHttp. Note: out of the box it
creates its **own** `OkHttpClient` — sharing the `networkModule` singleton
(timeouts, logging interceptor) requires the explicit `SingletonImageLoader`
wiring shown in the design decisions below, which the current implementation
does not yet apply.

---

### 2. DTO — the generic `DogResponseDto<T>` envelope

The image endpoint returns the same `message` + `status` envelope as the
breeds endpoint, so the shared generic DTO covers it with `T = String`:

```kotlin
// data/remote/dto/DogResponseDto.kt
@Serializable
data class DogResponseDto<T>(
    val message: T,
    val status: String = "",
)
```

---

### 3. New service endpoint — `DogApiService`

```kotlin
@GET("api/breed/{breed}/images/random")
suspend fun getBreedImage(@Path("breed") breed: String): DogResponseDto<String>
```

---

### 4. New repository method — `BreedRepository`

Interface:

```kotlin
suspend fun fetchBreedImageUrl(breedName: String): AppResult<String>
```

Implementation in `BreedRepositoryImpl`:

```kotlin
override suspend fun fetchBreedImageUrl(breedName: String): AppResult<String> =
    withContext(dispatchers.io) {
        safeApiCall { api.getBreedImage(breedName) }
            .unwrap()
            .onFailure { error -> Log.w(TAG, "Breed image fetch failed: $error") }
    }
```

`safeApiCall` covers all failure cases the same way it does for breed
fetches, and the shared `unwrap()` performs the same `"status"` field check —
no extra error handling needed.

---

### 5. Update `BreedDetailUiState.Content`

Add a nullable `imageUrl` field. `null` means no photo is available — still
loading or the fetch failed — so the screen falls back gracefully either way.

```kotlin
data class Content(
    val name: String,
    val subBreeds: List<String>,
    val isFavorite: Boolean,
    val imageUrl: String? = null,   // null = loading / failed / unavailable
) : BreedDetailUiState
```

---

### 6. Fetch image in `BreedDetailViewModel`

Fire the image fetch alongside the breed refresh so both arrive
independently. A failed image does not affect the breed `Content` state —
the screen degrades to the existing monogram avatar instead of showing
a full error.

```kotlin
private fun refresh() {
    viewModelScope.launch {
        refreshError.value = null
        repository.refreshBreeds().onFailure { refreshError.value = it }
    }
}

private fun loadImage() {
    viewModelScope.launch {
        repository.fetchBreedImageUrl(breedName).onSuccess { imageUrl.value = it }
    }
}

private val imageUrl = MutableStateFlow<String?>(null)
```

Combine `imageUrl` into `uiState` alongside the existing flows:

```kotlin
val uiState: StateFlow<BreedDetailUiState> =
    combine(
        repository.observeBreed(breedName),
        repository.favorites,
        refreshError,
        imageUrl,
    ) { breed, favorites, error, url ->
        when {
            breed != null -> BreedDetailUiState.Content(
                name = breed.name,
                subBreeds = breed.subBreeds,
                isFavorite = breed.name in favorites,
                imageUrl = url,
            )
            error != null -> BreedDetailUiState.Error(error)
            else -> BreedDetailUiState.Loading
        }
    }.stateIn(...)
```

---

### 7. Update `BreedDetailScreen` — replace the avatar with `AsyncImage`

Replace the large `BreedAvatar` in `BreedHeader` with a photo when the URL
is available, falling back to the monogram when it is not:

```kotlin
if (!content.imageUrl.isNullOrEmpty()) {
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(content.imageUrl)
            .crossfade(true)
            .build(),
        contentDescription = content.name,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .clip(MaterialTheme.shapes.large),
    )
} else {
    // existing BreedAvatar monogram (covers loading + error states)
    BreedAvatar(name = content.name, size = 88.dp)
}
```

Coil handles the in-flight loading state internally with a fade-in
via `crossfade(true)`. No manual `LoadingContent` is needed for the image.

---

## Design decisions

### One image, not a gallery (recommended starting point)

A single random image per breed keeps the implementation simple. A
horizontal `HorizontalPager` gallery is visually richer but requires
fetching multiple URLs, managing pager state, and more UI complexity.
Start with one image; the architecture supports upgrading later.

### Image fetch is independent from breed data fetch

A failed image fetch leaves `imageUrl` null and falls back to the monogram
avatar. The breed name, sub-breeds, and favorite toggle all remain fully
functional. This ensures a broken CDN or slow image server never degrades
the core experience.

### Coil could reuse the existing `OkHttpClient` (proposed, not yet applied)

By passing the Koin-provided `OkHttpClient` to Coil's `OkHttpNetworkFetcherFactory`,
image requests would share the same connection pool, timeouts, and debug
logging interceptor as API calls, instead of Coil's own default client. The
current implementation skips this wiring — `AsyncImage` runs on Coil's
default `ImageLoader` — which is acceptable at one image per detail visit but
is the first thing to add if image traffic grows:

```kotlin
// di/AppModules.kt — inside singleOf(::SingletonImageLoader) or Coil builder
SingletonImageLoader.setSafe {
    ImageLoader.Builder(it)
        .networkCachePolicy(CachePolicy.ENABLED)
        .components {
            add(OkHttpNetworkFetcherFactory(callFactory = { get() }))
        }
        .build()
}
```

### Sub-breed images (optional extension)

Each `SubBreedCard` can show a thumbnail using
`GET api/breed/{breed}/{subbreed}/images/random`. This multiplies the
number of network calls by the sub-breed count (up to ~10 per breed).
Coil's memory and disk cache means repeated visits to the same detail
screen cost nothing after the first load.

---

## Trade-offs summary

| Concern | Impact |
|---|---|
| Extra network call per detail open | One additional GET; ~100–300 ms on a good connection |
| Failed image | Falls back to monogram avatar — no UX regression |
| Disk cache | Coil caches images automatically; revisiting a breed is instant |
| New dependency | Coil 3 adds ~400 KB to the APK (before R8 shrinking) |
| Sub-breed images | N calls per detail open; acceptable with Coil caching |
| Testing | `BreedDetailViewModelTest` needs a new fake `fetchBreedImageUrl` path; image composable is tested by swapping the URL with a local test drawable |
