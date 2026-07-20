package com.beoffline.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.beoffline.app.ui.screens.AccessibilityDisclosureScreen
import com.beoffline.app.ui.screens.AccountabilityScreen
import com.beoffline.app.ui.screens.AppPickerScreen
import com.beoffline.app.ui.screens.DashboardScreen
import com.beoffline.app.ui.screens.GroupChatScreen
import com.beoffline.app.ui.screens.GroupsScreen
import com.beoffline.app.ui.screens.OpenBlockRuleCreatorScreen
import com.beoffline.app.ui.screens.OpenBlockRuleCreatorViewModel
import com.beoffline.app.ui.screens.OpenBlockScreen
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

    // ── App Lock (open-block) feature area ────────────────────────────────
    object OpenBlock : Screen("open_block")
    object OpenBlockDisclosure : Screen("open_block_disclosure")
    object OpenBlockRuleCreator : Screen("open_block_rule_creator?ruleId={ruleId}") {
        fun createRoute(ruleId: Int? = null) =
            buildString {
                append("open_block_rule_creator")
                if (ruleId != null) append("?ruleId=$ruleId")
            }
    }
    object OpenBlockAppPicker : Screen("open_block_app_picker")
    object Accountability : Screen("accountability")
    object Groups : Screen("groups")
    object GroupChat : Screen("group_chat/{groupId}") {
        fun createRoute(groupId: String) = "group_chat/$groupId"
    }
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
                onRequestVpn = onRequestVpn,
                onOpenAppLock = { navController.navigate(Screen.OpenBlock.route) }
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

        // ── App Lock (open-block) feature area ────────────────────────────

        composable(Screen.OpenBlock.route) {
            OpenBlockScreen(
                onBack = { navController.popBackStack() },
                onCreateRule = { navController.navigate(Screen.OpenBlockRuleCreator.createRoute()) },
                onEditRule = { ruleId ->
                    navController.navigate(Screen.OpenBlockRuleCreator.createRoute(ruleId))
                },
                onShowDisclosure = { navController.navigate(Screen.OpenBlockDisclosure.route) },
                onOpenAccountability = { navController.navigate(Screen.Accountability.route) }
            )
        }

        composable(Screen.Accountability.route) {
            AccountabilityScreen(
                onBack = { navController.popBackStack() },
                onOpenGroups = { navController.navigate(Screen.Groups.route) }
            )
        }

        composable(Screen.Groups.route) {
            GroupsScreen(
                onBack = { navController.popBackStack() },
                onOpenChat = { groupId ->
                    navController.navigate(Screen.GroupChat.createRoute(groupId))
                }
            )
        }

        composable(Screen.GroupChat.route) {
            GroupChatScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.OpenBlockDisclosure.route) {
            AccessibilityDisclosureScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.OpenBlockRuleCreator.route) { backStackEntry ->
            val ruleId = backStackEntry.arguments?.getString("ruleId")?.toIntOrNull()
            val viewModel: OpenBlockRuleCreatorViewModel = hiltViewModel(backStackEntry)
            OpenBlockRuleCreatorScreen(
                ruleId = ruleId,
                viewModel = viewModel,
                onNavigateToAppPicker = {
                    navController.navigate(Screen.OpenBlockAppPicker.route)
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.OpenBlockAppPicker.route) { entry ->
            // Reuse the same AppPickerScreen, bound to the open-block creator's ViewModel.
            val creatorEntry = remember(entry) {
                navController.getBackStackEntry(Screen.OpenBlockRuleCreator.route)
            }
            val creatorViewModel: OpenBlockRuleCreatorViewModel = hiltViewModel(creatorEntry)

            AppPickerScreen(
                initiallySelected = creatorViewModel.uiState.value.selectedPackages,
                onDone = { selectedPackages ->
                    creatorViewModel.onPackagesSelected(selectedPackages)
                    navController.popBackStack()
                }
            )
        }
    }
}
