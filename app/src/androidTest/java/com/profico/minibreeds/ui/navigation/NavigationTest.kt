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

    /**
     * Builds a [FakeBreedRepository] pre-seeded with `akita` and `bulldog`,
     * wraps it in a Koin `single<BreedRepository>` override, and installs the
     * override module so the real ViewModels resolve the fake.
     */
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

    /** Removes the override so the next test starts from the real Koin graph. */
    @After
    fun tearDown() {
        unloadKoinModules(testModule)
    }

    /** Composes the real [MiniBreedsNavHost] inside [MiniBreedsTheme]. */
    private fun setNavHost() {
        composeTestRule.setContent {
            MiniBreedsTheme {
                MiniBreedsNavHost()
            }
        }
    }

    /**
     * Polls up to 5s for at least one semantic node with [tag]. Used because
     * the NavHost has async state-collection/navigation work and Compose may
     * not be idle when the test next interacts with the tree.
     */
    private fun waitForTag(tag: String) {
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** Start destination is the breed list; both seeded rows are rendered. */
    @Test
    fun startDestination_showsBreedList() {
        setNavHost()

        waitForTag(BreedListTestTags.row("bulldog"))
        composeTestRule.onNodeWithTag(BreedListTestTags.LIST).assertIsDisplayed()
        composeTestRule.onNodeWithTag(BreedListTestTags.row("akita")).assertIsDisplayed()
    }

    /**
     * Clicking a row navigates to detail with the correct title and sub-breed
     * nodes — verifies route argument plumbing through the real NavHost.
     */
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

    /** Back button on detail pops back to the list with the row still present. */
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

    /**
     * Toggling a favorite on the detail screen propagates through the real
     * VM/state flow plumbing back to the repository — verified by polling
     * `fakeRepository.favoritesState` for the expected value.
     */
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
