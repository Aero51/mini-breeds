package com.profico.minibreeds.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.profico.minibreeds.ui.breeddetail.BreedDetailScreen
import com.profico.minibreeds.ui.breedlist.BreedListScreen
import kotlinx.serialization.Serializable

/** Typed navigation route for the breed list (start) screen. */
@Serializable
object BreedListRoute

/** Typed navigation route for the detail screen; carries the selected breed name. */
@Serializable
data class BreedDetailRoute(val breedName: String)

/** Hosts the two destinations and wires navigation between them. */
@Composable
fun MiniBreedsNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = BreedListRoute) {
        composable<BreedListRoute> {
            BreedListScreen(
                onBreedClick = { breedName -> navController.navigate(BreedDetailRoute(breedName)) },
            )
        }
        composable<BreedDetailRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<BreedDetailRoute>()
            BreedDetailScreen(
                breedName = route.breedName,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
