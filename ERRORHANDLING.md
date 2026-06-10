# Error Handling

How the Mini-Breeds app converts every possible failure into a typed value and
surfaces it as a user-friendly message — with no raw exceptions crossing a layer
boundary.

---

## Taxonomy — `AppError`

All failures are represented as cases of the `AppError` sealed interface
(`core/AppError.kt`). Nothing above the data layer ever catches a network or
parsing exception directly.

| Case | Cause | Example |
|---|---|---|
| `NoConnection` | No usable network / host unreachable | Airplane mode, DNS failure, `ConnectException`, `UnknownHostException`, generic `IOException` |
| `Timeout` | Server did not respond in time | `SocketTimeoutException`, `InterruptedIOException` |
| `Http(code)` | Non-2xx HTTP response | `HttpException` from Retrofit (404, 500, …) |
| `Serialization` | Response body could not be parsed | `SerializationException` from kotlinx.serialization |
| `ApiStatus(status)` | HTTP 2xx but `"status" != "success"` | Dog API returns `{ "status": "error", … }` |
| `Unknown(cause)` | Anything not covered above | Unexpected runtime exceptions |

---

## Result wrapper — `AppResult<T>`

`AppResult<T>` (`core/AppResult.kt`) is a sealed type with two variants:

```
AppResult<T>
├── Success(value: T)
└── Failure(error: AppError)
```

Every function that can fail returns `AppResult<T>` instead of throwing.
Callers are forced by the type system to handle both paths. Three extension
functions support chaining without nested `when` blocks:

| Function | Purpose |
|---|---|
| `map { }` | Transform the success value; failures pass through unchanged |
| `onSuccess { }` | Run a side-effect on success; returns `this` for chaining |
| `onFailure { }` | Run a side-effect on failure; returns `this` for chaining |

---

## Single catch point — `safeApiCall`

`data/remote/SafeApiCall.kt` is the **only** place in the codebase where
transport-level exceptions are caught:

```kotlin
suspend fun <T> safeApiCall(block: suspend () -> T): AppResult<T> =
    try {
        AppResult.Success(block())
    } catch (e: CancellationException) {
        throw e          // must propagate for structured concurrency
    } catch (t: Throwable) {
        AppResult.Failure(t.toAppError())
    }
```

`CancellationException` is always rethrown so coroutine cancellation
keeps working correctly. Everything else is mapped by `Throwable.toAppError()`:

```
UnknownHostException / ConnectException  →  AppError.NoConnection
SocketTimeoutException                   →  AppError.Timeout
InterruptedIOException                   →  AppError.Timeout
HttpException                            →  AppError.Http(code)
SerializationException                   →  AppError.Serialization
IOException (other)                      →  AppError.NoConnection
else                                     →  AppError.Unknown(cause)
```

---

## API-level status check

The Dog API signals application-layer failures with a `"status"` field even on
HTTP 2xx responses. `BreedRepositoryImpl.toBreeds()` checks this **after**
`safeApiCall` succeeds:

```kotlin
if (value.status != BreedsResponseDto.STATUS_SUCCESS) {
    AppResult.Failure(AppError.ApiStatus(value.status))
} else {
    AppResult.Success(/* sorted breed list */)
}
```

This means a `{ "status": "error" }` body never silently produces empty content
— it becomes an explicit `Failure` that reaches the UI as an error state.

---

## Repository — failure logging

`BreedRepositoryImpl.refreshBreeds()` logs every failure at a single point
before passing it up the call chain:

```kotlin
result.onFailure { error -> Log.w(TAG, "Breed refresh failed: $error") }
```

Swapping `Log.w` for a Crashlytics or analytics call in the future is a
one-line change here.

---

## DataStore corruption

`DataStoreFavoritesDataSource.favorites` degrades gracefully if the on-disk
preferences file is corrupted or unreadable:

```kotlin
.catch { throwable ->
    if (throwable is IOException) emit(emptyPreferences()) else throw throwable
}
```

An `IOException` (corrupt file, permission loss) results in an empty favorites
set instead of a crash. Non-IO errors (programming bugs) are rethrown and
surface normally.

---

## ViewModel — UI state transitions

Both ViewModels translate `AppResult` into their screen's sealed `UiState`:

### `BreedListViewModel`

```
refreshBreeds() →  Success  →  LoadState.Loaded  →  BreedListUiState.Content
                →  Failure  →  LoadState.Failed  →  BreedListUiState.Error(error)
```

The search query is kept in a **separate** `StateFlow` outside `uiState`, so
text typed by the user survives `Loading` and `Error` state transitions — it is
never lost on retry.

### `BreedDetailViewModel`

The detail screen combines three independent flows — the cached breed entry,
the favorites set, and an optional `refreshError` — into one `uiState`:

```
breed != null              →  BreedDetailUiState.Content
breed == null, error != null  →  BreedDetailUiState.Error(error)
breed == null, error == null  →  BreedDetailUiState.Loading
```

`refreshError` is cleared to `null` at the start of every retry so the screen
returns to `Loading` rather than staying on the previous error message.

**Process death / cold cache:** if the detail screen is opened after process
death, `repository.cachedBreeds.value` is `null`. The ViewModel detects this in
`init` and triggers `refresh()` automatically, so a deep link or back-stack
restore still renders content instead of hanging on `Loading` forever.

---

## UI — error presentation

`ui/common/UiError.kt` maps each `AppError` case to a distinct string resource:

| `AppError` | String resource | User-visible message |
|---|---|---|
| `NoConnection` | `error_no_connection` | "No internet connection" |
| `Timeout` | `error_timeout` | "Request timed out" |
| `Http(code)` | `error_server` | "Server error (HTTP %d)" |
| `Serialization` | `error_unexpected_response` | "Unexpected response from server" |
| `ApiStatus` | `error_api_status` | "The server returned an error" |
| `Unknown` | `error_unknown` | "Something went wrong" |

`ErrorContent` (`ui/common/StateContent.kt`) renders the message alongside a
**Retry** button on both screens. Tapping Retry calls `ViewModel.retry()`, which
re-runs the network request and transitions back through `Loading → Content/Error`.

---

## Flow diagram

```
Network / IO
     │
     ▼
safeApiCall()          catches all Throwables → AppError
     │
     ▼
toBreeds()             checks "status" field  → AppError.ApiStatus
     │
     ▼
BreedRepositoryImpl    logs Failure, updates cache on Success
     │
     ▼
ViewModel              maps AppResult → sealed UiState
     │
     ▼
Screen composable      renders Loading / Error / Content
     │
     ▼
ErrorContent           localized message + Retry button
```

---

## Test coverage

| Test class | What it verifies |
|---|---|
| `SafeApiCallTest` | Each exception type maps to the correct `AppError`; `CancellationException` propagates |
| `BreedRepositoryImplTest` | HTTP 500 → `Http(500)`; garbage body → `Serialization`; `"status":"error"` → `ApiStatus`; connection refused → `NoConnection`; timeout → `Timeout` |
| `BreedListViewModelTest` | `Failure` result → `UiState.Error`; retry transitions back through `Loading` |
| `BreedDetailViewModelTest` | Cold-cache refresh on init; error cleared on retry |
| `UiErrorTest` | Every `AppError` maps to a distinct, non-empty string resource |
| `BreedListScreenTest` / `BreedDetailScreenTest` | `Error` state renders the error message and retry button nodes |
