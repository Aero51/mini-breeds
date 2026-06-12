# Alternatives — what else was considered, class by class

For every class in the app: the approach that was chosen, the realistic
alternative implementations, and why the chosen one won. Alternatives are
listed with their honest advantages — an alternative with no upside wasn't a
real decision. For a plain-English explanation of *what* each class does,
read `WALKTHROUGH.md`; this file is about *what else it could have been*.

Format per class:

> **Chosen:** the implemented approach.
> **Alternatives:** each with its genuine pro and the reason it lost.
> **Deciding factor:** the one consideration that settled it.

---

## core

### `AppError`

**Chosen:** a sealed interface enumerating six failure cases, some carrying
data (`Http(code)`, `ApiStatus(status)`, `Unknown(cause)`).

**Alternatives:**
- *Raw exceptions* — zero extra code, idiomatic Java heritage. Lost because
  nothing forces callers to handle them; an uncaught path is a production
  crash, and the compiler can't list "all the ways this can fail".
- *An enum* — even simpler than sealed. Lost because enum cases can't carry
  payloads; you'd need side channels for the HTTP code and the cause.
- *Error codes / strings* — trivially serializable. Lost because they can't
  be matched exhaustively and invite typo bugs (`"timout"` compiles fine).
- *One generic `Error(message: String)` case* — minimal. Lost because the UI
  then can't react differently per case (offline vs server error need
  different copy), and English text would originate in the data layer,
  killing localization.

**Deciding factor:** exhaustive `when` — adding a seventh error case breaks
the build everywhere it must be handled, instead of being silently ignored.

### `AppResult<T>` (+ `map` / `onSuccess` / `onFailure`)

**Chosen:** a two-case sealed result (`Success(value)` /
`Failure(error: AppError)`) with three small chaining extensions.

**Alternatives:**
- *`kotlin.Result<T>`* — in the standard library, zero code to write. Lost
  because its failure side is `Throwable`: exactly the untyped catch-all the
  app is trying to eliminate. Wrapping `AppError` in an exception class to
  fit it would be ceremony to defeat the type system.
- *Arrow's `Either<AppError, T>`* — richer combinators, well-tested. Lost
  because pulling in a functional-programming library for one type used in
  two call sites is dependency weight with no payoff; the three extensions
  cover every operation the app actually performs.
- *Exceptions + `runCatching`* — least code. Lost for the same reason as raw
  exceptions in `AppError`, plus `runCatching` catches `CancellationException`
  and silently breaks coroutine cancellation — a classic production bug.
- *Returning `null` on failure* — simplest possible. Lost because it erases
  *why* it failed, and the UI needs the why.

**Deciding factor:** the failure channel must be `AppError`, not `Throwable`;
no off-the-shelf type offers that without adapters costing more than the ~25
lines this class is.

(History note: the class originally also had `success()`/`failure()` factory
functions; they were deleted once it was clear constructors read just as well
— speculative API is a cost, not an investment.)

### `DispatcherProvider` / `DefaultDispatcherProvider`

**Chosen:** a three-property interface over `Dispatchers`, injected via Koin.

**Alternatives:**
- *Hardcode `Dispatchers.IO` at call sites* — no indirection at all. Lost
  because JVM unit tests then run on real thread pools: timing-dependent,
  slow, and incompatible with `runTest`'s virtual clock.
- *Inject a `CoroutineDispatcher` parameter per class* — lighter than an
  interface, common in Google samples. Genuinely viable; lost narrowly
  because one shared provider scales to multiple dispatcher kinds without
  growing every constructor signature.
- *`Dispatchers.setMain`-style global overrides for IO too* — no API exists
  for IO; only Main is swappable that way.

**Deciding factor:** deterministic JVM tests. Honest caveat: only `io` has
production call sites today; `default`/`main` are convention, and trimming
them would be defensible too.

---

## domain

### `Breed`

**Chosen:** an immutable `data class Breed(name, subBreeds)` — no favorite
flag, no DTO fields.

**Alternatives:**
- *Reuse the DTO as the model* — one class fewer. Lost because every screen
  would then depend on dog.ceo's wire format; an API rename would ripple
  through the UI.
