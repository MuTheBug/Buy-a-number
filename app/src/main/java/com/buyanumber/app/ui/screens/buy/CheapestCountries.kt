package com.buyanumber.app.ui.screens.buy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.buyanumber.app.core.formatMoney
import com.buyanumber.app.core.formatRate
import com.buyanumber.app.core.toDisplayName
import com.buyanumber.app.domain.model.CountryOffer
import com.buyanumber.app.domain.model.OfferSort
import com.buyanumber.app.ui.components.EmptyState
import com.buyanumber.app.ui.components.SortMenu

/**
 * The service-first half of the buy screen: name a service and every country is
 * ranked by what its cheapest in-stock operator charges.
 *
 * Written as [LazyListScope] extensions so the ranking shares one scrolling
 * list with the rest of the screen rather than nesting scrollers.
 */
@OptIn(ExperimentalMaterial3Api::class)
fun LazyListScope.cheapestCountriesSection(
    state: BuyUiState,
    onPickService: () -> Unit,
    onSelectCountry: (CountryOffer) -> Unit,
    onSortChange: (OfferSort) -> Unit,
) {
    item {
        ServiceCard(
            service = state.cheapestService,
            countryCount = state.countryOffers.size,
            onClick = onPickService,
        )
    }

    item {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "Countries", style = MaterialTheme.typography.titleMedium)
            SortMenu(
                selected = state.offerSort,
                options = OfferSort.entries,
                label = OfferSort::label,
                onSelect = onSortChange,
            )
        }
    }

    when {
        state.isLoadingCountryOffers -> item {
            Box(
                Modifier.fillMaxWidth().padding(24.dp),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
        }

        state.countryOffers.isEmpty() -> item {
            EmptyState(
                title = "Nobody is selling ${state.cheapestService.toDisplayName()}",
                body = "No country has this service in stock right now. Try another service.",
            )
        }

        else -> itemsIndexed(state.countryOffers) { index, offer ->
            CountryOfferRow(
                rank = index + 1,
                offer = offer,
                isBuying = state.isBuying,
                onClick = { onSelectCountry(offer) },
            )
        }
    }
}

/** `items` with an index, kept local so the section reads top to bottom. */
private fun <T> LazyListScope.itemsIndexed(
    items: List<T>,
    itemContent: @Composable (Int, T) -> Unit,
) = items(count = items.size) { index -> itemContent(index, items[index]) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServiceCard(service: String, countryCount: Int, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Service",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = service.toDisplayName(),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (countryCount > 0) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "in stock in $countryCount ${if (countryCount == 1) "country" else "countries"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CountryOfferRow(
    rank: Int,
    offer: CountryOffer,
    isBuying: Boolean,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        enabled = !isBuying,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RankBadge(rank)
            Spacer(Modifier.width(12.dp))
            Text(text = offer.country.flag, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = offer.country.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = listOfNotNull(
                        offer.bestOperator.toDisplayName(),
                        "${offer.available} left",
                        formatRate(offer.successRate)?.let { "$it success" },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.width(8.dp))
            Text(
                text = formatMoney(offer.price),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** The cheapest three are worth calling out; the rest are just numbered. */
@Composable
private fun RankBadge(rank: Int) {
    val highlighted = rank <= 3
    Surface(
        shape = RoundedCornerShape(50),
        color = if (highlighted) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        modifier = Modifier.size(28.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = "$rank",
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                color = if (highlighted) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}
