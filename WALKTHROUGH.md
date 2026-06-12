# Code walkthrough — every class explained simply

A plain-English tour of every class and function in the app, what it does, and
why that approach was chosen over the alternatives.  Read it top to
bottom — it goes in dependency order, so nothing refers to something you
haven't met yet.

The packages form a one-way street:

```
core  ←  domain  ←  data  ←  ui
(no deps)                    (knows everything below it)
```

`core` knows nothing about Android or any library. `ui` may use everything.
Nothing ever points the other way — that's what keeps the app testable.

---

## `core` — tiny building blocks with zero dependencies

### `AppError` (sealed interface)

**What it does:** Lists every way the app can fail, as a fixed set of cases:
`NoConnection`, `Timeout`, `Http(code)`, `Serialization`, `ApiStatus(status)`,
`Unknown(cause)`.

**Why this approach:** A `sealed interface` means the compiler knows *all*
possible cases. When a `when` expression handles an `AppError`, forgetting a
case is a compile error, not a runtime surprise.

**Alternatives rejected:**
- *Raw exceptions* — nothing forces a caller to catch them; a forgotten
  `try/catch` crashes the app at runtime.
- *An enum* — enums can't carry data; we need `Http` to carry its status code
  and `Unknown` to carry the original throwable.
- *Error strings* — can't be matched exhaustively and would leak English text
  into layers that shouldn't know about UI wording.

### `AppResult<T>` (sealed interface)

**What it does:** The return type for anything that can fail. It is either
`Success(value)` or `Failure(error: AppError)` — never both, never neither.

Three small extension functions make it pleasant to use without nested `when`s:

- `map { }` — transform the success value, pass failures through untouched.
- `onSuccess { }` — run a side-effect (like "store in cache") only on success.
- `onFailure { }` — run a side-effect (like "log it") only on failure.

Each returns the result again, so they chain:
`fetch().map { … }.onSuccess { … }.onFailure { … }`.

**Why this approach:** Returning a result instead of throwing means the *type
signature tells you the call can fail*, and the compiler makes you handle it.

**Alternatives rejected:**
- *Kotlin's built-in `kotlin.Result`* — its failure side is `Throwable`, which
  is exactly the untyped "anything could be in here" we're trying to avoid.
  Ours carries a typed `AppError`.
- *Arrow's `Either`* — a whole functional-programming library for what is 20
  lines of code here. Too heavy for one use case.
- *Throwing exceptions* — see `AppError` above.

### `DispatcherProvider` / `DefaultDispatcherProvider`

**What it does:** A tiny interface with the three coroutine dispatchers
(`io`, `default`, `main`). Production code gets `DefaultDispatcherProvider`,
which just hands back the real `Dispatchers.IO` etc.

**Why this approach:** If code called `Dispatchers.IO` directly, unit tests
would run on real background thread pools — slow and flaky. With the
interface, tests inject a `TestDispatcher` and control time precisely
("advance the virtual clock until all coroutines finish").

**Alternative rejected:** Hardcoding `Dispatchers.IO` — works in production,
untestable on the JVM. (Honest note: only `io` currently has production
callers; `default` and `main` are there because this is the conventional
shape of the interface.)

---

## `domain` — what the app is about, in plain Kotlin

### `Breed` (data class)

**What it does:** A dog breed: its `name` and its list of `subBreeds`. This is
the object the UI works with.

**Why this approach:** It exists *separately* from the network DTO so that the
API's JSON shape never leaks into screens. If dog.ceo renamed a field
tomorrow, only the data layer would change.

**Alternative rejected:** Passing the DTO straight to the UI — fewer classes,
but every screen would now depend on the exact JSON format of a third-party
API.

### `BreedRepository` (interface)

**What it does:** The contract between the UI and "wherever data comes from":

- `cachedBreeds` — the last successfully fetched list (null before first load),
  observable as a `StateFlow`.
- `refreshBreeds()` — fetch from network, update the cache, return the result.
- `observeBreed(name)` — watch one breed from the cache.
- `fetchBreedImageUrl(name)` — get one random photo URL for a breed.
- `favorites` — the set of favorited breed names, observable.
- `toggleFavorite(name)` — flip a breed's favorite status.

