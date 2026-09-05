package com.buyanumber.app.ui.screens.buy

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.buyanumber.app.core.formatMoney
import com.buyanumber.app.core.formatRate
import com.buyanumber.app.core.toDisplayName
import com.buyanumber.app.domain.model.CountryInfo
import com.buyanumber.app.domain.model.Offer
import com.buyanumber.app.domain.model.ServiceSummary
import com.buyanumber.app.ui.components.EmptyState
import com.buyanumber.app.ui.components.ErrorBanner
import com.buyanumber.app.ui.components.ErrorState
import com.buyanumber.app.ui.components.LoadingState
import com.buyanumber.app.ui.components.SearchableSheet

/**
 * Country → service → operator, then buy. Each step only unlocks once the
 * previous one is answered, which mirrors how 5sim's own pricing is scoped.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuyScreen(
    onOrderPurchased: (Long) -> Unit,
    viewModel: BuyViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var openStep by remember { mutableStateOf<BuyStep?>(null) }

    LaunchedEffect(state.purchasedOrderId) {
        state.purchasedOrderId?.let { orderId ->
            viewModel.onPurchaseHandled()
            onOrderPurchased(orderId)
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Buy a number") })

        when {
            state.isLoadingCountries -> LoadingState()
            state.countries.isEmpty() && state.error != null ->
                ErrorState(message = state.error!!, onRetry = viewModel::retry)
            else -> BuyContent(
                state = state,
                onOpenStep = { openStep = it },
                onBuy = viewModel::buy,
            )
        }
    }

    when (openStep) {
        BuyStep.COUNTRY -> SearchableSheet(
            title = "Choose a country",
            items = state.countries,
            searchText = { "${it.name} ${it.code} ${it.prefix}" },
            key = CountryInfo::code,
            placeholder = "Search countries",
            onDismiss = { openStep = null },
        ) { country ->
            ListItem(
                headlineContent = { Text(country.name) },
                supportingContent = { Text(country.prefix) },
                leadingContent = { Text(country.flag, style = MaterialTheme.typography.titleLarge) },
                modifier = Modifier.clickable {
                    viewModel.selectCountry(country)
                    openStep = BuyStep.SERVICE
                },
            )
        }

        BuyStep.SERVICE -> SearchableSheet(
            title = "Choose a service",
            items = state.services,
            searchText = ServiceSummary::product,
            key = ServiceSummary::product,
            placeholder = "Search services",
            emptyMessage = if (state.isLoadingServices) "Loading services…" else "No services here.",
            onDismiss = { openStep = null },
        ) { service ->
            ListItem(
                headlineContent = { Text(service.product.toDisplayName()) },
                supportingContent = {
                    Text(
                        if (service.available > 0) {
                            "${service.available} available · from ${formatMoney(service.cheapestPrice)}"
                        } else {
                            "Out of stock"
                        },
                    )
                },
                modifier = Modifier.clickable {
                    viewModel.selectService(service)
                    openStep = null
                },
            )
        }

        BuyStep.OPERATOR, null -> Unit
    }
}

@Composable
private fun BuyContent(
    state: BuyUiState,
    onOpenStep: (BuyStep) -> Unit,
    onBuy: (Offer?) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        state.error?.let { message -> item { ErrorBanner(message) } }

        item {
            SelectionCard(
                label = "Country",
                value = state.selectedCountry?.let { "${it.flag}  ${it.name}" } ?: "Choose a country",
                isPlaceholder = state.selectedCountry == null,
                onClick = { onOpenStep(BuyStep.COUNTRY) },
            )
        }

        item {
            SelectionCard(
                label = "Service",
                value = state.selectedService?.product?.toDisplayName()
                    ?: if (state.selectedCountry == null) "Pick a country first" else "Choose a service",
                isPlaceholder = state.selectedService == null,
                enabled = state.selectedCountry != null,
                onClick = { onOpenStep(BuyStep.SERVICE) },
            )
        }

        if (state.selectedService != null) {
            item {
                Text(
                    text = "Operators",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            when {
                state.isLoadingOffers -> item {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                state.offers.isEmpty() -> item {
                    EmptyState(
                        title = "No operators available",
                        body = "Nobody is selling this service in this country right now.",
                    )
                }

                else -> items(state.offers, key = { "${it.operator}-${it.price}" }) { offer ->
                    OfferRow(
                        offer = offer,
                        isBuying = state.isBuying,
                        onBuy = { onBuy(offer) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectionCard(
    label: String,
    value: String,
    isPlaceholder: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isPlaceholder) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null)
        }
    }
}

@Composable
private fun OfferRow(offer: Offer, isBuying: Boolean, onBuy: () -> Unit) {
    Card(
        onClick = onBuy,
        enabled = offer.inStock && !isBuying,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = offer.operator.toDisplayName(),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(4.dp))
                Row {
                    Text(
                        text = if (offer.inStock) "${offer.available} in stock" else "Out of stock",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    formatRate(offer.successRate)?.let { rate ->
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "$rate success",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            if (isBuying) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text(
                    text = formatMoney(offer.price),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