- *`isFavorite: Boolean` on the model* — convenient for rendering. Lost
  because it bakes mutable persistence state into a value object: either the
  repository rewrites cached breeds on every toggle, or the flag goes stale.
  Favorites live in their own `Flow<Set<String>>` and are merged at the edge
  (ViewModel), which is also what makes a toggle propagate to both screens
  reactively.
- *A richer domain entity with behavior (DDD-style)* — appropriate when there
  are business rules. Lost because a breed has none here; a record suffices.

**Deciding factor:** domain objects stay immutable facts; view-specific and
mutable concerns belong to the layers that own them.

### `BreedRepository` (interface)

**Chosen:** one interface covering breeds *and* favorites, with an observable
in-memory cache (`cachedBreeds: StateFlow`), `refreshBreeds()`,
`observeBreed(name)`, `fetchBreedImageUrl(name)`, `favorites`,
`toggleFavorite(name)`.

**Alternatives:**
- *No interface, concrete class only* — one file fewer, YAGNI-compliant on
  its face. Lost because the interface has two concrete consumers today:
  `FakeBreedRepository` in ViewModel unit tests, and the Koin override in
  `NavigationTest`. Abstraction with a paying customer isn't speculation.
- *Two repositories (breeds / favorites)* — finer-grained, single
  responsibility per interface. Lost because both ViewModels need both
  concerns *together*; splitting doubles the injection surface and the fake
  count for zero isolation benefit at this size.
- *`suspend fun getBreeds(): List<Breed>` with no cache* — the smallest
  possible contract. Lost because the detail screen then has no data source:
  you'd refetch per navigation or pass whole objects through navigation
  arguments (size-limited, goes stale).
- *Expose only `Flow`s, no suspend functions (full "offline-first" shape)* —
  elegant. Lost because retry-with-result becomes awkward; the UI wants to
  know *whether the refresh it triggered* failed, which a return value states
  directly.

**Deciding factor:** the detail screen's needs (read from cache, react to
favorite changes, survive process death) define this exact surface.

---

## data/remote

### `DogResponseDto<T>`

**Chosen:** one generic `@Serializable` envelope (`message: T`,
`status: String = ""`), `T` = breed map or image-URL string.

**Alternatives:**
- *One DTO per endpoint* — more explicit, no generics. This *was* the
  original implementation; replaced because the two classes and their two
  unwrap functions were word-for-word duplicates, and a third endpoint would
  have made a third copy.
- *Parse with `JsonElement` and pick fields dynamically* — no DTO classes at
  all. Lost because it trades compile-time field checking for runtime
  casting, the exact opposite of what kotlinx.serialization buys.
- *Defaults on every field (`message: T` can't default, but e.g.
  `Map<…> = emptyMap()` on a non-generic DTO)* — never crashes on missing
  fields. Deliberately rejected: a 2xx body with no payload should surface as
  `AppError.Serialization`, not masquerade as "zero dog breeds exist".

**Deciding factor:** dog.ceo's envelope is one shape; the code should say so
once.

### `DogApiService`

**Chosen:** Retrofit interface, two `suspend` endpoints.

**Alternatives:**
- *Ktor Client* — Kotlin-first, multiplatform, also fine. Lost because
  multiplatform buys nothing in an Android-only app and Retrofit is the
  vocabulary every Android reviewer reads fluently.
- *Raw OkHttp* — fewest dependencies. Lost because URL building, body
  parsing, and coroutine adaptation would be hand-rolled — all code Retrofit
  generates, all code that can be wrong.
- *HttpURLConnection* — zero dependencies. Lost for the same reasons,
  doubled.

**Deciding factor:** least code for the reviewer to question; industry
default.

### `safeApiCall` + `Throwable.toAppError()`

**Chosen:** one suspend wrapper that try/catches the whole call, rethrows
`CancellationException`, and maps everything else to `AppError`.

**Alternatives:**
- *try/catch in each repository function* — no abstraction. Lost because the
  mapping table would be copy-pasted and would drift.