**Why an interface when there's only one implementation:** Two concrete
payoffs. (1) ViewModel unit tests use a hand-written `FakeBreedRepository`
instead of real networking. (2) The instrumented `NavigationTest` swaps the
real repository for a fake through dependency injection. Without the
interface, neither is possible.

**Alternative rejected:** Just using the concrete class — one less file, but
every test would need a real HTTP server.

---

## `data/remote` — talking to the dog.ceo API

### `DogResponseDto<T>` (data class)

**What it does:** Mirrors the JSON envelope every dog.ceo endpoint returns:
`{ "message": <payload>, "status": "success" }`. The generic `T` is the
payload type — a map of breeds for the list endpoint, a URL string for the
image endpoint.

**Why this approach:** Both endpoints share the envelope, so one generic class
replaces two near-identical ones (DRY). `message` deliberately has *no*
default value: a response without a payload should fail parsing loudly rather
than quietly pretend the dog list is empty. `status` defaults to `""`, which
then fails the status check downstream.

**Alternatives rejected:**
- *One DTO class per endpoint* — that's how this code originally looked; the
  two classes and their two mappers were word-for-word duplicates.
- *Parsing into the domain `Breed` directly* — couples the domain model to
  the wire format (see `Breed` above).

### `DogApiService` (interface)

**What it does:** Declares the two HTTP endpoints as Kotlin functions.
Retrofit reads the annotations (`@GET`, `@Path`) and generates the actual
HTTP-calling implementation at runtime.

**Why Retrofit:** It's the de-facto standard, the `suspend fun` support means
no callback code, and the team reviewing this assessment will know it on
sight.

**Alternatives rejected:**
- *Ktor Client* — perfectly good, but its multiplatform strengths buy nothing
  in an Android-only app.
- *Raw OkHttp* — you'd hand-write URL building, response parsing, and
  threading that Retrofit does for free.

### `safeApiCall` + `Throwable.toAppError()` (functions)

**What it does:** `safeApiCall { api.getAllBreeds() }` runs the network call
and catches *everything that can go wrong*, translating each exception into
the matching `AppError` case (e.g. `UnknownHostException` → `NoConnection`,
`HttpException` → `Http(code)`). It is the **single place** in the whole app
where transport exceptions are caught.

One special case: `CancellationException` is re-thrown, never swallowed.
That exception is how Kotlin coroutines cancel themselves (e.g. when the user
leaves the screen); catching it would break cancellation silently.

**Why this approach:** One catch point means one place to audit, one place to
test, and zero `try/catch` blocks scattered through the codebase.

**Alternative rejected:** `try/catch` in every repository function — the same
mapping logic copy-pasted, guaranteed to drift apart over time.

---

## `data/local` — remembering favorites

### `FavoritesDataSource` (interface)

**What it does:** The contract for favorite storage: an observable
`favorites: Flow<Set<String>>` and a `toggle(breedName)` function. Same
interface-for-testability story as `BreedRepository`.

### `DataStoreFavoritesDataSource` (class)

**What it does:** Implements the contract with Jetpack **DataStore**
(Preferences flavor). Favorites are one `Set<String>` under one key.

- `favorites` — DataStore's data as a Flow, mapped to the set. If the file on
  disk is corrupted (`IOException`), it degrades to "no favorites" instead of
  crashing; any *other* exception is a real bug and is allowed to propagate.
- `toggle(breedName)` — one atomic read-modify-write: remove the name if
  present, add it if not.

**Why DataStore:**
- *vs `SharedPreferences`* — SharedPreferences has a synchronous API that can
  block the UI thread and no error reporting. DataStore is coroutine-based,
  transactional, and exposes data as a Flow, so the UI updates automatically
  when a favorite changes.
- *vs Room (SQLite)* — a database for a single set of strings is a truck to
  deliver a letter. Room earns its place when you have relational data or
  queries; favorites are neither.

---

## `data/repository` — the one place everything meets

### `BreedRepositoryImpl` (class)

**What it does:** The real `BreedRepository`. It owns an in-memory cache
(`MutableStateFlow<List<Breed>?>`) and glues together the API service and the
favorites data source.

Function by function:

- `refreshBreeds()` — runs on the IO dispatcher; chains
  `safeApiCall { api.getAllBreeds() }` → `unwrap()` → `map` (turn the raw
  name→sub-breeds map into sorted `Breed` objects) → `onSuccess` (store in
  cache) → `onFailure` (log a warning). The chain reads like the actual
  sequence of events.
