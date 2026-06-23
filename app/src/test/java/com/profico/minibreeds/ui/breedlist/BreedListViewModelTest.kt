package com.profico.minibreeds.ui.breedlist

import app.cash.turbine.test
import com.profico.minibreeds.core.AppError
import com.profico.minibreeds.core.AppResult
import com.profico.minibreeds.domain.model.Breed
import com.profico.minibreeds.testutil.FakeBreedRepository
import com.profico.minibreeds.testutil.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Unit tests for [BreedListViewModel] using [MainDispatcherRule], Turbine, and
 * [FakeBreedRepository] for scripted refresh results.
 */
class BreedListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val breeds = listOf(
        Breed("akita", emptyList()),
        Breed("bulldog", listOf("boston", "french")),
        Breed("hound", listOf("afghan", "basset")),
    )

    /** Constructs the SUT bound to [repository]. */
    private fun viewModel(repository: FakeBreedRepository) = BreedListViewModel(repository)

    /**
     * Cold start: the VM emits [BreedListUiState.Loading] first, then
     * [BreedListUiState.Content] with rows in API order and the right
     * sub-breed counts (`bulldog` reports `2`).
     */
    @Test
    fun `starts in Loading then shows content`() = runTest {
        val repository = FakeBreedRepository().apply {
            refreshResults += AppResult.Success(breeds)
        }

        val vm = viewModel(repository)

        vm.uiState.test {
            assertEquals(BreedListUiState.Loading, awaitItem())

            val content = awaitItem() as BreedListUiState.Content
            assertEquals(listOf("akita", "bulldog", "hound"), content.rows.map { it.name })
            assertEquals(2, content.rows.first { it.name == "bulldog" }.subBreedCount)
        }
    }

    /**
     * Failure → retry → success: the VM walks Loading → Error → Loading →
     * Content and reports two `refreshBreeds` calls (StateFlow may conflate
     * the intermediate Loading).
     */
    @Test
    fun `failed load shows Error then retry recovers`() = runTest {
        val repository = FakeBreedRepository().apply {
            refreshResults += AppResult.Failure(AppError.NoConnection)
            refreshResults += AppResult.Success(breeds)
        }

        val vm = viewModel(repository)

        vm.uiState.test {
            assertEquals(BreedListUiState.Loading, awaitItem())
            assertEquals(BreedListUiState.Error(AppError.NoConnection), awaitItem())

            vm.retry()

            // StateFlow may conflate the intermediate Loading into the final state.
            var state = awaitItem()
            if (state is BreedListUiState.Loading) state = awaitItem()
            assertTrue(state is BreedListUiState.Content)
            assertEquals(2, repository.refreshCallCount)
        }
    }

    /** A query of `"BULL"` filters the rows case-insensitively to `[bulldog]`. */
    @Test
    fun `query filters case-insensitively`() = runTest {
        val repository = FakeBreedRepository().apply {
            refreshResults += AppResult.Success(breeds)
        }
        val vm = viewModel(repository)

        vm.uiState.test {
            awaitItem() // Loading
            awaitItem() // full Content

            vm.onQueryChange("BULL")

            val filtered = awaitItem() as BreedListUiState.Content
            assertEquals(listOf("bulldog"), filtered.rows.map { it.name })
        }
    }

    /**
     * Genuine empty API response: [BreedListUiState.Content] with no rows and
     * `noResultsForQuery = false`, so the UI doesn't mistake it for an empty
     * *search* result.
     */
    @Test
    fun `empty breed list is Content without the no-results flag`() = runTest {
        // A genuinely empty list (API returned nothing) must not be mistaken for
        // an empty *search* result, so noResultsForQuery stays false.
        val repository = FakeBreedRepository().apply {
            refreshResults += AppResult.Success(emptyList())
        }
        val vm = viewModel(repository)

        vm.uiState.test {
            assertEquals(BreedListUiState.Loading, awaitItem())

            val content = awaitItem() as BreedListUiState.Content
            assertTrue(content.rows.isEmpty())
            assertTrue(!content.noResultsForQuery)
        }
    }

    /** Whitespace around the query is trimmed before filtering. */
    @Test
    fun `query is trimmed before filtering`() = runTest {
        val repository = FakeBreedRepository().apply {
            refreshResults += AppResult.Success(breeds)
        }
        val vm = viewModel(repository)

        vm.uiState.test {
            awaitItem() // Loading
            awaitItem() // full Content

            vm.onQueryChange("  bull  ")

            val filtered = awaitItem() as BreedListUiState.Content
            assertEquals(listOf("bulldog"), filtered.rows.map { it.name })
            assertTrue(!filtered.noResultsForQuery)
        }
    }

    /**
     * A query that matches no breed yields empty rows with
     * `noResultsForQuery = true`, which the UI uses to show the "no matches"
     * panel.
     */
    @Test
    fun `query with no matches sets noResultsForQuery`() = runTest {
        val repository = FakeBreedRepository().apply {
            refreshResults += AppResult.Success(breeds)
        }
        val vm = viewModel(repository)

        vm.uiState.test {
            awaitItem() // Loading
            awaitItem() // full Content

            vm.onQueryChange("zzz")

            val filtered = awaitItem() as BreedListUiState.Content
            assertTrue(filtered.rows.isEmpty())
            assertTrue(filtered.noResultsForQuery)
        }
    }

    /**
     * Toggling a favorite flips that row's `isFavorite` flag in the next
     * emission and records the call on the fake repository.
     */
    @Test
    fun `favorite changes are reflected in rows`() = runTest {
        val repository = FakeBreedRepository().apply {
            refreshResults += AppResult.Success(breeds)
        }
        val vm = viewModel(repository)

        vm.uiState.test {
            awaitItem() // Loading
            val before = awaitItem() as BreedListUiState.Content
            assertTrue(before.rows.none { it.isFavorite })

            vm.onToggleFavorite("hound")

            val after = awaitItem() as BreedListUiState.Content
            assertTrue(after.rows.first { it.name == "hound" }.isFavorite)
            assertEquals(listOf("hound"), repository.toggledNames)
        }
    }

    /** Setting the query back to `""` after filtering restores the full row list. */
    @Test
    fun `clearing the query restores the full list`() = runTest {
        val repository = FakeBreedRepository().apply {
            refreshResults += AppResult.Success(breeds)
        }
        val vm = viewModel(repository)

        vm.uiState.test {
            awaitItem() // Loading
            awaitItem() // full Content
            vm.onQueryChange("akita")
            awaitItem() // filtered

            vm.onQueryChange("")

            val restored = awaitItem() as BreedListUiState.Content
            assertEquals(3, restored.rows.size)
        }
    }
}
