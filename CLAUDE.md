# CLAUDE.md

Mini-Breeds: a junior Android assessment app — 100% Jetpack Compose master–detail
list of dog breeds from `https://dog.ceo/api/breeds/list/all`, with real-time search
and DataStore-persisted favorites. See `README.md` for the architecture and the
decisions behind it.

The app is deliberately kept **simple and easy to explain in an interview** — favour
the obvious, idiomatic solution over extra abstraction layers.

## The assignment brief (what this app is graded against)

"The Mini-Breeds App" — junior Android technical assessment. Scope, condensed:

- **100% Jetpack Compose**; XML view hierarchies/layout files are prohibited.
  Architecture, code cleanliness, and functionality matter — not pixel-perfect styling.
- **Core:** fetch breeds from `GET https://dog.ceo/api/breeds/list/all` (Retrofit);
  show names in a `LazyColumn` with every row clickable → a detail screen showing the
  breed's name, with a back button; a loading indicator while fetching and a
  user-friendly error fallback on failure.
- **Payload:** `message` keys are breed names, values are sub-breed lists;
  `status` is `"success"` on a healthy response.
- **Guardrails:** MVVM, Compose Navigation, coroutines + `viewModelScope`.
- **Bonus (both implemented):** real-time local search; per-row favorites persisted
  locally.

## Commands (Windows)

```
.\gradlew.bat :app:assembleDebug          # build
.\gradlew.bat :app:testDebugUnitTest      # JVM unit tests
.\gradlew.bat :app:installDebug           # install on a device/emulator
```

## Build prerequisites

- JDK 17+ on `PATH`/`JAVA_HOME`; Gradle comes from the wrapper.
- Android SDK with platform 36, located via `local.properties` (`sdk.dir=...`) or
  `ANDROID_HOME`. **`local.properties` is gitignored** — fresh clones and new git
  worktrees must provide it or every build fails with "SDK location not found".
- An emulator/device (API 26+) is only needed for `installDebug` and manual checks;
  unit tests run on the JVM.

## Architecture in one paragraph

Single `:app` module, two packages. `data` holds `Breed`, the `DogResponse` DTO, the
`DogApi` Retrofit service (with a `create()` factory), and `BreedRepository`
(interface + impl; the impl owns Retrofit and the favorites DataStore). `ui` holds the
typed navigation routes, the list ViewModel + screen, the stateless detail screen, and
shared composables. DI is manual: `MiniBreedsApp` builds the repository and a
`ViewModelProvider.Factory` passes it to `BreedListViewModel`.

## Conventions — keep it simple

- **Errors:** the ViewModel uses `try/catch` and emits a sealed `BreedListUiState`
  (`Loading` / `Error(isOffline)` / `Content`). No `Result`/`AppError` wrapper types.
- **DI is manual.** Don't reintroduce Koin/Hilt for one dependency.
- **The detail screen takes the breed name as a navigation argument** and is a stateless
  composable — it has no ViewModel.
- **Screens are stateless** functions of `(state, callbacks)`; a thin wrapper owns the
  `viewModel()` + `collectAsStateWithLifecycle()`.
- `testOptions.unitTests.isReturnDefaultValues = true` keeps `android.util.Log` a no-op
  in JVM tests instead of throwing.
- **No mocking libraries** — hand-written fakes (`FakeBreedRepository`).
- **Pin note:** `coreKtx` is 1.18.0 on purpose (1.19.x needs compileSdk 37; this is 36).
- **OkHttp is pinned via its BOM** so Retrofit doesn't pull a different (uncached) okio;
  see the Avast note below for why a fresh transitive download is a problem here.

## Local environment note (this dev machine)

**Avast Antivirus** interferes in two ways relevant here — neither is an app bug:

1. **Gradle dependency downloads** fail with `PKIX path building failed`. Fixed
   machine-locally in `%USERPROFILE%\.gradle\gradle.properties` by pointing the Gradle
   JVM at the Windows certificate store
   (`-Djavax.net.ssl.trustStore=NUL -Djavax.net.ssl.trustStoreType=Windows-ROOT`).
   Because of this, prefer dependency versions already in the local Gradle cache; a new
   transitive artifact may need a download Avast blocks.
2. **Emulator TLS**: Web Shield HTTPS scanning breaks TLS from the Android emulator, so
   the live dog.ceo call shows the app's "No internet connection" screen. Disable
   Avast's HTTPS scanning or use a physical device.