- *A Retrofit `CallAdapter` returning `AppResult<T>` automatically* — most
  elegant; the interface itself would return `AppResult`. Genuinely
  attractive; lost because a custom CallAdapter is ~3× the code of
  `safeApiCall`, harder to read for a reviewer, and saves only one explicit
  function call per endpoint. At two endpoints, the arithmetic says wrapper.
- *An OkHttp interceptor translating errors* — wrong layer; interceptors
  can't return typed results to Kotlin callers, only manipulate HTTP.
- *Catching `Exception` instead of `Throwable` with explicit rethrow* — the
  common tutorial shape. Lost because it silently misses `Error` subclasses,
  and more importantly most tutorial versions forget `CancellationException`,
  breaking structured concurrency. The explicit rethrow is the whole point.

**Deciding factor:** one auditable choke point; the cheapest implementation
that has one.

---

## data/local

### `FavoritesDataSource` (interface) / `DataStoreFavoritesDataSource`

**Chosen:** Preferences DataStore holding a `Set<String>` under one key;
`IOException` on read degrades to empty, anything else propagates.

**Alternatives:**
- *SharedPreferences* — simpler API, universally known. Lost because its
  synchronous reads can jank the main thread, `apply()` failures are
  silently swallowed, and change-observation requires listeners glued on
  manually — DataStore gives a `Flow` natively.
- *Room* — real queries, migrations, relations. Lost because favorites are a
  single set of strings; a schema + DAO + migration story for that is the
  truck-for-a-letter case.
- *Proto DataStore* — type-safe schema vs preferences' key-value. Lost
  because defining a `.proto` for one `Set<String>` adds a codegen step for
  no type-safety gain over `stringSetPreferencesKey`.
- *A JSON file* — no library at all. Lost because atomic writes,
  corruption handling, and change notification would all be hand-built —
  DataStore *is* that code, maintained by Google.
- *Swallowing all read exceptions* — never crashes. Deliberately rejected:
  only `IOException` is plausibly "disk hiccup"; anything else is a bug that
  must surface in development, not be eaten.

**Deciding factor:** observable, transactional, coroutine-native — and the
assessment explicitly rewards DataStore.

---

## data/repository

### `BreedRepositoryImpl`

**Chosen:** in-memory `MutableStateFlow` cache; `refreshBreeds()` as
`safeApiCall → unwrap() → map → onSuccess(cache) → onFailure(log)`; images
fetched but never cached; one generic `unwrap()` doing the `status`-field
check.

**Alternatives:**
- *Room as offline cache* — survives process death, real offline mode. Lost
  because the requirement is surviving *navigation*, not reboots; the cost is
  a schema/DAO/migrations, and the cold-start case is handled in 4 lines by
  the detail ViewModel instead. The README states this trade-off; it's the
  most challengeable decision in the app and is owned explicitly.
- *OkHttp HTTP cache* — free, standards-based. Lost because it caches bytes,
  not parsed domain objects; every read would re-parse JSON, and
  cache-control is at the server's mercy.
- *No cache, refetch per screen* — simplest. Lost: visible loading flash on
  every back-navigation and pointless network traffic for static data.
- *Caching the image URLs too* — consistent screens. Deliberately rejected:
  the endpoint returns a *random* photo by design, a fresh one per visit is
  a feature, and Coil already caches the actual image bytes, so nothing is
  re-downloaded.
- *Checking `status` inside `safeApiCall`* — one fewer step in the chain.
  Lost because `safeApiCall` would then need to know the DTO shape, coupling
  the generic exception wrapper to dog.ceo's envelope.
- *Sorting in the ViewModel instead of the repository* — arguably
  presentation logic. Lost because JSON map order is undefined; sorting once
  at the source gives every current and future consumer a stable order.

**Deciding factor:** match the persistence machinery to the actual
requirement (session cache), and own the trade-off in writing.

---

## di

### `AppModules.kt` (`networkModule`, `dataModule`, `viewModelModule`)

**Chosen:** Koin, three modules in one file, everything `single` except
ViewModels.