- `observeBreed(name)` — the cache Flow, filtered to one breed. Returns null
  while the cache is cold; the detail screen treats that as "loading".
- `fetchBreedImageUrl(name)` — same `safeApiCall` → `unwrap()` pattern;
  deliberately *not* cached, because the endpoint returns a random photo each
  time and Coil (the image library) already caches the downloaded bytes.
- `favorites` / `toggleFavorite(name)` — straight delegation to the favorites
  data source. The repository exposes them so the UI has *one* data door to
  knock on, not two.
- `unwrap()` (private) — checks dog.ceo's own `"status"` field. The API can
  return HTTP 200 with `"status": "error"` in the body; without this check
  that would look like a successful-but-empty response. Generic, so it serves
  both endpoints.

**Why an in-memory cache instead of a database:** The assessment needs the
list to survive *navigation* (list → detail → back), not *process death*. A
`StateFlow` does that in three lines. Room would add a schema, a DAO, and
migrations to solve a problem the app doesn't have. The trade-off is honest:
kill the process and the next launch re-fetches — acceptable for ~100 names.
The detail ViewModel explicitly handles the cold-cache-after-process-death
case (see below).

**Why breeds are sorted here:** JSON map ordering is not guaranteed. Sorting
once at the source means every consumer sees a stable order for free.

---

## `di` — wiring it all together with Koin

### `AppModules.kt` (three Koin modules)

**What it does:** Tells Koin how to build each object, in three groups:

