package com.buyanumber.app.ui.screens.buy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buyanumber.app.core.toFiveSimError
import com.buyanumber.app.data.local.SettingsStore
import com.buyanumber.app.data.repository.CatalogRepository
import com.buyanumber.app.data.repository.OrderRepository
import com.buyanumber.app.domain.model.CountryInfo
import com.buyanumber.app.domain.model.Offer
import com.buyanumber.app.domain.model.OfferSort
import com.buyanumber.app.domain.model.ServiceSort
import com.buyanumber.app.domain.model.ServiceSummary
import com.buyanumber.app.domain.model.sortedBy
import com.buyanumber.app.work.OrderTracker
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which picker sheet the buy screen currently has open. */
enum class BuyStep { COUNTRY, SERVICE, OPERATOR }

data class BuyUiState(
    val countries: List<CountryInfo> = emptyList(),
    val services: List<ServiceSummary> = emptyList(),
    val offers: List<Offer> = emptyList(),
    val offerSort: OfferSort = OfferSort.DEFAULT,
    val serviceSort: ServiceSort = ServiceSort.DEFAULT,
    val selectedCountry: CountryInfo? = null,
    val selectedService: ServiceSummary? = null,
    val isLoadingCountries: Boolean = true,
    val isLoadingServices: Boolean = false,
    val isLoadingOffers: Boolean = false,
    val isBuying: Boolean = false,
    val error: String? = null,
    /** Set once a purchase succeeds so the screen can open the new order. */
    val purchasedOrderId: Long? = null,
)

@HiltViewModel
class BuyViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository,
    private val orderRepository: OrderRepository,
    private val settingsStore: SettingsStore,
    private val orderTracker: OrderTracker,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BuyUiState())

    /**
     * Sorting is applied when the state is read rather than when the lists are
     * fetched, so switching the order is instant and never hits the network.
     */
    val uiState: StateFlow<BuyUiState> =
        combine(_uiState, settingsStore.settings) { state, settings ->
            state.copy(
                services = state.services.sortedBy(settings.serviceSort),
                offers = state.offers.sortedBy(settings.offerSort),
                offerSort = settings.offerSort,
                serviceSort = settings.serviceSort,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = BuyUiState(),
        )

    init {
        loadCountries()
    }

    private fun loadCountries() {
        _uiState.update { it.copy(isLoadingCountries = true, error = null) }
        viewModelScope.launch {
            catalogRepository.countries()
                .onSuccess { countries ->
                    // Pre-select the user's default country so the common case
                    // is two taps: pick a service, buy.
                    val preferred = settingsStore.settings.first().defaultCountry
                    val selected = countries.firstOrNull { it.code == preferred }
                    _uiState.update {
                        it.copy(countries = countries, isLoadingCountries = false)
                    }
                    selected?.let { selectCountry(it) }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isLoadingCountries = false,
                            error = throwable.toFiveSimError().userMessage,
                        )
                    }
                }
        }
    }

    fun selectCountry(country: CountryInfo) {
        _uiState.update {
            it.copy(
                selectedCountry = country,
                // The previous service belongs to the previous country.
                selectedService = null,
                services = emptyList(),
                offers = emptyList(),
                isLoadingServices = true,
                error = null,
            )
        }
        viewModelScope.launch {
            catalogRepository.services(country.code)
                .onSuccess { services ->
                    _uiState.update { it.copy(services = services, isLoadingServices = false) }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(isLoadingServices = false, error = throwable.toFiveSimError().userMessage)
                    }
                }
        }
    }

    fun selectService(service: ServiceSummary) {
        val country = _uiState.value.selectedCountry ?: return
        _uiState.update {
            it.copy(selectedService = service, offers = emptyList(), isLoadingOffers = true, error = null)
        }
        viewModelScope.launch {
            catalogRepository.offers(country.code, service.product)
                .onSuccess { offers ->
                    _uiState.update { it.copy(offers = offers, isLoadingOffers = false) }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(isLoadingOffers = false, error = throwable.toFiveSimError().userMessage)
                    }
                }
        }
    }

    /**
     * Buys a number. [offer] is null when the user takes the "cheapest
     * available" shortcut, which asks 5sim for `any` operator.
     */
    fun buy(offer: Offer?) {
        val state = _uiState.value
        val country = state.selectedCountry ?: return
        val service = state.selectedService ?: return
        if (state.isBuying) return

        _uiState.update { it.copy(isBuying = true, error = null) }
        viewModelScope.launch {
            orderRepository.buy(
                country = country.code,
                operator = offer?.operator ?: "any",
                product = service.product,
                // Guard against a price change between listing and purchase.
                maxPrice = offer?.price,
            )
                .onSuccess { order ->
                    orderTracker.scheduleNextPoll()
                    _uiState.update { it.copy(isBuying = false, purchasedOrderId = order.id) }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(isBuying = false, error = throwable.toFiveSimError().userMessage)
                    }
                }
        }
    }

    fun setOfferSort(sort: OfferSort) = viewModelScope.launch { settingsStore.setOfferSort(sort) }

    fun setServiceSort(sort: ServiceSort) = viewModelScope.launch {
        settingsStore.setServiceSort(sort)
    }

    /** Called once the screen has navigated to the new order. */
    fun onPurchaseHandled() = _uiState.update { it.copy(purchasedOrderId = null) }

    fun dismissError() = _uiState.update { it.copy(error = null) }

    fun retry() = loadCountries()
}
