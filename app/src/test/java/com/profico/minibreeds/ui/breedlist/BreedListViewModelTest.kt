package com.profico.minibreeds.ui.breedlist

import com.profico.minibreeds.data.Breed
import com.profico.minibreeds.data.FakeBreedRepository
import com.profico.minibreeds.testutil.MainDispatcherRule
import java.net.UnknownHostException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BreedListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val breeds = listOf(
        Breed("akita", emptyList()),
        Breed("bulldog", listOf("boston", "french")),
    )

    /** Builds the ViewModel and lets its init load finish before running [block]. */
    private fun runViewModelTest(
        repository: FakeBreedRepository,
        block: suspend TestScope.(BreedListViewModel) -> Unit,
    ) = runTest {
        val viewModel = BreedListViewModel(repository)
        advanceUntilIdle()
        block(viewModel)
    }

    @Test
    fun `loads breeds into content`() = runViewModelTest(FakeBreedRepository(breeds)) { viewModel ->
        val state = viewModel.uiState.value as BreedListUiState.Content
        assertEquals(listOf("akita", "bulldog"), state.breeds.map { it.name })
    }

    @Test
    fun `query filters the list`() = runViewModelTest(FakeBreedRepository(breeds)) { viewModel ->
        viewModel.onQueryChange("bull")
        advanceUntilIdle()

        val state = viewModel.uiState.value as BreedListUiState.Content
        assertEquals(listOf("bulldog"), state.breeds.map { it.name })
    }

    @Test
    fun `toggling a favorite marks the row`() = runViewModelTest(FakeBreedRepository(breeds)) { viewModel ->
        viewModel.onToggleFavorite("akita")
        advanceUntilIdle()

        val state = viewModel.uiState.value as BreedListUiState.Content
        assertTrue(state.breeds.first { it.name == "akita" }.isFavorite)
    }

    @Test
    fun `network failure shows offline error`() =
        runViewModelTest(FakeBreedRepository(errorOnGet = UnknownHostException())) { viewModel ->
            val state = viewModel.uiState.value as BreedListUiState.Error
            assertTrue(state.isOffline)
        }

    @Test
    fun `other failure shows generic error`() =
        runViewModelTest(FakeBreedRepository(errorOnGet = IllegalStateException("boom"))) { viewModel ->
            val state = viewModel.uiState.value as BreedListUiState.Error
            assertTrue(!state.isOffline)
        }
}
