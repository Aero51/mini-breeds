# List Performance

Why the breed list scrolls smoothly on the release build but feels laggy in
debug, what causes the gap, and the available options for improving debug
performance.

---

## Root cause — debug Compose runs without AOT

Debug Compose builds run on the **JVM interpreter** with no ahead-of-time
compilation, no R8/ProGuard optimization, and no baseline profile warmup.
The Compose runtime itself (layout pass, recomposition, draw) is several times
slower in this mode. This is structural — it is not caused by app code.

Measured on a Redmi Note 14 Pro (60 Hz) with `dumpsys gfxinfo`:

| Build | Median frame time | Jank rate |
|---|---|---|
| Debug | ~77 ms | ~36.5 % |
| Release | ~7 ms | ~0.3 % |

The 70 ms difference is interpreter overhead, not a bug. The release number
(7 ms at 60 Hz) is genuinely smooth.

---

## What is already correct in this app

All standard Compose performance practices are already in place. None of the
common fixes apply here because the code is already optimal:

| Practice | Where |
|---|---|
| `key = { it.name }` in `LazyColumn` | `BreedListScreen.kt:178` |
| `animateItem()` on each card | `BreedListScreen.kt:183` |
| `remember(name, darkTheme)` around `Color.hsl()` | `BreedAvatar.kt` |
| `collectAsStateWithLifecycle()` (not `collectAsState()`) | Both ViewModels |
| No work on the UI thread | `withContext(dispatchers.io)` in `BreedRepositoryImpl` |
| Logging interceptor disabled in release | `if (BuildConfig.DEBUG)` guard in `AppModules.kt` |
| `StateFlow` with `WhileSubscribed(5000)` | Both ViewModels |

---

## Options to improve debug performance

### Option 1 — `relDebug` build type (recommended)

Add a third build type that enables R8 but keeps the build debuggable and
debug-signed. Near-release scroll performance with breakpoints still working.

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

### Option 2 — Baseline profiles

Baseline profiles pre-compile the hot Compose code paths so the JVM starts
with AOT-compiled bytecode even on the first run. This requires the
`ProfileInstaller` library and a `BaselineProfileGenerator` Gradle module —
more setup effort but a significant improvement for both debug and
cold-start release performance.

### Option 3 — Test performance on the release build (current approach)

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
| Move work off the UI thread | Already done; repository uses `Dispatchers.IO` |
| Reduce debug logging | `HttpLoggingInterceptor` is already guarded by `BuildConfig.DEBUG`; no logging in list composables |
| Disable Layout Inspector | Can help marginally but does not close the 70 ms gap |

---

## Summary

| Scenario | Recommendation |
|---|---|
| Checking functionality, fixing bugs | Use the debug build as normal |
| Judging scroll / animation smoothness | Use `installRelease` |
| Want fast scrolling while debugging | Add the `relDebug` build type |
| Systematic cold-start improvement | Add baseline profiles |
