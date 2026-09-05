package com.buyanumber.app.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buyanumber.app.core.toFiveSimError
import com.buyanumber.app.data.repository.AccountRepository
import com.buyanumber.app.data.repository.OrderRepository
import com.buyanumber.app.domain.model.NumberOrder
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

data class DashboardUiState(
    val profile: Profile? = null,
    val activeOrders: List<NumberOrder> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val orderRepository: OrderRepository,
    private val orderTracker: OrderTracker,
) : ViewModel() {

    private data class LoadState(
        val profile: Profile? = null,
        val isLoading: Boolean = true,
        val isRefreshing: Boolean = false,
        val error: String? = null,
    )

    private val loadState = MutableStateFlow(LoadState())

    val uiState: StateFlow<DashboardUiState> =
        combine(loadState, orderRepository.activeOrders) { load, active ->
            DashboardUiState(
                profile = load.profile,
                activeOrders = active,
                isLoading = load.isLoading,
                isRefreshing = load.isRefreshing,
                error = load.error,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DashboardUiState(),
        )

    init {
        refresh(initial = true)
    }

    fun refresh(initial: Boolean = false) {
        loadState.update {
            it.copy(isLoading = initial && it.profile == null, isRefreshing = !initial, error = null)
        }
        viewModelScope.launch {
            val profileResult = accountRepository.profile()

            // Pull the server's order list first so numbers bought elsewhere
            // appear, then poll the open ones for new messages.
            orderRepository.syncFromHistory()
            val stillOpen = orderRepository.refreshActive().any { it.status.isActive }
            if (stillOpen) orderTracker.scheduleNextPoll()

            loadState.update { current ->
                current.copy(
                    profile = profileResult.getOrNull() ?: current.profile,
                    isLoading = false,
                    isRefreshing = false,
                    error = profileResult.exceptionOrNull()?.toFiveSimError()?.userMessage,
                )
            }
        }
    }
}
