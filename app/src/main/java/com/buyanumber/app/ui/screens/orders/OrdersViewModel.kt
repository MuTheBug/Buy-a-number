package com.buyanumber.app.ui.screens.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buyanumber.app.core.toFiveSimError
import com.buyanumber.app.data.repository.AccountRepository
import com.buyanumber.app.data.repository.OrderRepository
import com.buyanumber.app.domain.model.NumberOrder
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OrdersUiState(
    val active: List<NumberOrder> = emptyList(),
    val history: List<NumberOrder> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val canLoadMore: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class OrdersViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val orderRepository: OrderRepository,
) : ViewModel() {

    private data class HistoryState(
        val orders: List<NumberOrder> = emptyList(),
        val total: Int = 0,
        val isLoading: Boolean = true,
        val isRefreshing: Boolean = false,
        val isLoadingMore: Boolean = false,
        val error: String? = null,
    )

    private val historyState = MutableStateFlow(HistoryState())

    val uiState: StateFlow<OrdersUiState> =
        combine(historyState, orderRepository.activeOrders) { history, active ->
            OrdersUiState(
                active = active,
                // Live orders get their own section, so keep them out of history.
                history = history.orders.filterNot { order -> active.any { it.id == order.id } },
                isLoading = history.isLoading,
                isRefreshing = history.isRefreshing,
                isLoadingMore = history.isLoadingMore,
                canLoadMore = history.orders.size < history.total,
                error = history.error,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = OrdersUiState(),
        )

    init {
        refresh()
    }

    fun refresh() {
        historyState.update {
            it.copy(isLoading = it.orders.isEmpty(), isRefreshing = it.orders.isNotEmpty(), error = null)
        }
        viewModelScope.launch {
            orderRepository.refreshActive()
            accountRepository.orders(limit = PAGE_SIZE, offset = 0)
                .onSuccess { page ->
                    historyState.update {
                        it.copy(
                            orders = page.orders,
                            total = page.total,
                            isLoading = false,
                            isRefreshing = false,
                        )
                    }
                }
                .onFailure { throwable ->
                    historyState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            error = throwable.toFiveSimError().userMessage,
                        )
                    }
                }
        }
    }

    fun loadMore() {
        val current = historyState.value
        if (current.isLoadingMore || current.orders.size >= current.total) return

        historyState.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch {
            accountRepository.orders(limit = PAGE_SIZE, offset = current.orders.size)
                .onSuccess { page ->
                    historyState.update { state ->
                        // De-duplicate: a number bought between two page loads
                        // shifts the server-side window.
                        val known = state.orders.mapTo(mutableSetOf()) { it.id }
                        val fresh = page.orders.filter { it.id !in known }
                        state.copy(
                            orders = state.orders + fresh,
                            total = page.total,
                            isLoadingMore = false,
                        )
                    }
                }
                .onFailure { throwable ->
                    historyState.update {
                        it.copy(isLoadingMore = false, error = throwable.toFiveSimError().userMessage)
                    }
                }
        }
    }

    private companion object {
        const val PAGE_SIZE = 25
    }
}
