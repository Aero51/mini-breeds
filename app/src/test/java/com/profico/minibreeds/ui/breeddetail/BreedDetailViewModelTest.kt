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
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class BreedDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val breeds = listOf(
        Breed("bulldog", listOf("boston", "french")),
        Breed("akita", emptyList()),
    )

    private fun savedStateHandleFor(breedName: String): SavedStateHandle =
        SavedStateHandle(mapOf("breedName" to breedName))

    private fun viewModel(repository: FakeBreedRepository, breedName: String = "bulldog") =
        BreedDetailViewModel(savedStateHandleFor(breedName), repository)

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

    @Test
    fun `missing breed name argument fails fast`() {
        val repository = FakeBreedRepository(initialCache = breeds)

        assertThrows(IllegalStateException::class.java) {
            BreedDetailViewModel(SavedStateHandle(), repository)
        }
    }

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
