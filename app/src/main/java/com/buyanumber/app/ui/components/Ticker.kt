package com.buyanumber.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import java.time.Instant
import kotlinx.coroutines.delay

/**
 * A clock that ticks once a second while it is on screen, so countdowns update
 * without every list item owning its own coroutine.
 */
@Composable
fun rememberNow(): State<Instant> = produceState(initialValue = Instant.now()) {
    while (true) {
        delay(1_000)
        value = Instant.now()
    }
}
