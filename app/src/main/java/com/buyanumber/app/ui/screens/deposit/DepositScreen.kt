package com.buyanumber.app.ui.screens.deposit

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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.buyanumber.app.core.PaymentPage
import com.buyanumber.app.core.formatDateTime
import com.buyanumber.app.core.formatMoney
import com.buyanumber.app.domain.model.Payment
import com.buyanumber.app.ui.components.ErrorBanner
import com.buyanumber.app.ui.components.LoadingState
import com.buyanumber.app.ui.theme.MonoNumberStyle

/**
 * Top-up screen. The payment itself happens on 5sim's hosted page — they have
 * no deposit API and card details should not pass through this app — but the
 * hand-off and the confirmation both happen here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DepositScreen(
    onBack: () -> Unit,
    viewModel: DepositViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Coming back to the foreground is the only signal available that the user
    // finished on 5sim's page; the ViewModel ignores it unless a hand-off
    // actually happened.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onReturnedFromPayment()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add funds") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 16.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
                start = 16.dp,
                end = 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.error?.let { message -> item { ErrorBanner(message) } }

            state.creditedAmount?.let { credited ->
                item { CreditedCard(amount = credited, onDismiss = viewModel::consumeCredited) }
            }

            item { BalanceCard(balance = state.balance) }

            if (state.isAwaitingPayment) {
                item { WaitingCard(onStop = viewModel::stopWaiting) }
            }

            item {
                Button(
                    onClick = {
                        viewModel.onPaymentPageOpened()
                        PaymentPage.open(context)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Top up on 5sim")
                }
            }

            item {
                Text(
                    text = "5sim has no deposit API, so payment happens on their own secure page — " +
                        "card, crypto and wallet methods all live there, and no card details ever " +
                        "reach this app. It opens in a tab over the app; come straight back and your " +
                        "new balance will appear here automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (state.recentPayments.isNotEmpty()) {
                item {
                    Text(
                        text = "Recent payments",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(state.recentPayments, key = Payment::id) { payment -> PaymentRow(payment) }
            }
        }
    }
}

@Composable
private fun BalanceCard(balance: Double) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("Current balance", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            Text(text = formatMoney(balance), style = MonoNumberStyle)
        }
    }
}

@Composable
private fun WaitingCard(onStop: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Watching for your payment", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Cards clear in seconds; bank and some crypto transfers take longer. " +
                        "You can leave this screen — the balance updates either way.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onStop) { Text("Stop") }
        }
    }
}

@Composable
private fun CreditedCard(amount: Double, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.CheckCircle, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Payment received", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "${formatMoney(amount)} added to your balance.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    }
}

@Composable
private fun PaymentRow(payment: Payment) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = payment.type.ifBlank { "Payment" },
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = listOfNotNull(
                        payment.provider.takeIf { it.isNotBlank() },
                        formatDateTime(payment.createdAt),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = formatMoney(payment.amount),
                style = MaterialTheme.typography.titleMedium,
                color = if (payment.amount >= 0) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
        HorizontalDivider()
    }
}
