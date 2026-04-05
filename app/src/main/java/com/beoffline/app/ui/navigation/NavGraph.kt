package com.beoffline.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.beoffline.app.ui.screens.AppPickerScreen
import com.beoffline.app.ui.screens.DashboardScreen
import com.beoffline.app.ui.screens.RuleCreatorScreen
import com.beoffline.app.ui.screens.RuleCreatorViewModel

sealed class Screen(val route: String) {
    object Dashboard : Screen("dashboard")
    object RuleCreator : Screen("rule_creator?ruleId={ruleId}") {
        fun createRoute(ruleId: Int? = null) =
            buildString {
                append("rule_creator")
                if (ruleId != null) append("?ruleId=$ruleId")
            }
    }
    object AppPicker : Screen("app_picker")
}

@Composable
fun BeOfflineNavGraph(onRequestVpn: (List<String>) -> Unit) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.Dashboard.route
    ) {
        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onCreateRule = { navController.navigate(Screen.RuleCreator.createRoute()) },
                onEditRule = { ruleId -> navController.navigate(Screen.RuleCreator.createRoute(ruleId)) },
                onRequestVpn = onRequestVpn
            )
        }

        composable(Screen.RuleCreator.route) { backStackEntry ->
            val ruleId = backStackEntry.arguments?.getString("ruleId")?.toIntOrNull()
            val viewModel: RuleCreatorViewModel = hiltViewModel(backStackEntry)
            RuleCreatorScreen(
                ruleId = ruleId,
                viewModel = viewModel,
                onNavigateToAppPicker = {
                    navController.navigate(Screen.AppPicker.route)
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.AppPicker.route) { entry ->
            val ruleCreatorEntry = remember(entry) {
                navController.getBackStackEntry(Screen.RuleCreator.route)
            }
            val ruleCreatorViewModel: RuleCreatorViewModel = hiltViewModel(ruleCreatorEntry)

            AppPickerScreen(
                initiallySelected = ruleCreatorViewModel.uiState.value.selectedPackages,
                onDone = { selectedPackages ->
                    ruleCreatorViewModel.onPackagesSelected(selectedPackages)
                    navController.popBackStack()
                }
            )
        }
    }
}
