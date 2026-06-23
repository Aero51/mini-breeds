package com.profico.minibreeds.ui.breedlist

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.profico.minibreeds.R
import com.profico.minibreeds.core.AppError
import com.profico.minibreeds.ui.common.CommonTestTags
import com.profico.minibreeds.ui.theme.MiniBreedsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** UI tests for the stateless [BreedListScreen]; no Koin, network, or ViewModel involved. */
class BreedListScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val contentState = BreedListUiState.Content(
        rows = listOf(
            BreedRowUi(name = "akita", subBreedCount = 0, isFavorite = false),
            BreedRowUi(name = "bulldog", subBreedCount = 3, isFavorite = true),
        ),
        noResultsForQuery = false,
    )

    /**
     * Wraps [BreedListScreen] in [MiniBreedsTheme] and installs it in the
     * Compose test rule. All callbacks default to no-ops; each test overrides
     * only the ones it asserts on.
     */
    private fun setScreen(
        uiState: BreedListUiState,
        query: String = "",
        onQueryChange: (String) -> Unit = {},
        onBreedClick: (String) -> Unit = {},
        onToggleFavorite: (String) -> Unit = {},
        onRetry: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            MiniBreedsTheme {
                BreedListScreen(
                    uiState = uiState,
                    query = query,
                    onQueryChange = onQueryChange,
                    onBreedClick = onBreedClick,
                    onToggleFavorite = onToggleFavorite,
                    onRetry = onRetry,
                )
            }
        }
    }

    /** Loading state shows the shared `LOADING_INDICATOR`. */
    @Test
    fun loadingState_showsProgressIndicator() {
        setScreen(uiState = BreedListUiState.Loading)

        composeTestRule.onNodeWithTag(CommonTestTags.LOADING_INDICATOR).assertIsDisplayed()
    }

    /**
     * Content state renders the `LIST`, title-cased breed names, and the
     * pluralised sub-breed count resolved from `R.plurals.sub_breed_count`.
     */
    @Test
    fun contentState_showsBreedRowsWithSubBreedCount() {
        setScreen(uiState = contentState)

        composeTestRule.onNodeWithTag(BreedListTestTags.LIST).assertIsDisplayed()
        composeTestRule.onNodeWithText("Akita").assertIsDisplayed()
        composeTestRule.onNodeWithText("Bulldog").assertIsDisplayed()
        val subBreedCount =
            composeTestRule.activity.resources.getQuantityString(R.plurals.sub_breed_count, 3, 3)
        composeTestRule.onNodeWithText(subBreedCount).assertIsDisplayed()
    }

    /** Clicking a row forwards that row's breed name through `onBreedClick`. */
    @Test
    fun rowClick_invokesCallbackWithBreedName() {
        val clicked = mutableListOf<String>()
        setScreen(uiState = contentState, onBreedClick = { clicked += it })

        composeTestRule.onNodeWithTag(BreedListTestTags.row("bulldog")).performClick()

        assertEquals(listOf("bulldog"), clicked)
    }

    /** Clicking a favorite icon forwards that row's name through `onToggleFavorite`. */
    @Test
    fun favoriteClick_invokesToggleCallbackWithBreedName() {
        val toggled = mutableListOf<String>()
        setScreen(uiState = contentState, onToggleFavorite = { toggled += it })

        composeTestRule.onNodeWithTag(BreedListTestTags.favorite("akita")).performClick()

        assertEquals(listOf("akita"), toggled)
    }

    /**
     * Error state renders the resolved string for the [AppError] and the
     * retry button forwards taps via `onRetry`.
     */
    @Test
    fun errorState_showsMessageAndRetryInvokesCallback() {
        var retried = false
        setScreen(
            uiState = BreedListUiState.Error(AppError.NoConnection),
            onRetry = { retried = true },
        )

        val message = composeTestRule.activity.getString(R.string.error_no_connection)
        composeTestRule.onNodeWithText(message).assertIsDisplayed()
        composeTestRule.onNodeWithTag(CommonTestTags.RETRY_BUTTON).performClick()

        assertTrue(retried)
    }

    /**
     * Typing into the search field invokes `onQueryChange`; the latest value
     * received is the full input string. Doesn't assert exact emission count
     * because IME-driven input may fire per-keystroke.
     */
    @Test
    fun typingInSearchField_invokesQueryChange() {
        val queries = mutableListOf<String>()
        setScreen(uiState = contentState, onQueryChange = { queries += it })

        composeTestRule.onNodeWithTag(BreedListTestTags.SEARCH_FIELD).performTextInput("husky")

        assertTrue("expected a query callback", queries.isNotEmpty())
        assertEquals("husky", queries.last())
    }

    /**
     * Tapping the clear icon (located by `R.string.search_clear` content
     * description) emits exactly one `""` query.
     */
    @Test
    fun clearSearchIcon_invokesQueryChangeWithEmptyString() {
        val queries = mutableListOf<String>()
        setScreen(uiState = contentState, query = "bull", onQueryChange = { queries += it })

        val clearSearch = composeTestRule.activity.getString(R.string.search_clear)
        composeTestRule.onNodeWithContentDescription(clearSearch).performClick()

        assertEquals(listOf(""), queries)
    }

    /** Empty filter result with the no-results flag set renders the `EMPTY_RESULTS` panel. */
    @Test
    fun emptyFilterResult_showsEmptyMessage() {
        setScreen(
            uiState = BreedListUiState.Content(rows = emptyList(), noResultsForQuery = true),
            query = "zzz",
        )

        composeTestRule.onNodeWithTag(BreedListTestTags.EMPTY_RESULTS).assertIsDisplayed()
    }
}