**Alternatives:**
- *Hilt* — compile-time graph validation, Google-endorsed. The strongest
  competitor. Lost because it costs an annotation processor (build time),
  generated-code stack traces, and `@HiltAndroidApp`/`@AndroidEntryPoint`
  ceremony; on a ~10-binding graph its compile-time safety is replaced by a
  5-line `KoinModulesTest` running `verify()`. On a large multi-module,
  multi-team app this decision flips — and the README says so.
- *Dagger 2 directly* — maximum control. Lost: all of Hilt's costs with more
  boilerplate.
- *Manual constructor injection (a hand-rolled `AppContainer`)* — zero
  libraries, fully transparent. Genuinely fine at this size; lost because
  ViewModel + `SavedStateHandle` wiring is exactly the fiddly part Koin's
  `viewModelOf` does in one line, and the assessment brief mentioned Koin.
- *Service locator / `object` singletons* — least typing. Lost because tests
  can't substitute pieces, which forfeits the entire fake-based test
  strategy.
- *One module instead of three* — fewer names. Lost narrowly: the split
  documents the graph's layers and lets instrumented tests override one
  module surgically.

**Deciding factor:** smallest DI that supports test-time substitution, with
the graph-validity risk covered by a test instead of a compiler.

---

## ui/common

### `UiErrorMessage` + `AppError.toUiMessage()`

**Chosen:** a pure mapping from `AppError` to string-resource ID + format
args, living in `ui`.

**Alternatives:**
- *Message strings inside `AppError`* — one place. Lost: `core` would depend
  on Android resources, breaking both the no-deps rule and JVM testability.
- *`when` blocks inline in each screen* — no extra file. Lost because two
  screens already share it; a third copy of the table would drift.
- *Exception `message` passthrough* — free text. Lost: not localizable, often
  developer-speak ("Unable to resolve host…") that should never reach users.

**Deciding factor:** wording is a presentation concern; resources make it
translatable; one table keeps both screens identical.

### `LoadingContent` / `ErrorContent`

**Chosen:** shared full-screen composables with stable `testTag`s.

**Alternatives:**
- *Per-screen loading/error UI* — screens could diverge intentionally. Lost
  because here they *shouldn't* diverge, and shared tags mean shared test
  helpers.
- *Snackbar for errors instead of full-screen* — less intrusive. Lost
  because with no data yet there is nothing behind the snackbar to show; a
  full-screen state with Retry is the honest representation of "we have
  nothing".
- *A generic `StateLayout(state) { … }` wrapper rendering all three states*
  — DRYer still. Lost because the two screens place content differently
  (list keeps the search field visible during error; detail keeps the top
  bar); the `when` in each screen is the right altitude.

**Deciding factor:** identical states should be identical code, but the
*composition* of states per screen stays local.

### `BreedAvatar`

**Chosen:** monogram circle, hue from `name.hashCode()`, dark-mode-aware,
`remember`ed.

**Alternatives:**
- *Fetch a photo per list row* — prettier. Lost: ~100 image requests on
  first launch against a free API, jank, and the list endpoint provides no
  image URLs anyway (each would be one extra call).
- *One static placeholder icon* — simplest. Lost because rows become visually
  indistinguishable; the deterministic color gives identity for free.
- *A color palette lookup instead of HSL math* — curated colors. Lost
  narrowly; hash-to-hue scales to any name without maintaining a table, and
  saturation/lightness clamps keep contrast acceptable in both themes.

**Deciding factor:** per-row identity at zero network cost.

### `FavoriteIcon`

**Chosen:** one composable owning icon choice, tint + scale animation, and
the accessibility description, reused by both screens.

**Alternatives:**
- *Inline `Icon(...)` in each screen* — fewer files. Lost: the animation spec
  and content descriptions would be duplicated and drift.
- *No animation* — less code. Lost cheaply: two `animate*AsState` calls are
  nearly free and favoriting is the app's one moment of delight.
- *Lottie* — richer animation. Lost: a JSON animation runtime for a heart
  bounce.

**Deciding factor:** the heart appears in two places and must behave (and
read to screen readers) identically.

---

## ui/navigation

### `Routes.kt` (`BreedListRoute`, `BreedDetailRoute`)

**Chosen:** typed `@Serializable` routes; the detail ViewModel reads the
argument from `SavedStateHandle` **by key** (`ARG_BREED_NAME`), not
`toRoute()`.

