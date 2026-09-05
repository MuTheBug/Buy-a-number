package com.buyanumber.app.core

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent

/**
 * 5sim has no deposit endpoint — the API exposes payment *history* only — so
 * topping up has to happen on their hosted payment page, which is also the
 * right place for it: card details never touch this app.
 *
 * The page opens in a Custom Tab, which keeps the app's task and colours and
 * returns the user straight back when they are done.
 */
object PaymentPage {

    /** 5sim's top-up page, listing every method available to the account. */
    const val URL = "https://5sim.net/payment"

    fun open(context: Context, url: String = URL): Boolean {
        val uri = Uri.parse(url)
        return try {
            CustomTabsIntent.Builder()
                .setShowTitle(true)
                .setUrlBarHidingEnabled(false)
                .build()
                .launchUrl(context, uri)
            true
        } catch (_: ActivityNotFoundException) {
            // No Custom Tabs provider: fall back to whatever browser exists.
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                true
            } catch (_: ActivityNotFoundException) {
                false
            }
        }
    }
}
