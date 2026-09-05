package com.buyanumber.app.ui.screens.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.buyanumber.app.core.formatMoney
import com.buyanumber.app.domain.model.NumberOrder
import com.buyanumber.app.domain.model.Profile
import com.buyanumber.app.ui.components.EmptyState
import com.buyanumber.app.ui.components.ErrorBanner
import com.buyanumber.app.ui.components.LoadingState
import com.buyanumber.app.ui.components.OrderCard
import com.buyanumber.app.ui.components.StatBlock

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onOpenOrder: (Long) -> Unit,
    onBuyNumber: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Buy a Number") })

        if (state.isLoading) {
            LoadingState()
            return@Column
        }

        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                state.error?.let { message ->
                    item { ErrorBanner(message) }
                }

                item {
                    BalanceCard(profile = state.profile, onBuyNumber = onBuyNumber)
                }

                item {
                    Text(
                        text = "Active numbers",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                if (state.activeOrders.isEmpty()) {
                    item {
                        EmptyState(
                            title = "No active numbers",
                            body = "Buy a number and its verification code will show up here.",
                        )
                    }
                } else {
                    items(state.activeOrders, key = NumberOrder::id) { order ->
                        OrderCard(order = order, onClick = { onOpenOrder(order.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun BalanceCard(profile: Profile?, onBuyNumber: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                text = "Balance",
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = formatMoney(profile?.balance ?: 0.0),
                style = MaterialTheme.typography.displaySmall,
            )
            profile?.email?.takeIf { it.isNotBlank() }?.let { email ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = email,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                StatBlock(
                    label = "Held",
                    value = formatMoney(profile?.frozenBalance ?: 0.0),
                    valueColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                StatBlock(
                    label = "Rating",
                    value = profile?.rating?.let { String.format(java.util.Locale.US, "%.0f", it) } ?: "—",
                    valueColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }

            Spacer(Modifier.height(16.dp))
            FilledTonalButton(
                onClick = onBuyNumber,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Buy a number")
            }
        }
    }
}
