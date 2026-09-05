package com.buyanumber.app.work

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.buyanumber.app.MainActivity
import com.buyanumber.app.R
import com.buyanumber.app.domain.model.NumberOrder
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Posts a heads-up notification the moment a verification code lands. */
@Singleton
class SmsNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.sms_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.sms_channel_description)
            enableVibration(true)
        }
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    @SuppressLint("MissingPermission") // guarded by hasPermission()
    fun notifySms(order: NumberOrder) {
        if (!hasPermission()) return
        val code = order.code
        val body = order.latestSms?.text.orEmpty()
        val title = if (code != null) {
            "Code $code for ${order.product.replaceFirstChar { it.uppercase() }}"
        } else {
            "SMS for ${order.product.replaceFirstChar { it.uppercase() }}"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body.ifBlank { order.phone })
            .setStyle(NotificationCompat.BigTextStyle().bigText(body.ifBlank { order.phone }))
            .setSubText(order.phone)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(openOrderIntent(order.id))
            .build()

        // The order id keeps one notification per number rather than stacking
        // a new one for every poll.
        NotificationManagerCompat.from(context).notify(order.id.toInt(), notification)
    }

    private fun openOrderIntent(orderId: Long): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_ORDER_ID, orderId)
        }
        return PendingIntent.getActivity(
            context,
            orderId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val CHANNEL_ID = "incoming_sms"
    }
}
