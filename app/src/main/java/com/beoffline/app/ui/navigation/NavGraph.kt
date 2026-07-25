package com.beoffline.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.beoffline.app.accountability.AccountabilityRepository
import com.beoffline.app.ui.screens.AccessibilityDisclosureScreen
import com.beoffline.app.ui.screens.AppPickerScreen
import com.beoffline.app.ui.screens.GroupChatScreen
import com.beoffline.app.ui.screens.MainShell
import com.beoffline.app.ui.screens.OpenBlockRuleCreatorScreen
import com.beoffline.app.ui.screens.OpenBlockRuleCreatorViewModel
import com.beoffline.app.ui.screens.RuleCreatorScreen
import com.beoffline.app.ui.screens.RuleCreatorViewModel
import com.beoffline.app.ui.screens.ShellTab

sealed class Screen(val route: String) {
    /** The tabbed shell: Home, Rules, App Lock, Social, Settings. */
    object Shell : Screen("shell")

    object RuleCreator : Screen("rule_creator?ruleId={ruleId}") {
        fun createRoute(ruleId: Int? = null) =
            buildString {
                append("rule_creator")
                if (ruleId != null) append("?ruleId=$ruleId")
            }
    }
    object AppPicker : Screen("app_picker")

    // ── App Lock (open-block) feature area ────────────────────────────────
    object OpenBlockDisclosure : Screen("open_block_disclosure")
    object OpenBlockRuleCreator : Screen("open_block_rule_creator?ruleId={ruleId}") {
        fun createRoute(ruleId: Int? = null) =
            buildString {
                append("open_block_rule_creator")
                if (ruleId != null) append("?ruleId=$ruleId")
            }
    }
    object OpenBlockAppPicker : Screen("open_block_app_picker")
    object GroupChat : Screen("group_chat/{groupId}") {
        fun createRoute(groupId: String) = "group_chat/$groupId"
    }
}

@Composable
fun BeOfflineNavGraph(
    onRequestVpn: (List<String>) -> Unit,
    navRoute: StateFlow<String?> = MutableStateFlow(null),
    onNavRouteHandled: () -> Unit = {}
) {
    val navController = rememberNavController()
    var selectedTab by rememberSaveable { mutableStateOf(ShellTab.Home) }

    // Notification deep-link: navigate once to the requested route, then clear
    // it so rotation/recomposition doesn't re-trigger the jump. Anything that
    // used to be its own screen but now lives in a tab resolves to selecting
    // that tab instead of pushing a destination.
    val route by navRoute.collectAsState()
    LaunchedEffect(route) {
        val target = route ?: return@LaunchedEffect
        when (target) {
            AccountabilityRepository.ROUTE_ACCOUNTABILITY,
            AccountabilityRepository.ROUTE_GROUPS -> {
                selectedTab = ShellTab.Social
                navController.popBackStack(Screen.Shell.route, inclusive = false)
            }
            else -> navController.navigate(target) { launchSingleTop = true }
        }
        onNavRouteHandled()
    }

    NavHost(
        navController = navController,
        startDestination = Screen.Shell.route
    ) {
        composable(Screen.Shell.route) {
            MainShell(
                selectedTab = selectedTab,
                onSelectTab = { selectedTab = it },
                onRequestVpn = onRequestVpn,
                onCreateRule = { navController.navigate(Screen.RuleCreator.createRoute()) },
                onEditRule = { ruleId -> navController.navigate(Screen.RuleCreator.createRoute(ruleId)) },
                onCreateLock = { navController.navigate(Screen.OpenBlockRuleCreator.createRoute()) },
                onEditLock = { ruleId ->
                    navController.navigate(Screen.OpenBlockRuleCreator.createRoute(ruleId))
                },
                onShowDisclosure = { navController.navigate(Screen.OpenBlockDisclosure.route) },
                onOpenChat = { groupId -> navController.navigate(Screen.GroupChat.createRoute(groupId)) }
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
