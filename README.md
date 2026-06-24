# Mini-Breeds

A small master–detail Android app, 100% Jetpack Compose. It lists dog breeds from
[dog.ceo](https://dog.ceo/dog-api/) (`GET https://dog.ceo/api/breeds/list/all`), opens
a detail screen for the selected breed, and handles loading and error states.

**Bonus features:** real-time local search, and per-row favorites persisted with DataStore.

## Tech stack

| Concern | Choice |
|---|---|
| UI | Jetpack Compose + Material 3, single activity |
| Architecture | MVVM with a `StateFlow` of UI state |
| Navigation | Navigation Compose, typed (`@Serializable`) routes |
| Networking | Retrofit + OkHttp + kotlinx.serialization |
| Persistence | Preferences DataStore (favorites) |
| DI | Manual — the repository is created in `Application` |
| Tests | JUnit + coroutines-test with hand-written fakes |

## How it's organized

One `:app` module, two small packages:

```
data/                      Breed, DogResponse (DTO), DogApi (Retrofit), BreedRepository
ui/
  Navigation.kt            typed routes + NavHost
  breedlist/               BreedListViewModel + BreedListScreen
  breeddetail/             BreedDetailScreen
  common/                  Loading/Error composables, BreedAvatar
  theme/                   Material 3 theme
MiniBreedsApp.kt           builds the repository (manual DI)
MainActivity.kt            hosts the Compose nav graph
```

Data flows one way: the screen collects a `StateFlow<UiState>` from the ViewModel; the
ViewModel calls the repository; the repository talks to Retrofit and DataStore. The
`BreedRepository` interface is the seam that lets the ViewModel be tested with a fake.

## Key decisions

- **`UiState` is a sealed interface** (`Loading` / `Error` / `Content`). The ViewModel
  wraps the network call in a `try/catch` and emits the matching state — an `IOException`
  becomes an offline error, anything else a generic one. No custom result/error types.
- **Manual DI.** There's exactly one dependency to wire (the repository), so the
  `Application` builds it and a small `ViewModelProvider.Factory` hands it to the
  ViewModel. No DI framework to explain.
- **Search and favorites are merged in the ViewModel.** The ViewModel keeps the loaded
  breeds, the search query, and the current favorites as plain fields and rebuilds the
  rendered list with a single `recompute()` whenever any of them changes — so filtering
  and favorite state live in one place and are easy to unit-test. No `combine`/`stateIn`
  to reason about; just a `MutableStateFlow` updated on the main thread.
- **The detail screen only needs the breed name**, which is passed as a navigation
  argument — so it's a stateless composable with no ViewModel.
- **Stateless screens + thin stateful wrappers.** Each screen is a function of
  `(state, callbacks)` and is previewable without a ViewModel or the network.

## Running it

```
.\gradlew.bat :app:assembleDebug        # build
.\gradlew.bat :app:testDebugUnitTest    # unit tests
.\gradlew.bat :app:installDebug         # install on a device/emulator
```

Requires JDK 17+, Android SDK platform 36, minSdk 26.

## Tests

Unit tests in `app/src/test` (`.\gradlew.bat :app:testDebugUnitTest`):

- `BreedMappingTest` — the API payload maps to a sorted list of breeds.
- `BreedListViewModelTest` — loads into content, search filters, favorite toggles,
  and network failures surface as offline vs. generic errors. Uses a hand-written
  `FakeBreedRepository`.
