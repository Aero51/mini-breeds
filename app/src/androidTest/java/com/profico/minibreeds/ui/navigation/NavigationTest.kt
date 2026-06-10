package com.profico.minibreeds.ui.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.profico.minibreeds.domain.model.Breed
import com.profico.minibreeds.domain.repository.BreedRepository
import com.profico.minibreeds.testutil.FakeBreedRepository
import com.profico.minibreeds.ui.breeddetail.BreedDetailTestTags
import com.profico.minibreeds.ui.breedlist.BreedListTestTags
import com.profico.minibreeds.ui.theme.MiniBreedsTheme
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.core.context.loadKoinModules
import org.koin.core.context.unloadKoinModules
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * End-to-end navigation tests against the real [MiniBreedsNavHost] and real
 * ViewModels. Only the repository is swapped: [MiniBreedsApp] has already
 * started global Koin when the test process launches, so each test overrides
 * the [BreedRepository] definition with a fake before composing the NavHost
 * (singles are lazy — nothing has resolved the real one yet).
 */
class NavigationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var fakeRepository: FakeBreedRepository
    private lateinit var testModule: Module

    @Before
    fun setUp() {
        fakeRepository = FakeBreedRepository(
            initialCache = listOf(
                Breed(name = "akita", subBreeds = emptyList()),
                Breed(name = "bulldog", subBreeds = listOf("boston", "french")),
            ),
        )
        testModule = module { single<BreedRepository> { fakeRepository } }
        loadKoinModules(testModule)
    }

    @After
    fun tearDown() {
        unloadKoinModules(testModule)
    }

    private fun setNavHost() {
        composeTestRule.setContent {
            MiniBreedsTheme {
                MiniBreedsNavHost()
            }
        }
    }

    private fun waitForTag(tag: String) {
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun startDestination_showsBreedList() {
        setNavHost()

        waitForTag(BreedListTestTags.row("bulldog"))
        composeTestRule.onNodeWithTag(BreedListTestTags.LIST).assertIsDisplayed()
        composeTestRule.onNodeWithTag(BreedListTestTags.row("akita")).assertIsDisplayed()
    }

    @Test
    fun clickingBreedRow_navigatesToDetailWithSubBreeds() {
        setNavHost()

        waitForTag(BreedListTestTags.row("bulldog"))
        composeTestRule.onNodeWithTag(BreedListTestTags.row("bulldog")).performClick()

        waitForTag(BreedDetailTestTags.TITLE)
        composeTestRule.onNodeWithTag(BreedDetailTestTags.TITLE).assertTextEquals("Bulldog")
        waitForTag(BreedDetailTestTags.subBreed("boston"))
        composeTestRule.onNodeWithTag(BreedDetailTestTags.subBreed("french")).assertIsDisplayed()
    }

    @Test
    fun backFromDetail_returnsToBreedList() {
        setNavHost()

        waitForTag(BreedListTestTags.row("akita"))
        composeTestRule.onNodeWithTag(BreedListTestTags.row("akita")).performClick()
        waitForTag(BreedDetailTestTags.BACK_BUTTON)

        composeTestRule.onNodeWithTag(BreedDetailTestTags.BACK_BUTTON).performClick()

        waitForTag(BreedListTestTags.LIST)
        composeTestRule.onNodeWithTag(BreedListTestTags.row("akita")).assertIsDisplayed()
    }

    @Test
    fun favoriteToggledOnDetail_isReflectedInList() {
        setNavHost()

        waitForTag(BreedListTestTags.row("bulldog"))
        composeTestRule.onNodeWithTag(BreedListTestTags.row("bulldog")).performClick()
        waitForTag(BreedDetailTestTags.FAVORITE_BUTTON)

        composeTestRule.onNodeWithTag(BreedDetailTestTags.FAVORITE_BUTTON).performClick()
        composeTestRule.onNodeWithTag(BreedDetailTestTags.BACK_BUTTON).performClick()

        waitForTag(BreedListTestTags.LIST)
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            fakeRepository.favoritesState.value == setOf("bulldog")
        }
    }
}