**Alternatives:**
- *String routes (`"details/{breedName}"`)* — the long-standing idiom, more
  tutorial coverage. Lost because route typos and argument-encoding mistakes
  move to runtime; typed routes are also the current official
  recommendation.
- *`savedStateHandle.toRoute<BreedDetailRoute>()`* — the "proper" typed read.
  Lost to a hard constraint: it requires a real Android `Bundle` and silently
  misbehaves in JVM unit tests. The by-key read is the documented, deliberate
  workaround — the one place purity lost to testability.
- *Passing the whole `Breed` object* — no re-lookup. Lost: navigation args
  are size-limited serialized state and a copy goes stale when favorites
  change; pass the ID, observe the source of truth.
- *Shared ViewModel between screens* — no argument at all. Lost: couples the
  screens' lifecycles and dies awkwardly on deep links / process death.

**Deciding factor:** compile-time safety where it's free; documented
pragmatism where the API fights the tests.

### `MiniBreedsNavHost`

**Chosen:** one NavHost; screens receive lambdas, never the `NavController`.

**Alternatives:**
- *Passing `NavController` into screens* — fewer lambdas. Lost: screens
  become untestable/unpreviewable without navigation infrastructure and gain
  the power to navigate anywhere (hidden coupling).
- *Navigation libraries (Voyager, Decompose, Circuit)* — nicer APIs, real
  benefits in multiplatform or deeply nested navigation. Lost: two
  destinations; the official library is the boring, right-sized choice.
- *Multiple Activities* — the pre-Compose pattern. Lost: two back-stack
  systems, Bundle passing, no shared composition.

**Deciding factor:** screens as pure functions of state + callbacks.

---

## ui/breedlist & ui/breeddetail

### `BreedListUiState` / `BreedDetailUiState` (+ `BreedRowUi`)

**Chosen:** sealed `Loading / Error / Content` per screen; list rows
pre-shaped into `BreedRowUi`.

**Alternatives:**
- *Flat data class (`isLoading`, `error`, `data` fields)* — simpler partial
  updates, favored by some style guides. Lost because it can represent
  contradictions (loading *and* error set); sealed states make illegal
  combinations unrepresentable and the screen one exhaustive `when`.
- *Passing domain `Breed` + separate favorites set to the UI* — fewer types.
  Lost: every row composable would re-derive `isFavorite` on every
  recomposition; pre-shaping does it once per state emission.
- *MVI with reducers and intent channels* — fully uniform state machine.
  Lost: framework ceremony for two screens; `combine` already gives
  unidirectional flow.

**Deciding factor:** make the impossible unrepresentable; keep the screen a
dumb renderer.

### `BreedListViewModel`

**Chosen:** private `loadState` + separate `query` flow, public `uiState =
combine(load, query, favorites)` with `stateIn(WhileSubscribed(5s))`; no
debounce.

**Alternatives:**
- *Query inside the UI state* — one flow total. Lost: typed text would be
  destroyed by Loading/Error transitions; keeping it separate is what makes
  it survive a retry.
- *Filtering in the composable* — less ViewModel code. Lost: untestable
  without an emulator and recomputed per recomposition.
- *Debounced search* — standard for server-backed search. Deliberately
  rejected: the filter is local over ~100 strings; debounce would only add
  perceived lag.
- *`SharingStarted.Eagerly`* — simpler mental model. Lost: keeps the pipeline
  hot with no subscribers; `WhileSubscribed(5_000)` survives rotation and
  stops when truly abandoned.
- *Mutable `allBreeds` var + filtered copy* — the imperative classic. Lost:
  two sources of truth to keep synchronized; `combine` derives instead.

**Deciding factor:** state is derived, never patched.

### `BreedDetailViewModel`

**Chosen:** argument by `SavedStateHandle` key; `combine` of cache + favorites
+ refreshError + imageUrl; cold-cache refresh in `init`; image failure leaves
`imageUrl` null (monogram fallback), never an error state.

**Alternatives:**
- *Refetch unconditionally in `init`* — simpler condition. Lost: a pointless
  network call on every navigation with a warm cache.
