package com.buyanumber.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.AddShoppingCart
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.ui.graphics.vector.ImageVector

object Routes {
    const val SIGN_IN = "sign-in"
    const val DASHBOARD = "dashboard"
    const val BUY = "buy"
    const val ORDERS = "orders"
    const val ACCOUNT = "account"
    const val PAYMENTS = "payments"

    const val ORDER_ID_ARG = "orderId"
    const val ORDER_DETAIL = "order/{$ORDER_ID_ARG}"

    fun orderDetail(orderId: Long) = "order/$orderId"
}

/** The four top-level tabs. */
enum class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    DASHBOARD(Routes.DASHBOARD, "Home", Icons.Outlined.Dashboard),
    BUY(Routes.BUY, "Buy", Icons.Outlined.AddShoppingCart),
    ORDERS(Routes.ORDERS, "Numbers", Icons.Outlined.Sms),
    ACCOUNT(Routes.ACCOUNT, "Account", Icons.Outlined.AccountCircle),
}
