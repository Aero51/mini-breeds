# CLAUDE.md

Mini-Breeds: a junior Android assessment app — 100% Jetpack Compose master–detail
list of dog breeds from `https://dog.ceo/api/breeds/list/all`, with real-time search
and DataStore-persisted favorites. See `README.md` for the architecture and the
rationale behind every design decision (no use-case layer, Koin over Hilt, etc.).

## Commands (Windows)

```
.\gradlew.bat :app:assembleDebug               # build
.\gradlew.bat :app:testDebugUnitTest           # JVM unit tests
.\gradlew.bat :app:connectedDebugAndroidTest   # Compose UI tests (emulator required)
.\gradlew.bat :app:lintDebug :app:assembleRelease
```

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
  installs). Debug Compose runs without AOT/baseline profiles and is several
  times slower — not a code problem.
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
