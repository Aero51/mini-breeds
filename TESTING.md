# Testing Mini-Breeds

How to run and extend the test suites, plus a manual verification checklist.
For *why* the tests are structured this way (fakes over mocks, MockWebServer
against the real HTTP stack, stateless-screen UI tests), see the Tests and
Design decisions sections in `README.md`.

## Prerequisites

- JDK 17+ on `PATH` (or set in `JAVA_HOME`); the Gradle wrapper handles the rest.
- For instrumented tests and manual testing: an Android emulator (API 26+)
  running, or a physical device with USB debugging — verify with `adb devices`.
- No API keys or backend setup needed. Unit tests run fully offline.

## 1. Unit tests (JVM, no device)

```
.\gradlew.bat :app:testDebugUnitTest
```

55 tests across 9 classes in `app\src\test\java\com\profico\minibreeds\`:

| Class | What it proves |
|---|---|
| `core\AppResultTest` | `map`/`onSuccess`/`onFailure` fire on the right variant and pass the other through unchanged |
| `data\remote\BreedsResponseDtoTest` | Spec-shaped JSON parses; unknown keys ignored; malformed JSON throws `SerializationException` |
| `data\remote\SafeApiCallTest` | Every exception type maps to the right `AppError`; `CancellationException` is rethrown |
| `data\repository\BreedRepositoryImplTest` | Real Retrofit/OkHttp/Json against MockWebServer: success mapping/sorting/caching, empty-but-successful body, HTTP 500, garbage body, `"status":"error"`, connection refused, timeout |
| `data\local\DataStoreFavoritesDataSourceTest` | Real JVM DataStore: toggle on/off, persistence across instances, empty default; a corrupt (`IOException`) store degrades to empty while non-IO failures propagate |
| `ui\breedlist\BreedListViewModelTest` | Turbine: Loading→Content, Error→retry→Content, case-insensitive + trimmed filtering, empty-result flag, genuinely-empty list isn't flagged as no-results, favorites reactivity |
| `ui\breeddetail\BreedDetailViewModelTest` | Warm-cache content; cold cache triggers refresh; refresh failure → Error; favorite toggle; missing nav argument fails fast |
| `ui\common\UiErrorTest` | Each `AppError` maps to its own distinct string resource; `Http` carries the status code |
| `di\KoinModulesTest` | The Koin graph resolves (Koin `verify()`) |

HTML report: `app\build\reports\tests\testDebugUnitTest\index.html`

**Caveat on this dev machine:** with Avast's File Shield active, the two
DataStore tests that write to disk twice in a row fail with
`IOException: Unable to rename …preferences_pb.tmp` — the shield scans the
freshly written file and holds it open, so DataStore's atomic rename fails.
This is environmental, not an app or test bug (the same tests are green on
machines without Avast). **Note (verified 2026-06-12): Avast's "pause
protection" does NOT stop File Shield** — the failures persist while paused.
Disable File Shield itself (Protection → Core Shields → File Shield) or add
an Avast exception for `%LOCALAPPDATA%\Temp`, where the tests write.

Run a single class or test:

```
.\gradlew.bat :app:testDebugUnitTest --tests "com.profico.minibreeds.ui.common.UiErrorTest"
.\gradlew.bat :app:testDebugUnitTest --tests "*BreedListViewModelTest.retry*"
```

## 2. Instrumented UI tests (emulator/device required)

```
.\gradlew.bat :app:connectedDebugAndroidTest
```

19 tests across 3 classes in `app\src\androidTest\java\com\profico\minibreeds\`:

- `ui\breedlist\BreedListScreenTest` / `ui\breeddetail\BreedDetailScreenTest` —
  Compose tests on the **stateless** screens: every UI state renders, and every
  callback (row click, favorite, retry, back, search input/clear) fires with the
  right arguments. No Koin, ViewModels, or network involved.
- `ui\navigation\NavigationTest` — the real `MiniBreedsNavHost` with real
  ViewModels; only `BreedRepository` is swapped for `FakeBreedRepository` by
  overriding its Koin definition. Covers list → detail → back and favorite
  propagation between screens. **No network is used**, so these pass even on a
  machine where the emulator has no internet (see §4).

HTML report: `app\build\reports\androidTests\connected\debug\index.html`

Conventions when adding UI tests:

- Use the **v2 Compose test API** (`androidx.compose.ui.test.junit4.v2.*`) — the
  v1 rules are deprecated in this Compose BOM.
- Find nodes by the `testTag`s exposed in `BreedListTestTags`,
  `BreedDetailTestTags`, and `CommonTestTags` — don't match on display text
  except when the text itself is what's being asserted.
- No mocking libraries; extend the hand-written fakes. `FakeBreedRepository`
  exists in both `src\test` and `src\androidTest` (source sets don't share code —
  keep the two copies in sync).

## 3. Lint and release build

```
.\gradlew.bat :app:lintDebug :app:assembleRelease
```

Expected: **0 errors**. A handful of warnings are deliberate and should not be
"fixed": `coreKtx` is pinned to 1.18.0 and compileSdk to 36 because core-ktx
1.19.x requires compileSdk 37 (see `CLAUDE.md`).
Report: `app\build\reports\lint-results-debug.txt`.

## 4. Manual verification checklist

Install and launch:

```
.\gradlew.bat :app:installDebug
adb shell am start -n com.profico.minibreeds/.MainActivity
```

Happy path (needs working internet from the emulator — see caveat below):

1. Launch → loading spinner → alphabetical breed list appears.
2. Type in the search field → list filters in real time, case-insensitive;
   nonsense query shows "No breeds match your search"; the ✕ icon clears it.
3. Tap the heart on a breed → it fills with a spring bounce; restart the app
   (swipe away, relaunch) → the heart is still filled (DataStore persistence).
4. Tap a breed with sub-breeds (e.g. *bulldog*) → detail screen shows its name
   in the top bar and sub-breed cards; a breed without sub-breeds shows
   "This breed has no sub-breeds".
5. Back arrow (and system back) → returns to the list with search text intact.

Error handling:

6. Enable airplane mode (`adb shell cmd connectivity airplane-mode enable`) →
   tap Retry → friendly "No internet connection" message, no crash.
7. Disable airplane mode (`...airplane-mode disable`) → tap Retry → list loads.

**Caveat on this dev machine:** Avast's Web Shield HTTPS scanning breaks TLS
from the emulator (certificate trust-anchor failures), so step 1 shows the
error screen instead. That is the app behaving correctly. To run the happy
path here, pause Avast protection or disable Avast → Protection → Core
Shields → Web Shield → "Enable HTTPS scanning", or use a physical device.
Pausing **does** fix this (unlike the File Shield issue in §1, which pausing
does not fix). Details in `CLAUDE.md`.

## 5. Judging scroll performance

Always evaluate scrolling and animations on the **release** build. Debug builds
of Compose apps are not representative: ART withholds its strongest
optimizations from debuggable apps (no install-time AOT, restricted JIT) and
debug APKs skip R8, which makes lists feel several times slower than what
users get — see `LISTSLOWDEBUG.md` for the full analysis. The release build
type is R8-optimized and debug-signed precisely so it can be installed
locally:

```
.\gradlew.bat :app:installRelease
adb shell am start -n com.profico.minibreeds/.MainActivity
```

(Uninstall the debug build first if signatures clash: `adb uninstall com.profico.minibreeds`.)

## 6. Everything at once

```
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:connectedDebugAndroidTest :app:assembleRelease
```

Last verified on 2026-06-12: the JVM unit suite **55/55 green** (with Avast
File Shield disabled; earlier the same day the two §1 environment-dependent
DataStore failures reproduced while it was active); lint and
`assembleRelease` green; the instrumented suite **19/19 green** on the
Pixel_9a emulator (including the detail-scroll regression test).
