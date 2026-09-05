package com.buyanumber.app.ui.screens.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buyanumber.app.core.toFiveSimError
import com.buyanumber.app.data.local.AppSettings
import com.buyanumber.app.data.local.SettingsStore
import com.buyanumber.app.data.repository.AccountRepository
import com.buyanumber.app.data.repository.CatalogRepository
import com.buyanumber.app.domain.model.CountryInfo
import com.buyanumber.app.domain.model.Payment
import com.buyanumber.app.domain.model.Profile
import com.buyanumber.app.work.OrderTracker
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AccountUiState(
    val profile: Profile? = null,
    val payments: List<Payment> = emptyList(),
    val countries: List<CountryInfo> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

/**
 * The "follow your account" screen: who you are signed in as, what the balance
 * has been doing, and the preferences that shape the rest of the app.
 */
@HiltViewModel
class AccountViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val catalogRepository: CatalogRepository,
    private val settingsStore: SettingsStore,
    private val orderTracker: OrderTracker,
) : ViewModel() {

    private data class LoadState(
        val profile: Profile? = null,
        val payments: List<Payment> = emptyList(),
        val countries: List<CountryInfo> = emptyList(),
        val isLoading: Boolean = true,
        val error: String? = null,
    )

    private val loadState = MutableStateFlow(LoadState())

    val uiState: StateFlow<AccountUiState> =
        combine(loadState, settingsStore.settings) { load, settings ->
            AccountUiState(
                profile = load.profile,
                payments = load.payments,
                countries = load.countries,
                settings = settings,
                isLoading = load.isLoading,
                error = load.error,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AccountUiState(),
        )

    init {
        refresh()
    }

    fun refresh() {
        loadState.update { it.copy(isLoading = it.profile == null, error = null) }
        viewModelScope.launch {
            val profile = accountRepository.profile()
            val payments = accountRepository.payments(limit = PAYMENTS_PAGE, offset = 0)
            val countries = catalogRepository.countries()

            loadState.update { current ->
                current.copy(
                    profile = profile.getOrNull() ?: current.profile,
                    payments = payments.getOrNull()?.payments ?: current.payments,
                    countries = countries.getOrNull() ?: current.countries,
                    isLoading = false,
                    // Only the profile call failing is worth interrupting for;
                    // payments and the country list degrade quietly.
                    error = profile.exceptionOrNull()?.toFiveSimError()?.userMessage,
                )
            }
        }
    }

    fun setDefaultCountry(country: CountryInfo?) = viewModelScope.launch {
        settingsStore.setDefaultCountry(country?.code)
    }

    fun setNotifyOnSms(enabled: Boolean) = viewModelScope.launch {
        settingsStore.setNotifyOnSms(enabled)
    }

    fun signOut() = viewModelScope.launch {
        // Stop the poller before the key goes away, so no run wakes up
        // authenticated as nobody.
        orderTracker.stop()
        accountRepository.signOut()
    }

    private companion object {
        const val PAYMENTS_PAGE = 20
    }
}
