package com.buyanumber.app.ui.screens.account

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import com.buyanumber.app.core.formatDateTime
import com.buyanumber.app.core.formatMoney
import com.buyanumber.app.domain.model.CountryInfo
import com.buyanumber.app.domain.model.Payment
import com.buyanumber.app.domain.model.Profile
import com.buyanumber.app.ui.components.ErrorBanner
import com.buyanumber.app.ui.components.LoadingState
import com.buyanumber.app.ui.components.SearchableSheet
import com.buyanumber.app.ui.components.StatBlock

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(viewModel: AccountViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var countryPickerOpen by remember { mutableStateOf(false) }
    var signOutDialogOpen by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Account") })

        if (state.isLoading) {
            LoadingState()
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.error?.let { message -> item { ErrorBanner(message) } }

            item { ProfileCard(state.profile) }

            item { SectionTitle("Preferences") }

            item {
                val selected = state.countries.firstOrNull { it.code == state.settings.defaultCountry }
                ListItem(
                    headlineContent = { Text("Default country") },
                    supportingContent = {
                        Text(selected?.let { "${it.flag}  ${it.name}" } ?: "Ask every time")
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.clickable { countryPickerOpen = true },
                )
            }

            item {
                ListItem(
                    headlineContent = { Text("Notify me when an SMS arrives") },
                    supportingContent = { Text("Checks your open numbers in the background") },
                    trailingContent = {
                        Switch(
                            checked = state.settings.notifyOnSms,
                            onCheckedChange = viewModel::setNotifyOnSms,
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                )
            }

            item { SectionTitle("Balance history") }

            if (state.payments.isEmpty()) {
                item {
                    Text(
                        text = "No payments recorded yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(state.payments, key = Payment::id) { payment -> PaymentRow(payment) }
            }

            item {
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = { signOutDialogOpen = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Disconnect this API key")
                }
            }
        }
    }

    if (countryPickerOpen) {
        SearchableSheet(
            title = "Default country",
            items = state.countries,
            searchText = { "${it.name} ${it.code}" },
            key = CountryInfo::code,
            placeholder = "Search countries",
            onDismiss = { countryPickerOpen = false },
        ) { country ->
            ListItem(
                headlineContent = { Text(country.name) },
                leadingContent = { Text(country.flag, style = MaterialTheme.typography.titleLarge) },
                modifier = Modifier.clickable {
                    viewModel.setDefaultCountry(country)
                    countryPickerOpen = false
                },
            )
        }
    }

    if (signOutDialogOpen) {
        AlertDialog(
            onDismissRequest = { signOutDialogOpen = false },
            title = { Text("Disconnect this key?") },
            text = {
                Text(
                    "The key is removed from this device and your cached numbers are cleared. " +
                        "Your 5sim account and its orders are not affected.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        signOutDialogOpen = false
                        viewModel.signOut()
                    },
                ) { Text("Disconnect") }
            },
            dismissButton = {
                TextButton(onClick = { signOutDialogOpen = false }) { Text("Keep") }
            },
        )
    }
}

@Composable
private fun ProfileCard(profile: Profile?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                text = profile?.email?.takeIf { it.isNotBlank() } ?: "5sim account",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            profile?.vendor?.let { vendor ->
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Vendor: $vendor",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                StatBlock("Balance", formatMoney(profile?.balance ?: 0.0))
                StatBlock("Held", formatMoney(profile?.frozenBalance ?: 0.0))
                StatBlock(
                    label = "Rating",
                    value = profile?.rating?.let {
                        String.format(java.util.Locale.US, "%.0f", it)
                    } ?: "—",
                )
            }
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
                // 5sim signs the amount already; the sign is what tells a
                // top-up from a purchase.
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

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 8.dp),
    )
}
