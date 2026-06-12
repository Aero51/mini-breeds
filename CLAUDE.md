# CLAUDE.md

Mini-Breeds: a junior Android assessment app — 100% Jetpack Compose master–detail
list of dog breeds from `https://dog.ceo/api/breeds/list/all`, with real-time search
and DataStore-persisted favorites. See `README.md` for the architecture and the
rationale behind every design decision (no use-case layer, Koin over Hilt, etc.).

## The assignment brief (what this app is graded against)

"The Mini-Breeds App" — junior Android technical assessment. Constraints and
scope, condensed from the brief; do not add features beyond it:

- **100% Jetpack Compose; XML view hierarchies/layout files strictly
  prohibited.** Layout/look is free-form — the graded priorities are
  architecture, code cleanliness, and functionality, explicitly *not*
  pixel-perfect styling.
- **Core:** fetch breeds from `GET https://dog.ceo/api/breeds/list/all`
  (Retrofit or Ktor); show breed names in a scrollable `LazyColumn` with
  every row clickable → detail screen showing the selected breed's name,
  with a clear back mechanism; loading indicator while fetching and a
  user-friendly error fallback on failure.
- **Payload shape:** root keys of `message` are the breed names; values are
  sub-breed lists. `status` is `"success"` on a healthy response.
- **Guardrails:** MVVM (UI fully split from business logic), standard
  Compose Navigation, coroutines + `viewModelScope` (no network on the main
  thread).
- **Bonus criteria (both implemented):** real-time local search via a
  `TextField` above the list; per-row favorites toggle persisted locally.
- **Evaluation matrix:** Compose mastery (idiomatic composables,
  `remember`/state, fluid lists), architecture & routing (clean MVVM
  decoupling, robust navigation), error resilience (no crashes on network
  failures or missing-network flags).

## Commands (Windows)

```
.\gradlew.bat :app:assembleDebug               # build
.\gradlew.bat :app:testDebugUnitTest           # JVM unit tests
.\gradlew.bat :app:connectedDebugAndroidTest   # Compose UI tests (emulator required)
.\gradlew.bat :app:lintDebug :app:assembleRelease
```

## Build prerequisites

- JDK 17+ on `PATH`/`JAVA_HOME`; Gradle itself comes from the wrapper.
- Android SDK with platform 36 installed, located via `local.properties`
  (`sdk.dir=...`) or `ANDROID_HOME`. **`local.properties` is gitignored** —
  fresh clones *and new git worktrees* must provide it or every build fails
  with "SDK location not found".
- An emulator or device (API 26+, the minSdk) is needed only for
  `connectedDebugAndroidTest`, `installDebug`/`installRelease`, and manual
  verification; everything else runs on the JVM.
- The first build downloads dependencies — if that fails with `PKIX path
  building failed` on this machine, see the Avast note at the bottom.

## Architecture in one paragraph

Single `:app` module, strictly layered packages: `core` (AppError/AppResult,
no deps) ← `domain` (Breed, BreedRepository interface) ← `data` (the only package
importing Retrofit/DataStore; `safeApiCall` is the single exception→AppError
mapping point) ← `ui` (MVVM, stateless screens + stateful `*Route` wrappers,
typed navigation routes). DI is Koin (`di\AppModules.kt`), started in
`MiniBreedsApp`.

## Gotchas — do not "fix" these

- **`BreedDetailViewModel` reads its route argument via
  `savedStateHandle[BreedDetailRoute.ARG_BREED_NAME]`, not
  `savedStateHandle.toRoute()`.** `toRoute()` needs a real Android `Bundle` and
  silently fails in JVM unit tests (stubbed Bundle + `returnDefaultValues = true`).
  Do not migrate it to `toRoute()`.
- **`coreKtx` is pinned to 1.18.0.** 1.19.x requires compileSdk 37; this project is
  on 36. The lint `GradleDependency`/`OldTargetApi` warnings about this are
  deliberate.
- **AGP 9 built-in Kotlin**: there is no `kotlin("android")` plugin applied — only
  `kotlin.plugin.compose` and `kotlin.plugin.serialization` as standalone plugins.
  Keep it that way.
- `testOptions.unitTests.isReturnDefaultValues = true` exists so `Log.w` in the
  repository is a no-op in JVM tests instead of throwing.
- **Compose UI tests use the v2 test API** (`androidx.compose.ui.test.junit4.v2.*`);
  the v1 `createComposeRule`/`createAndroidComposeRule` are deprecated in this BOM.
- MockWebServer is OkHttp 5's `mockwebserver3.*` package (builder-style
  `MockResponse`), not the old `okhttp3.mockwebserver` API from most tutorials.
- **No mocking libraries.** Hand-written fakes only; `FakeBreedRepository` is
  intentionally duplicated in `src/test` and `src/androidTest` (source sets don't
  share code).
- `NavigationTest` overrides the Koin `BreedRepository` definition via
  `loadKoinModules` — singles are lazy, so the real one is never constructed in
  tests.

- **Scroll/animation performance complaints must be reproduced on the release
  build** (`installRelease`; it is R8-optimized and debug-signed for local
  installs). ART withholds its optimizations from debuggable builds and debug
  APKs skip R8, making them several times slower — not a code problem
  (see LISTSLOWDEBUG.md).
- `android.r8.gradual.support=true` in `gradle.properties` is required by AGP 9
  for `optimization.enable=true`; don't remove it.
- **Warnings are errors.** Kotlin compiles with `allWarningsAsErrors` (all
  source sets, including tests) and lint runs with `warningsAsErrors = true`.
  Fix new warnings instead of weakening these settings. Four lint checks are
  deliberately disabled because they are time-dependent version advisories,
  not code quality: `GradleDependency`, `NewerVersionAvailable`,
  `AndroidGradlePluginVersion`, `OldTargetApi`.

## Local environment note

On this dev machine, **Avast Antivirus interferes in three distinct ways** when
its shields are active. None of them are app bugs — don't debug app code for them:

1. **Emulator TLS**: Web Shield HTTPS scanning breaks all TLS from the Android
   emulator (cert trust-anchor failures / `ERR_CERT_AUTHORITY_INVALID`), so the
   live dog.ceo call shows the app's "No internet connection" error screen.
   Disable Avast's HTTPS scanning or use a physical device.
2. **Gradle dependency downloads**: the same interception makes new dependency
   downloads fail with `PKIX path building failed`. Fixed machine-locally in
   `%USERPROFILE%\.gradle\gradle.properties` (not in this repo) by pointing the
   Gradle JVM at the Windows certificate store
   (`-Djavax.net.ssl.trustStore=NUL -Djavax.net.ssl.trustStoreType=Windows-ROOT`).
3. **DataStore unit tests**: File Shield scans freshly written files and holds
   them open, so the two `DataStoreFavoritesDataSourceTest` tests that write
   twice in a row fail with `IOException: Unable to rename …preferences_pb.tmp`.
   See TESTING.md §1.

Avast's **"pause protection" fixes #1 but NOT #3** (verified 2026-06-12: the
emulator's live network works while paused, but the two DataStore test
failures persist). For #3, disable File Shield itself or add an exception for
`%LOCALAPPDATA%\Temp`.
