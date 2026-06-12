package com.profico.minibreeds.ui.breeddetail

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.profico.minibreeds.R
import com.profico.minibreeds.core.AppError
import com.profico.minibreeds.ui.common.CommonTestTags
import com.profico.minibreeds.ui.theme.MiniBreedsTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** UI tests for the stateless [BreedDetailScreen]; no Koin, network, or ViewModel involved. */
class BreedDetailScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val contentState = BreedDetailUiState.Content(
        name = "bulldog",
        subBreeds = listOf("boston", "english", "french"),
        isFavorite = false,
    )

    private fun setScreen(
        uiState: BreedDetailUiState,
        onNavigateBack: () -> Unit = {},
        onToggleFavorite: () -> Unit = {},
        onRetry: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            MiniBreedsTheme {
                BreedDetailScreen(
                    uiState = uiState,
                    onNavigateBack = onNavigateBack,
                    onToggleFavorite = onToggleFavorite,
                    onRetry = onRetry,
                )
            }
        }
    }

    @Test
    fun contentState_showsCapitalizedTitleAndSubBreeds() {
        setScreen(uiState = contentState)

        composeTestRule.onNodeWithTag(BreedDetailTestTags.TITLE).assertTextEquals("Bulldog")
        composeTestRule.onNodeWithText("Boston").assertIsDisplayed()
        composeTestRule.onNodeWithText("English").assertIsDisplayed()
        composeTestRule.onNodeWithText("French").assertIsDisplayed()
    }

    @Test
    fun longSubBreedList_isScrollable_lastEntryReachable() {
        // Regression test for landscape/small viewports: the header and the
        // sub-breeds live in one LazyColumn, so off-screen content must be
        // reachable by scrolling the whole detail content.
        val many = (1..40).map { "sub$it" }
        setScreen(uiState = contentState.copy(subBreeds = many))

        composeTestRule.onNodeWithTag(BreedDetailTestTags.CONTENT)
            .performScrollToNode(hasTestTag(BreedDetailTestTags.subBreed("sub40")))

        composeTestRule.onNodeWithTag(BreedDetailTestTags.subBreed("sub40")).assertIsDisplayed()
    }

    @Test
    fun contentStateWithoutSubBreeds_showsPlaceholder() {
        setScreen(uiState = contentState.copy(subBreeds = emptyList()))

        composeTestRule.onNodeWithTag(BreedDetailTestTags.NO_SUB_BREEDS).assertIsDisplayed()
    }

    @Test
    fun backButton_invokesCallback() {
        var backInvoked = false
        setScreen(uiState = contentState, onNavigateBack = { backInvoked = true })

        composeTestRule.onNodeWithTag(BreedDetailTestTags.BACK_BUTTON).performClick()

        assertTrue(backInvoked)
    }

    @Test
    fun favoriteButton_invokesToggleCallback() {
        var toggleInvoked = false
        setScreen(uiState = contentState, onToggleFavorite = { toggleInvoked = true })

        composeTestRule.onNodeWithTag(BreedDetailTestTags.FAVORITE_BUTTON).performClick()

        assertTrue(toggleInvoked)
    }

    @Test
    fun errorState_showsMessageAndRetryInvokesCallback() {
        var retried = false
        setScreen(
            uiState = BreedDetailUiState.Error(AppError.Timeout),
            onRetry = { retried = true },
        )

        val message = composeTestRule.activity.getString(R.string.error_timeout)
        composeTestRule.onNodeWithText(message).assertIsDisplayed()
        composeTestRule.onNodeWithTag(CommonTestTags.RETRY_BUTTON).performClick()

        assertTrue(retried)
    }

    @Test
    fun loadingState_showsProgressIndicator() {
        setScreen(uiState = BreedDetailUiState.Loading)

        composeTestRule.onNodeWithTag(CommonTestTags.LOADING_INDICATOR).assertIsDisplayed()
    }

    @Test
    fun contentStateWithImageUrl_showsBreedPhoto() {
        setScreen(uiState = contentState.copy(imageUrl = "https://example.com/dog.jpg"))

        composeTestRule.onNodeWithTag(BreedDetailTestTags.IMAGE).assertIsDisplayed()
    }

    @Test
    fun contentStateWithoutImageUrl_fallsBackToAvatar() {
        setScreen(uiState = contentState.copy(imageUrl = null))

        composeTestRule.onNodeWithTag(BreedDetailTestTags.IMAGE).assertDoesNotExist()
        composeTestRule.onNodeWithTag(BreedDetailTestTags.TITLE).assertIsDisplayed()
    }
}
