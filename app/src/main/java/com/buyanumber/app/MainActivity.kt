package com.buyanumber.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.buyanumber.app.ui.navigation.BuyANumberApp
import com.buyanumber.app.ui.theme.BuyANumberTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** Set when the activity is opened by tapping an incoming-SMS notification. */
    private var pendingOrderId by mutableStateOf<Long?>(null)

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* optional */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingOrderId = intent.orderId()
        requestNotificationPermissionIfNeeded()

        setContent {
            BuyANumberTheme {
                BuyANumberApp(
                    pendingOrderId = pendingOrderId,
                    onPendingOrderHandled = { pendingOrderId = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // launchMode is singleTask, so a notification tap while the app is open
        // arrives here rather than through onCreate.
        setIntent(intent)
        intent.orderId()?.let { pendingOrderId = it }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun Intent.orderId(): Long? =
        getLongExtra(EXTRA_ORDER_ID, -1L).takeIf { it > 0 }

    companion object {
        const val EXTRA_ORDER_ID = "extra_order_id"
    }
}