- *Showing an error state when the photo fails* — "honest" errors. Lost: the
  breed content is fine; replacing working data with an error screen because
  a *decoration* failed punishes the user. Degrade, don't fail.
- *`""` sentinel for failed photo vs null* — distinguishes loading from
  failed. This *was* the original implementation; removed because the UI
  rendered both identically, so the distinction was dead weight.
- *Loading the photo in the composable (Coil straight from a URL built in
  UI)* — fewer flows. Lost: the URL comes from an API call that can fail;
  that belongs behind the repository with the rest of the error mapping.

**Deciding factor:** core content and decoration have different failure
budgets.

### `BreedListScreen.kt` / `BreedDetailScreen.kt`

**Chosen:** stateful `*Route` wrapper (Koin + collect) + stateless screen
(values in, lambdas out); private sub-composables; `testTag` objects.

**Alternatives:**
- *One stateful screen* — fewer functions. Lost: no `@Preview`, and UI tests
  would need Koin + fakes for every state instead of just constructing a
  `UiState`.
- *Slot-based generic screen scaffold shared by both screens* — DRYer.
  Lost: the screens differ exactly where it matters (search field placement,
  top-bar actions); a shared scaffold would grow flags.
- *Finding test nodes by display text* — no tags needed. Lost: breaks on
  copy changes and localization; tags are stable contracts.

**Deciding factor:** the stateless screen is the unit the UI tests and
previews consume; everything else follows.

---

## App entry & theme

### `MiniBreedsApp`

**Chosen:** `Application` subclass starting Koin in `onCreate`.

**Alternatives:** *Lazy/on-demand DI init* — startup micro-optimization.
Lost: Koin's `single`s are already lazy; only the (tiny) module registration
happens eagerly, and ContentProvider/App-Startup tricks would be complexity
without a measurable win at this graph size.

### `MainActivity`

**Chosen:** single Activity, `enableEdgeToEdge()`, `setContent { theme { navhost } }`.

**Alternatives:** *Activity per screen* — see navigation section. *Fragments
hosting Compose* — only justified when migrating an existing Fragment app;
this one is greenfield.

### `MiniBreedsTheme` (+ `Color.kt`, `Type.kt`)

**Chosen:** custom warm light/dark Material 3 scheme; dynamic color
*supported but off by default*.

**Alternatives:**
- *Dynamic color on by default* — free Material You integration. Deliberately
  off: the app would look different on every device and the brand palette is
  part of the submission's polish; the flag documents that it's a choice, not
  an omission.
- *Default Material theme* — zero effort. Lost: visual sameness with every
  template app; the warm palette is cheap differentiation.

**Deciding factor:** a take-home is judged partly on looking *intentional*.

---

## Cross-cutting decisions without a single home

- **No use-case/interactor layer** — the strongest candidate for "missing"
  architecture. Every use case would be a one-line pass-through to the
  repository; a layer of pure forwarding is indirection without abstraction.
  The moment real business logic appears (combining sources, enforcing
  rules), introduce them — the seams already exist.
- **Single Gradle module** — multi-module buys parallel builds and enforced
  boundaries on big teams. Here, package discipline + interfaces give the
  same decoupling with none of the build complexity; the README owns this.
- **Hand-written fakes over MockK/Mockito** — mocks verify *interactions*
  ("was method X called"), fakes verify *behavior* through real state. Fakes
  survive refactors that keep behavior; mock-verification tests break on
  them. Cost: `FakeBreedRepository` is duplicated across `test`/`androidTest`
  source sets (they can't share code without a fixtures module — accepted).
- **MockWebServer over mocking the API interface** — mocking `DogApiService`
  skips Retrofit, OkHttp, and JSON parsing — precisely the layers where
  integration bugs live. MockWebServer exercises real bytes through the real
  stack.
- **Coil over Glide/Picasso** — Compose-first (`AsyncImage`),
  coroutine-based. Glide/Picasso predate Compose and need adapter layers.
  Known gap: Coil currently uses its own default `ImageLoader` rather than
  the app's OkHttpClient singleton (see IMAGES.md for the proposed wiring).
