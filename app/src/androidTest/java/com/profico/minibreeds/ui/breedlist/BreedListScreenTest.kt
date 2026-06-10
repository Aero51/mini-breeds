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

    @Test
    fun loadingState_showsProgressIndicator() {
        setScreen(uiState = BreedListUiState.Loading)

        composeTestRule.onNodeWithTag(CommonTestTags.LOADING_INDICATOR).assertIsDisplayed()
    }

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

    @Test
    fun rowClick_invokesCallbackWithBreedName() {
        val clicked = mutableListOf<String>()
        setScreen(uiState = contentState, onBreedClick = { clicked += it })

        composeTestRule.onNodeWithTag(BreedListTestTags.row("bulldog")).performClick()

        assertEquals(listOf("bulldog"), clicked)
    }

    @Test
    fun favoriteClick_invokesToggleCallbackWithBreedName() {
        val toggled = mutableListOf<String>()
        setScreen(uiState = contentState, onToggleFavorite = { toggled += it })

        composeTestRule.onNodeWithTag(BreedListTestTags.favorite("akita")).performClick()

        assertEquals(listOf("akita"), toggled)
    }

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

    @Test
    fun typingInSearchField_invokesQueryChange() {
        val queries = mutableListOf<String>()
        setScreen(uiState = contentState, onQueryChange = { queries += it })

        composeTestRule.onNodeWithTag(BreedListTestTags.SEARCH_FIELD).performTextInput("husky")

        assertTrue("expected a query callback", queries.isNotEmpty())
        assertEquals("husky", queries.last())
    }

    @Test
    fun clearSearchIcon_invokesQueryChangeWithEmptyString() {
        val queries = mutableListOf<String>()
        setScreen(uiState = contentState, query = "bull", onQueryChange = { queries += it })

        val clearSearch = composeTestRule.activity.getString(R.string.search_clear)
        composeTestRule.onNodeWithContentDescription(clearSearch).performClick()

        assertEquals(listOf(""), queries)
    }

    @Test
    fun emptyFilterResult_showsEmptyMessage() {
        setScreen(
            uiState = BreedListUiState.Content(rows = emptyList(), noResultsForQuery = true),
            query = "zzz",
        )

        composeTestRule.onNodeWithTag(BreedListTestTags.EMPTY_RESULTS).assertIsDisplayed()
    }
}
