package com.buyanumber.app.ui.screens.orderdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buyanumber.app.core.toFiveSimError
import com.buyanumber.app.data.repository.OrderRepository
import com.buyanumber.app.domain.model.NumberOrder
import com.buyanumber.app.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class OrderDetailUiState(
    val order: NumberOrder? = null,
    val isLoading: Boolean = true,
    val isWorking: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    /** Set when the order is closed, so the screen can pop back. */
    val isClosed: Boolean = false,
)

@HiltViewModel
class OrderDetailViewModel @Inject constructor(
    private val orderRepository: OrderRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val orderId: Long = checkNotNull(savedStateHandle[Routes.ORDER_ID_ARG]) {
        "OrderDetail requires an ${Routes.ORDER_ID_ARG} argument"
    }

    private val _uiState = MutableStateFlow(OrderDetailUiState())
    val uiState: StateFlow<OrderDetailUiState> = _uiState.asStateFlow()

    private var pollJob: Job? = null

    init {
        refresh()
        startPolling()
    }

    /**
     * Polls while the screen is open and the order is still live. 5sim allows
     * 100 requests a second per key, so a five-second cadence is comfortably
     * within budget while feeling immediate.
     */
    private fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (isActive) {
                delay(POLL_INTERVAL_MILLIS)
                val order = _uiState.value.order
                // Stop once the number can no longer receive anything.
                if (order != null && !order.status.isActive) break
                loadOrder(showSpinner = false)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch { loadOrder(showSpinner = _uiState.value.order == null) }
    }

    private suspend fun loadOrder(showSpinner: Boolean) {
        if (showSpinner) _uiState.update { it.copy(isLoading = true) }
        orderRepository.check(orderId)
            .onSuccess { order ->
                _uiState.update { it.copy(order = order, isLoading = false, error = null) }
            }
            .onFailure { throwable ->
                _uiState.update {
                    it.copy(isLoading = false, error = throwable.toFiveSimError().userMessage)
                }
            }
    }

    fun finish() = applyAction(OrderRepository.Action.FINISH, "Order finished.")

    fun cancel() = applyAction(OrderRepository.Action.CANCEL, "Order cancelled and refunded.")

    fun ban() = applyAction(OrderRepository.Action.BAN, "Number reported and banned.")

    private fun applyAction(action: OrderRepository.Action, successMessage: String) {
        if (_uiState.value.isWorking) return
        _uiState.update { it.copy(isWorking = true, error = null, message = null) }
        viewModelScope.launch {
            orderRepository.applyAction(orderId, action)
                .onSuccess { order ->
                    pollJob?.cancel()
                    _uiState.update {
                        it.copy(
                            order = order,
                            isWorking = false,
                            message = successMessage,
                            isClosed = !order.status.isActive,
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(isWorking = false, error = throwable.toFiveSimError().userMessage)
                    }
                }
        }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }

    fun consumeError() = _uiState.update { it.copy(error = null) }

    private companion object {
        const val POLL_INTERVAL_MILLIS = 5_000L
    }
}