- `networkModule` — the `Json` parser (configured to ignore unknown JSON keys
  so an API addition doesn't crash old app versions), the `OkHttpClient`
  (10-second timeouts, request logging in debug builds only), `Retrofit`, and
  the `DogApiService`.
- `dataModule` — `DispatcherProvider`, the app-wide DataStore instance, the
  favorites data source, and the repository. All `single` (one shared
  instance each).
- `viewModelModule` — both ViewModels via `viewModelOf`, which integrates
  with Android's ViewModel lifecycle (survives rotation) and injects
  `SavedStateHandle` automatically where a ViewModel asks for it.

The file also holds the one `preferencesDataStore` delegate — the DataStore
library requires exactly one instance per file name, so it lives in exactly
one place.

**Why Koin over Hilt:** Koin is plain Kotlin — no annotation processors, no
generated code, faster builds, and errors are debuggable stack traces. Hilt
brings compile-time *validation* of the graph, which is valuable on big
multi-module projects with many developers; on a one-module app with ~10
dependencies, that safety is instead covered by a 5-line unit test
(`KoinModulesTest` calls Koin's `verify()`). Honest trade-off: Hilt finds
wiring mistakes at compile time, Koin at test time.

**Why DI at all instead of `object` singletons:** Tests must be able to
substitute pieces (fake repository, test dispatchers). Hardcoded singletons
can't be swapped.

---

## `ui/common` — pieces shared by both screens

### `UiErrorMessage` + `AppError.toUiMessage()` (in `UiError.kt`)

**What it does:** Maps each `AppError` case to an Android string resource (plus
format arguments — e.g. `Http` passes its status code into "Server error
(HTTP %d)"). The screen turns the resource into actual text.

**Why this approach:** String resources make messages translatable and keep
UI wording out of the data layer. The mapping lives in `ui` because *what to
tell the user* is a presentation decision; the repository shouldn't know
English.

**Alternative rejected:** Putting message strings inside `AppError` — the
core layer would then depend on Android resources, breaking the "core has no
dependencies" rule and making JVM tests harder.

### `LoadingContent` / `ErrorContent` (in `StateContent.kt`)

**What it does:** The two full-screen states both screens share: a centered
spinner, and an error panel (dog emoji, localized message, Retry button).
`CommonTestTags` holds the stable IDs UI tests use to find these elements.

**Why this approach:** Both screens need identical loading/error UI. Writing
it once means the screens can't drift apart visually, and tests can target
one set of tags.

### `BreedAvatar` (composable)

**What it does:** A colored circle with the breed's first letter — shown
wherever a breed has no photo. The background hue is derived from the breed
name's hash code, so "akita" is always the same color everywhere, and the
saturation/lightness adapt to dark mode. The computed colors are `remember`ed
so they aren't recalculated on every recomposition.

**Why this approach:** The list API gives no images, and fetching a photo per
row would be ~100 extra network calls on launch. A deterministic monogram
gives each row identity for free, offline, instantly.

### `FavoriteIcon` (composable)

**What it does:** The heart icon. Filled + tinted when favorited, outlined
when not, with a small spring bounce and color fade on change. The content
description changes too ("Add/Remove … to favorites") so screen readers and
UI tests both understand it.

**Why this approach:** Both the list rows and the detail toolbar show the
same heart; one composable keeps behavior and accessibility consistent.

---

## `ui/navigation` — moving between screens

### `Routes.kt` — `BreedListRoute`, `BreedDetailRoute`

**What it does:** The two destinations as `@Serializable` types. The detail
route carries `breedName` as a typed field. Navigating is
`navController.navigate(BreedDetailRoute("akita"))` — no string URLs.

**Why typed routes:** With old string routes (`"detail/{breedName}"`), a typo
compiles fine and crashes at runtime. With typed routes the compiler checks
everything.

**The `ARG_BREED_NAME` constant — an important exception:** typed navigation
stores route arguments in `SavedStateHandle` under their property names. The
official way to read them back, `savedStateHandle.toRoute()`, needs a real
Android `Bundle` and **silently returns garbage in JVM unit tests**. So the
ViewModel reads `savedStateHandle["breedName"]` by key instead, and this
constant documents that contract. Pragmatism over API purity — do not
"modernize" this.

### `MiniBreedsNavHost` (composable)

**What it does:** Declares the navigation graph: list screen (start
destination) and detail screen, and wires the two callbacks — "breed clicked →
navigate to detail" and "back pressed → pop".

**Why navigation logic lives here and not inside screens:** Screens receive
plain lambdas (`onBreedClick`) and never see the `NavController`. That keeps
them previewable in Android Studio and testable without navigation
infrastructure.

---

## `ui/breedlist` — the home screen

### `BreedListUiState` + `BreedRowUi`

**What it does:** Everything the list screen can show, as a sealed type:
`Loading`, `Error(error)`, or `Content(rows, noResultsForQuery)`. Each
`BreedRowUi` is one row, pre-baked: name, sub-breed count, favorite flag.

**Why one sealed state instead of separate `isLoading`/`error`/`data` fields:**
Separate fields allow impossible combinations (loading *and* error at once?).
A sealed type makes illegal states unrepresentable, and the screen is a dumb
`when` over it. `noResultsForQuery` exists as an explicit flag because "your
search matched nothing" and "the API returned an empty list" need different
UI, and only the ViewModel can tell them apart.

### `BreedListViewModel`

**What it does:** Owns the list screen's state. Internally:

- `loadState` (private sealed type) — where the network fetch currently is:
  `Loading`, `Loaded(breeds)`, or `Failed(error)`.
- `query` — the search text, kept *separate* from `loadState` so the user's
  typed text survives a failed load and a retry.
- `uiState` — the public output: `combine(loadState, query, favorites)` runs
  whenever *any* of the three changes, filters the breeds by the trimmed
  query (case-insensitive), merges in the favorite flags, and emits a fresh
  `BreedListUiState`. Declarative: state is *derived*, never patched.
- `load()` / `retry()` — trigger the fetch; `init` calls it once on creation.
- `onQueryChange(text)` / `onToggleFavorite(name)` — handle user input.

`stateIn(..., WhileSubscribed(5_000))` means the combine pipeline runs only
while a screen is actually watching, and survives a configuration change
(rotation takes < 5s) without restarting.

**Why no debounce on search:** Filtering ~100 in-memory strings takes
microseconds. Debouncing exists to spare a server or an expensive query;
adding it here would just make the UI feel laggy.

**Alternatives rejected:** Filtering in the composable (untestable without an
emulator, recomputes on every recomposition) or a full MVI framework with
reducers and intents (a lot of ceremony for two screens).

### `BreedListScreen.kt`

**What it does:** The visual half, split in two:

- `BreedListRoute` — the *stateful* wrapper: gets the ViewModel from Koin,
  collects its flows lifecycle-aware, passes plain values + callbacks down.
- `BreedListScreen` — the *stateless* screen: takes values, emits callbacks,
  holds zero state of its own. Inside it: `SearchField` (pill-shaped text
  field with clear button), `BreedList` (a `LazyColumn` — only renders rows
  actually on screen; `key = name` + `animateItem()` give smooth reorder
  animations when the filter changes), `BreedCard` (avatar + name + sub-breed
  count + heart), and `EmptySearchResults` (the 🐾 "nothing matched" state).
  `BreedListTestTags` holds the IDs UI tests target.

**Why the Route/Screen split:** A stateless screen can be rendered in a
`@Preview` and driven directly in UI tests with hand-made states — no Koin, no
network, no ViewModel. This is the standard "state hoisting" pattern and the
single biggest testability win in the UI layer.

---

## `ui/breeddetail` — the detail screen

### `BreedDetailUiState`

Same sealed pattern as the list: `Loading`, `Error`, or `Content(name,
subBreeds, isFavorite, imageUrl)`. `imageUrl` is null until a photo fetch
succeeds — null simply means "show the monogram avatar".

### `BreedDetailViewModel`

**What it does:**

- Reads `breedName` from `SavedStateHandle` (by key — see the navigation
  section for why), failing fast with a clear message if it's missing.
- `uiState` — `combine` of four flows: the observed breed from the cache,
  favorites, `refreshError`, and `imageUrl`. Priority: breed available →
  `Content`; no breed but an error → `Error`; otherwise `Loading`.
- `init` — if the cache is cold (first launch straight into this screen, or
  the process was killed and restored), it triggers `refresh()` so the screen
  doesn't hang on `Loading` forever. With a warm cache it does nothing — no
  redundant network call.
- `refresh()` / `retry()` — re-run the fetch; failure lands in `refreshError`.
- `loadImage()` — fetches the photo URL **independently** of the breed data.
  On success the URL flows into `Content`; on failure `imageUrl` just stays
  null and the monogram remains. A broken image CDN must never replace
  working breed content with an error screen.
- `onToggleFavorite()` — delegates to the repository.

**Why observe the cache instead of passing the whole breed through
navigation:** Navigation arguments should be IDs, not objects — they're
serialized into a size-limited Bundle, and a copy passed by argument would go
stale when favorites change. Observing by name keeps a single source of truth.

### `BreedDetailScreen.kt`

Same Route/Screen split. `BreedDetailScreen` shows a top bar (back arrow;
heart in the actions — only when content is loaded), then `Loading` / `Error`
/ `BreedDetailContent`. `BreedHeader` shows the photo via Coil's `AsyncImage`
when a URL exists, otherwise the big monogram avatar, plus the capitalized
name and sub-breed count. `SubBreedCard` renders each sub-breed (cards, not
navigable — sub-breeds have no further data in this API). `BreedDetailTestTags`
again holds test IDs.

**Why Coil for images:** Built Compose-first with `AsyncImage`, handles
caching/placeholdering for free. Glide/Picasso predate Compose and need
adapters. (Honest note: Coil currently runs with its own default
`ImageLoader` — it is *not* wired to the app's OkHttpClient singleton, so the
10-second timeouts and debug logging don't apply to image requests. Wiring it
via `SingletonImageLoader.Factory` on the Application is a known, deliberate
to-do sketched in IMAGES.md.)

---

## App entry points

### `MiniBreedsApp` (Application)

**What it does:** Runs once at process start, before any screen: starts Koin
(with the Android context and the three modules) so that by the time any
ViewModel asks for a dependency, the graph is ready.

### `MainActivity`

**What it does:** The app's only Activity. Enables edge-to-edge drawing and
sets the Compose content: theme wrapper around the NavHost. Two real lines of
logic.

**Why a single Activity:** In Compose, screens are composables and
"navigation" swaps composables inside one Activity. Multiple Activities would
mean multiple back-stack systems and Bundle-based data passing — all cost, no
benefit.

### `ui/theme` — `Theme.kt`, `Color.kt`, `Type.kt`

**What it does:** `MiniBreedsTheme` picks the light or dark warm-brand color
scheme (following the system setting) and applies Material 3 typography.
Material You "dynamic color" (colors derived from the user's wallpaper,
Android 12+) is supported but **off by default** — a deliberate choice so the
app shows its own brand palette instead of looking different on every device.

---

## Where to go next

- **README.md** — the architecture rationale in full (this file is the
  beginner version of it).
- **NETWORKLAYER.md / ERRORHANDLING.md / IMAGES.md** — deep dives on those
  slices.
- **TESTING.md** — what every test class proves and how to run the suites.
