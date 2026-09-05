package com.buyanumber.app.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.buyanumber.app.ui.components.LoadingState
import com.buyanumber.app.ui.screens.account.AccountScreen
import com.buyanumber.app.ui.screens.buy.BuyScreen
import com.buyanumber.app.ui.screens.dashboard.DashboardScreen
import com.buyanumber.app.ui.screens.deposit.DepositScreen
import com.buyanumber.app.ui.screens.orderdetail.OrderDetailScreen
import com.buyanumber.app.ui.screens.orders.OrdersScreen
import com.buyanumber.app.ui.screens.signin.SignInScreen

/**
 * Root of the UI. The sign-in screen and the main shell are separate graphs so
 * that disconnecting a key cannot leave authenticated screens on the back stack.
 */
@Composable
fun BuyANumberApp(
    pendingOrderId: Long?,
    onPendingOrderHandled: () -> Unit,
    viewModel: AppViewModel = hiltViewModel(),
) {
    val session by viewModel.sessionState.collectAsStateWithLifecycle()

    when (session) {
        SessionState.LOADING -> Box(Modifier.fillMaxSize()) { LoadingState() }
        SessionState.SIGNED_OUT -> SignInScreen()
        SessionState.SIGNED_IN -> MainShell(
            pendingOrderId = pendingOrderId,
            onPendingOrderHandled = onPendingOrderHandled,
        )
    }
}

@Composable
private fun MainShell(
    pendingOrderId: Long?,
    onPendingOrderHandled: () -> Unit,
) {
    val navController = rememberNavController()

    // A tapped SMS notification opens straight to that number.
    LaunchedEffect(pendingOrderId) {
        pendingOrderId?.let { orderId ->
            navController.navigate(Routes.orderDetail(orderId))
            onPendingOrderHandled()
        }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val showBottomBar = TopLevelDestination.entries.any { destination ->
        currentDestination?.hierarchy?.any { it.route == destination.route } == true
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    TopLevelDestination.entries.forEach { destination ->
                        val selected =
                            currentDestination?.hierarchy?.any { it.route == destination.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navController.navigateToTab(destination) },
                            icon = { Icon(destination.icon, contentDescription = destination.label) },
                            label = { Text(destination.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.DASHBOARD,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.DASHBOARD) {
                DashboardScreen(
                    onOpenOrder = { navController.navigate(Routes.orderDetail(it)) },
                    onBuyNumber = { navController.navigateToTab(TopLevelDestination.BUY) },
                    onAddFunds = { navController.navigate(Routes.DEPOSIT) },
                )
            }

            composable(Routes.BUY) {
                BuyScreen(
                    onOrderPurchased = { navController.navigate(Routes.orderDetail(it)) },
                )
            }

            composable(Routes.ORDERS) {
                OrdersScreen(onOpenOrder = { navController.navigate(Routes.orderDetail(it)) })
            }

            composable(Routes.ACCOUNT) {
                AccountScreen(onAddFunds = { navController.navigate(Routes.DEPOSIT) })
            }

            composable(Routes.DEPOSIT) {
                DepositScreen(onBack = { navController.popBackStack() })
            }

            composable(
                route = Routes.ORDER_DETAIL,
                arguments = listOf(navArgument(Routes.ORDER_ID_ARG) { type = NavType.LongType }),
            ) {
                OrderDetailScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

/**
 * Standard bottom-navigation behaviour: one entry per tab, state preserved,
 * and back always returning to the start destination.
 */
private fun NavHostController.navigateToTab(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
