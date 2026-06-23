package com.profico.minibreeds.ui.breeddetail

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.profico.minibreeds.core.AppError
import com.profico.minibreeds.core.AppResult
import com.profico.minibreeds.domain.model.Breed
import com.profico.minibreeds.testutil.FakeBreedRepository
import com.profico.minibreeds.testutil.MainDispatcherRule
import com.profico.minibreeds.ui.navigation.BreedDetailRoute
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Unit tests for [BreedDetailViewModel]. Uses [MainDispatcherRule] to swap the
 * Main dispatcher, Turbine's `.test {}` for flow assertions, and
 * [FakeBreedRepository] for scripting repository responses.
 */
class BreedDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val breeds = listOf(
        Breed("bulldog", listOf("boston", "french")),
        Breed("akita", emptyList()),
    )

    /**
     * Builds a [SavedStateHandle] carrying the breed-name argument via the
     * literal `"breedName"` key. Per the project gotchas, the VM reads its
     * argument by key — not via `toRoute()` — so JVM tests work without a
     * real Android `Bundle`.
     */
    private fun savedStateHandleFor(breedName: String): SavedStateHandle =
        SavedStateHandle(mapOf("breedName" to breedName))

    /** Constructs the SUT bound to [repository] and the chosen [breedName]. */
    private fun viewModel(repository: FakeBreedRepository, breedName: String = "bulldog") =
        BreedDetailViewModel(savedStateHandleFor(breedName), repository)

    /**
     * With the cache already warm, the VM resolves the breed without calling
     * `refreshBreeds`; final UI state is [BreedDetailUiState.Content] with the
     * expected name and sub-breeds.
     */
    @Test
    fun `warm cache renders content without refreshing`() = runTest {
        val repository = FakeBreedRepository(initialCache = breeds)

        val vm = viewModel(repository)

        vm.uiState.test {
            var state = awaitItem()
            if (state is BreedDetailUiState.Loading) state = awaitItem()

            val content = state as BreedDetailUiState.Content
            assertEquals("bulldog", content.name)
            assertEquals(listOf("boston", "french"), content.subBreeds)
            assertEquals(0, repository.refreshCallCount)
        }
    }

    /**
     * Cold cache + a scripted success: the VM triggers exactly one refresh and
     * then settles on [BreedDetailUiState.Content].
     */
    @Test
    fun `cold cache triggers refresh then renders content`() = runTest {
        val repository = FakeBreedRepository(initialCache = null).apply {
            refreshResults += AppResult.Success(breeds)
        }

        val vm = viewModel(repository)

        vm.uiState.test {
            var state = awaitItem()
            while (state is BreedDetailUiState.Loading) state = awaitItem()

            assertTrue(state is BreedDetailUiState.Content)
            assertEquals(1, repository.refreshCallCount)
        }
    }

    /**
     * Cold cache + scripted "fail then succeed": the VM first surfaces
     * [BreedDetailUiState.Error]; calling [BreedDetailViewModel.retry] then
     * drives the second result through to [BreedDetailUiState.Content].
     */
    @Test
    fun `cold cache refresh failure surfaces Error then retry recovers`() = runTest {
        val repository = FakeBreedRepository(initialCache = null).apply {
            refreshResults += AppResult.Failure(AppError.Timeout)
            refreshResults += AppResult.Success(breeds)
        }

        val vm = viewModel(repository)

        vm.uiState.test {
            var state = awaitItem()
            while (state is BreedDetailUiState.Loading) state = awaitItem()
            assertEquals(BreedDetailUiState.Error(AppError.Timeout), state)

            vm.retry()

            var next = awaitItem()
            while (next is BreedDetailUiState.Loading) next = awaitItem()
            assertTrue(next is BreedDetailUiState.Content)
        }
    }

    /**
     * Toggling the favorite emits a new [BreedDetailUiState.Content] with
     * `isFavorite = true` and records the call on the fake repository.
     */
    @Test
    fun `favorite toggle is reflected in content`() = runTest {
        val repository = FakeBreedRepository(initialCache = breeds)
        val vm = viewModel(repository)

        vm.uiState.test {
            var state = awaitItem()
            if (state is BreedDetailUiState.Loading) state = awaitItem()
            assertTrue(!(state as BreedDetailUiState.Content).isFavorite)

            vm.onToggleFavorite()

            val updated = awaitItem() as BreedDetailUiState.Content
            assertTrue(updated.isFavorite)
            assertEquals(listOf("bulldog"), repository.toggledNames)
        }
    }

    /**
     * Successful image fetch puts the URL into [BreedDetailUiState.Content]
     * (drains intermediate emissions until `imageUrl` lands).
     */
    @Test
    fun `successful image fetch puts the url in content`() = runTest {
        val repository = FakeBreedRepository(initialCache = breeds).apply {
            imageResult = AppResult.Success("https://images.dog.ceo/bulldog.jpg")
        }

        val vm = viewModel(repository)

        vm.uiState.test {
            var state = awaitItem()
            while (state !is BreedDetailUiState.Content || state.imageUrl == null) {
                state = awaitItem()
            }
            assertEquals("https://images.dog.ceo/bulldog.jpg", state.imageUrl)
        }
    }

    /**
     * A failed image fetch must not break the text content: the final
     * [BreedDetailUiState.Content] keeps name/sub-breeds, `imageUrl` is `null`,
     * and no further emissions follow.
     */
    @Test
    fun `failed image fetch keeps a null url with content intact`() = runTest {
        val repository = FakeBreedRepository(initialCache = breeds).apply {
            imageResult = AppResult.Failure(AppError.Timeout)
        }

        val vm = viewModel(repository)

        vm.uiState.test {
            var state = awaitItem()
            while (state !is BreedDetailUiState.Content) state = awaitItem()
            assertNull(state.imageUrl)
            assertEquals("bulldog", state.name)
            assertEquals(listOf("boston", "french"), state.subBreeds)
            expectNoEvents()
        }
    }

    /**
     * Constructor contract: an empty [SavedStateHandle] (no breed-name arg)
     * fails fast with [IllegalStateException].
     */
    @Test
    fun `missing breed name argument fails fast`() {
        val repository = FakeBreedRepository(initialCache = breeds)

        assertThrows(IllegalStateException::class.java) {
            BreedDetailViewModel(SavedStateHandle(), repository)
        }
    }

    /**
     * Stale-route guard: VM created for an unknown breed against a populated
     * cache stays in [BreedDetailUiState.Loading] forever instead of crashing.
     */
    @Test
    fun `unknown breed with warm cache stays Loading rather than crashing`() = runTest {
        val repository = FakeBreedRepository(initialCache = breeds)

        val vm = viewModel(repository, breedName = "not-a-breed")

        vm.uiState.test {
            assertEquals(BreedDetailUiState.Loading, awaitItem())
            expectNoEvents()
        }
    }
}
