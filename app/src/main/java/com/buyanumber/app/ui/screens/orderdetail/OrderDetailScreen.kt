package com.buyanumber.app.ui.screens.orderdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.buyanumber.app.core.formatCountdown
import com.buyanumber.app.core.formatMoney
import com.buyanumber.app.core.formatRelative
import com.buyanumber.app.core.toDisplayName
import com.buyanumber.app.domain.model.NumberOrder
import com.buyanumber.app.domain.model.SmsMessage
import com.buyanumber.app.ui.components.EmptyState
import com.buyanumber.app.ui.components.ErrorBanner
import com.buyanumber.app.ui.components.LoadingState
import com.buyanumber.app.ui.components.StatusChip
import com.buyanumber.app.ui.components.rememberNow
import com.buyanumber.app.ui.theme.MonoNumberStyle

/**
 * The live view of one number: the code as it arrives, a countdown to expiry,
 * the full SMS thread, and the actions that close the order out.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderDetailScreen(
    onBack: () -> Unit,
    viewModel: OrderDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.order?.product?.toDisplayName() ?: "Number") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val order = state.order
        when {
            state.isLoading && order == null -> LoadingState(Modifier.padding(padding))
            order == null -> ErrorBanner(
                message = state.error ?: "This order could not be loaded.",
                modifier = Modifier.padding(padding).padding(16.dp),
            )
            else -> OrderDetailContent(
                order = order,
                state = state,
                contentPadding = padding,
                onCopy = { label, value ->
                    clipboard.setText(AnnotatedString(value))
                    scope.launch {
                        snackbarHostState.currentSnackbarData?.dismiss()
                        snackbarHostState.showSnackbar(label)
                    }
                },
                onFinish = viewModel::finish,
                onCancel = viewModel::cancel,
                onBan = viewModel::ban,
            )
        }
    }
}

@Composable
private fun OrderDetailContent(
    order: NumberOrder,
    state: OrderDetailUiState,
    contentPadding: PaddingValues,
    onCopy: (String, String) -> Unit,
    onFinish: () -> Unit,
    onCancel: () -> Unit,
    onBan: () -> Unit,
) {
    val now by rememberNow()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 16.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
            start = 16.dp,
            end = 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        state.error?.let { message -> item { ErrorBanner(message) } }

        item { NumberCard(order = order, secondsLeft = order.secondsRemaining(now), onCopy = onCopy) }

        order.code?.let { code ->
            item { CodeCard(code = code, onCopy = onCopy) }
        }

        item {
            ActionRow(
                order = order,
                isWorking = state.isWorking,
                onFinish = onFinish,
                onCancel = onCancel,
                onBan = onBan,
            )
        }

        item {
            Text(
                text = "Messages",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        if (order.sms.isEmpty()) {
            item {
                EmptyState(
                    title = if (order.status.isActive) "Waiting for the SMS" else "No messages arrived",
                    body = if (order.status.isActive) {
                        "This screen refreshes every few seconds, and you will get a notification " +
                            "even if you leave the app."
                    } else {
                        null
                    },
                )
            }
        } else {
            items(order.sms) { sms -> SmsCard(sms = sms, onCopy = onCopy) }
        }
    }
}

@Composable
private fun NumberCard(
    order: NumberOrder,
    secondsLeft: Long,
    onCopy: (String, String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusChip(order.status)
                if (order.status.isActive) {
                    Text(
                        text = "Expires in ${formatCountdown(secondsLeft)}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = order.phone,
                    style = MonoNumberStyle,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onCopy("Number copied", order.phone) }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy number")
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                text = listOfNotNull(
                    order.country?.toDisplayName(),
                    order.operator?.toDisplayName(),
                    formatMoney(order.price),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CodeCard(code: String, onCopy: (String, String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(20.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Verification code", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                Text(text = code, style = MonoNumberStyle)
            }
            Button(onClick = { onCopy("Code copied", code) }) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Copy")
            }
        }
    }
}

@Composable
private fun ActionRow(
    order: NumberOrder,
    isWorking: Boolean,
    onFinish: () -> Unit,
    onCancel: () -> Unit,
    onBan: () -> Unit,
) {
    if (!order.status.isActive) return

    Column {
        if (isWorking) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Finishing is only meaningful once a code has actually arrived.
            Button(
                onClick = onFinish,
                enabled = !isWorking && order.sms.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Finish")
            }
            OutlinedButton(
                onClick = onCancel,
                enabled = !isWorking && order.sms.isEmpty(),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.Undo, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Cancel")
            }
        }

        TextButton(onClick = onBan, enabled = !isWorking) {
            Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Report this number as unusable")
        }
    }
}

@Composable
private fun SmsCard(sms: SmsMessage, onCopy: (String, String) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = sms.sender.ifBlank { "Unknown sender" },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = formatRelative(sms.receivedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(text = sms.text, style = MaterialTheme.typography.bodyMedium)

            sms.resolvedCode?.let { code ->
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = code,
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { onCopy("Code copied", code) }) { Text("Copy code") }
                }
            }
        }
    }
}
