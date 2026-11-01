package io.github.aljadvb.worldbuilder.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.aljadvb.worldbuilder.AppContainer
import io.github.aljadvb.worldbuilder.ui.game.GameScreen
import io.github.aljadvb.worldbuilder.ui.game.GameViewModel
import io.github.aljadvb.worldbuilder.ui.home.HomeScreen
import io.github.aljadvb.worldbuilder.ui.home.HomeViewModel

@Composable
fun AppNavigation(container: AppContainer) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "home") {
        composable("home") {
            val vm = viewModel { HomeViewModel(container) }
            LaunchedEffect(Unit) { vm.refresh() }
            HomeScreen(vm, onOpenGame = { campaignId, slot -> nav.navigate("game/$campaignId/$slot") })
        }
        composable("game/{campaignId}/{slot}") { entry ->
            val campaignId = entry.arguments?.getString("campaignId").orEmpty()
            val slot = entry.arguments?.getString("slot").orEmpty()
            val vm = viewModel { GameViewModel(container, campaignId, slot) }
            GameScreen(vm, onBack = { nav.popBackStack() })
        }
    }
}
