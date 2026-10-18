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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.beoffline.app.accountability.AccountabilityRepository
import com.beoffline.app.ui.screens.AccessibilityDisclosureScreen
import com.beoffline.app.ui.screens.AppPickerScreen
import com.beoffline.app.ui.screens.BlockCreatorScreen
import com.beoffline.app.ui.screens.BlockCreatorViewModel
import com.beoffline.app.ui.screens.GroupChatScreen
import com.beoffline.app.ui.screens.MainShell
import com.beoffline.app.ui.screens.SettingsScreen
import com.beoffline.app.ui.screens.ShellTab
import com.beoffline.app.ui.screens.WelcomeScreen

sealed class Screen(val route: String) {
    /** The tabbed shell: Your apps, People. */
    object Shell : Screen("shell")

    /**
     * The first-run flow. Shown once, before the shell has anything to list:
     * it asks what you want to put down, for how long, and only then asks for
     * the one permission the answer actually needs.
     */
    object Welcome : Screen("welcome")

    /**
     * The one creator, for both strictness levels. [lock] decides which table
     * the result lands in, and the user never sees the word.
     */
    object BlockCreator : Screen("block_creator?ruleId={ruleId}&lock={lock}") {
        fun createRoute(ruleId: Int? = null, lock: Boolean = false) =
            buildString {
                append("block_creator?lock=$lock")
                if (ruleId != null) append("&ruleId=$ruleId")
            }
    }

    object AppPicker : Screen("app_picker")
    object Settings : Screen("settings")
    object OpenBlockDisclosure : Screen("open_block_disclosure")

    object GroupChat : Screen("group_chat/{groupId}") {
        fun createRoute(groupId: String) = "group_chat/$groupId"
    }
}

@Composable
fun BeOfflineNavGraph(
    onRequestVpn: (List<String>) -> Unit,
    startOnWelcome: Boolean = false,
    navRoute: StateFlow<String?> = MutableStateFlow(null),
    onNavRouteHandled: () -> Unit = {}
) {
    val navController = rememberNavController()
    var selectedTab by rememberSaveable { mutableStateOf(ShellTab.Apps) }

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
                selectedTab = ShellTab.People
                navController.popBackStack(Screen.Shell.route, inclusive = false)
            }
            else -> navController.navigate(target) { launchSingleTop = true }
        }
        onNavRouteHandled()
    }

    NavHost(
        navController = navController,
        startDestination = if (startOnWelcome) Screen.Welcome.route else Screen.Shell.route
    ) {
        composable(Screen.Welcome.route) {
            WelcomeScreen(
                onFinished = {
                    navController.navigate(Screen.Shell.route) {
                        popUpTo(Screen.Welcome.route) { inclusive = true }
                    }
                },
                onShowDisclosure = { navController.navigate(Screen.OpenBlockDisclosure.route) },
                onRequestVpn = onRequestVpn
            )
        }

        composable(Screen.Shell.route) {
            MainShell(
                selectedTab = selectedTab,
                onSelectTab = { selectedTab = it },
                onRequestVpn = onRequestVpn,
                onOpenApp = { row ->
                    navController.navigate(
                        Screen.BlockCreator.createRoute(ruleId = row.ruleId, lock = row.isLock)
                    )
                },
                onAddApp = { navController.navigate(Screen.BlockCreator.createRoute()) },
                onOpenSettings = { navController.navigate(Screen.Settings.route) },
                onOpenChat = { groupId -> navController.navigate(Screen.GroupChat.createRoute(groupId)) }
            )
        }

        composable(
            route = Screen.BlockCreator.route,
            arguments = listOf(
                navArgument("ruleId") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("lock") { type = NavType.BoolType; defaultValue = false }
            )
        ) { backStackEntry ->
            val ruleId = backStackEntry.arguments?.getString("ruleId")?.toIntOrNull()
            val lock = backStackEntry.arguments?.getBoolean("lock") ?: false
            val viewModel: BlockCreatorViewModel = hiltViewModel(backStackEntry)
            BlockCreatorScreen(
                ruleId = ruleId,
                startAsLock = lock,
                viewModel = viewModel,
                onNavigateToAppPicker = { navController.navigate(Screen.AppPicker.route) },
                onShowDisclosure = { navController.navigate(Screen.OpenBlockDisclosure.route) },
                onRequestVpn = onRequestVpn,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.AppPicker.route) { entry ->
            val creatorEntry = remember(entry) {
                navController.getBackStackEntry(Screen.BlockCreator.route)
            }
            val creatorViewModel: BlockCreatorViewModel = hiltViewModel(creatorEntry)

            AppPickerScreen(
                initiallySelected = creatorViewModel.uiState.value.selectedPackages,
                onDone = { selectedPackages ->
                    creatorViewModel.onPackagesSelected(selectedPackages)
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onRequestVpn = onRequestVpn,
                onBack = { navController.popBackStack() },
                onShowDisclosure = { navController.navigate(Screen.OpenBlockDisclosure.route) }
            )
        }

        composable(Screen.OpenBlockDisclosure.route) {
            AccessibilityDisclosureScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.GroupChat.route) {
            GroupChatScreen(onBack = { navController.popBackStack() })
        }
    }
}
