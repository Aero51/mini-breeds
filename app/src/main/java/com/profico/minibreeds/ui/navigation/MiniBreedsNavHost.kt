package com.profico.minibreeds.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.profico.minibreeds.ui.breeddetail.BreedDetailRoute as BreedDetailRouteScreen
import com.profico.minibreeds.ui.breedlist.BreedListRoute as BreedListRouteScreen

/**
 * Root [NavHost] for the app. Registers the breed list and breed detail destinations
 * and wires up the navigation callbacks between them.
 */
@Composable
fun MiniBreedsNavHost(
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = BreedListRoute,
    ) {
        composable<BreedListRoute> {
            BreedListRouteScreen(
                onBreedClick = { breedName ->
                    navController.navigate(BreedDetailRoute(breedName))
                },
            )
        }
        composable<BreedDetailRoute> {
            BreedDetailRouteScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }
    }
}
