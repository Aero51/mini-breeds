# List Performance

Why the breed list scrolls smoothly on the release build but feels laggy in
debug, what causes the gap, and the available options for improving debug
performance.

---

## Root cause — debuggable builds run without release optimizations

Debug builds are **debuggable**, and ART (the Android Runtime) deliberately
withholds its strongest optimizations from debuggable apps so that a debugger
can attach, set breakpoints, and deoptimize methods at any moment: no
install-time AOT compilation, restricted JIT and profile-guided compilation,
and extra debugging metadata kept live. On top of that, debug APKs skip R8
entirely — no code shrinking, no inlining, no optimization passes. The Compose
runtime (recomposition, layout, draw) amplifies both effects because its hot
paths run for every frame. This is structural — it is not caused by app code.

Measured with `dumpsys gfxinfo` on a physical device (Redmi Note 14 Pro,
60 Hz):

| Build | Median frame time | Jank rate |
|---|---|---|
| Debug | ~77 ms | ~36.5 % |
| Release | ~7 ms | ~0.3 % |

The ~70 ms difference is the cost of debuggability plus missing R8, not a
bug. The release number (7 ms at 60 Hz) is genuinely smooth.

> **Methodology note:** debug-build measurements are included for diagnostic
> purposes only. User-visible performance must be evaluated on release or
> release-equivalent builds — that is what ships.

---

## What is already correct in this app

All standard Compose performance practices are already in place. None of the
common fixes apply here because the code is already optimal:

| Practice | Where |
|---|---|
| `key = { it.name }` in `LazyColumn` | `BreedListScreen.kt` (`BreedList`) |
| `animateItem()` on each card | `BreedListScreen.kt` (`BreedList`) |
| `remember(name, darkTheme)` around `Color.hsl()` | `BreedAvatar.kt` |
| `collectAsStateWithLifecycle()` (not `collectAsState()`) | Both ViewModels |
| No I/O or network work on the UI thread | `withContext(dispatchers.io)` in `BreedRepositoryImpl` |
| Logging interceptor disabled in release | `if (BuildConfig.DEBUG)` guard in `AppModules.kt` |
| `StateFlow` with `WhileSubscribed(5000)` | Both ViewModels |

(Compose itself still performs recomposition, measure, layout, and draw on
the UI thread — that is by design and unavoidable; the point above is that
the app adds no *blocking* work of its own there.)

---

## Options to improve debug performance

### Option 1 — `relDebug` build type

Add a third build type that enables R8 but keeps the build debuggable and
debug-signed:

```kotlin
// app/build.gradle.kts
buildTypes {
    create("relDebug") {
        initWith(getByName("release"))          // inherits R8 + optimizations
        isDebuggable = true                     // breakpoints still attach
        signingConfig = signingConfigs.getByName("debug")
        matchingFallbacks += "release"
    }
}
```

Install and run:

```
.\gradlew.bat :app:installRelDebug
```

**Honest expectations:** this recovers the *R8 share* of the gap only. The
build stays debuggable, so ART still withholds its release-grade
optimizations — expect performance *between* debug and release, not
release-equivalent. Two further caveats:

- Stepping through R8-processed code is unpleasant by default (inlined
  methods, renamed symbols). For comfortable debugging the build type needs
  its own rules — at minimum `-dontobfuscate` and
  `-keepattributes SourceFile,LineNumberTable`.
- `BuildConfig.DEBUG` follows the debuggable flag, so the HTTP logging
  interceptor switches back **on** in `relDebug` — it is a third behavior
  profile, not "release with breakpoints".

### Option 2 — Baseline profiles (release cold-start, *not* a debug fix)

Baseline profiles list the hot code paths (startup, first scroll, critical
journeys) so ART AOT-compiles them **at install time** instead of waiting for
background profile-guided compilation to discover them. They primarily
improve **release cold start and first-run jank**.

They are **not** a solution for debug scrolling: ART ignores baseline
profiles for debuggable builds entirely. Also note that the Compose libraries
ship their own baseline profile, which the build system merges automatically —
the smooth release measurement above already benefits from it. An app-specific
profile (the `androidx.baselineprofile` Gradle plugin + a Macrobenchmark
generator module) would add this app's own paths on top, which matters mostly
for first-launch experience after install/update.

### Option 3 — Test performance on the release build (current approach, recommended)

The pragmatic default. Debug is used for functionality and correctness;
the release build (`installRelease`) is used for judging scroll smoothness
and animation quality. The release build type is already R8-optimized and
debug-signed so local installs work without a keystore.

```
.\gradlew.bat :app:installRelease
```

See `README.md → Installing the release build on a device` for full
instructions including multi-device targeting and MIUI restrictions.

---

## Diagnosing a real Compose performance problem

If the **release** build ever janks, the debug-vs-release explanation above
no longer applies and the proper Compose tooling is:

| Tool | What it shows |
|---|---|
| Layout Inspector → recomposition counts | Which composables recompose (and skip) per interaction — the first place to look for over-recomposition |
| Compose compiler reports (`composeCompiler { reportsDestination = ... }`) | Which classes the compiler considers unstable, i.e. which parameters defeat skipping |
| Macrobenchmark (`androidx.benchmark.macro`) with `FrameTimingMetric` | Reproducible frame-time measurements of real scrolls on release builds — the authoritative number, replacing manual `dumpsys gfxinfo` |

None of these were needed here: the release build measures smooth, and the
state in this app is already immutable data classes and pre-shaped row models
(stable by construction).

---

## Generic advice that does NOT apply here

The following tips are commonly given for list performance but target
View-based (RecyclerView) apps or unoptimized Compose code. They are listed
here to avoid confusion:

| Advice | Why it doesn't apply |
|---|---|
| Add `setHasFixedSize(true)` | RecyclerView API; not relevant to `LazyColumn` |
| Use `ListAdapter` + `DiffUtil` | RecyclerView API; `LazyColumn` with `key` is the Compose equivalent and is already used |
| Avoid `notifyDataSetChanged()` | RecyclerView API |
| Add `remember` / avoid unstable state | Already done throughout |
| Move I/O off the UI thread | Already done; repository uses `Dispatchers.IO` |
| Reduce debug logging | `HttpLoggingInterceptor` is already guarded by `BuildConfig.DEBUG`; no logging in list composables |
| Disable Layout Inspector | Can help marginally but does not close the ~70 ms gap |

---

## Summary

| Scenario | Recommendation |
|---|---|
| Checking functionality, fixing bugs | Use the debug build as normal |
| Judging scroll / animation smoothness | Use `installRelease` |
| Faster (not release-grade) scrolling while debugging | Add the `relDebug` build type with no-obfuscation keep rules |
| Release cold-start / first-run improvement | Add an app baseline profile (release only; ignored in debug) |
| Release build actually janks | Layout Inspector recomposition counts → compiler stability report → Macrobenchmark |
