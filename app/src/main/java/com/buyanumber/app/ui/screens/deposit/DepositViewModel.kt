package com.buyanumber.app.ui.screens.deposit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buyanumber.app.core.toFiveSimError
import com.buyanumber.app.data.repository.AccountRepository
import com.buyanumber.app.domain.model.Payment
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DepositUiState(
    val balance: Double = 0.0,
    val recentPayments: List<Payment> = emptyList(),
    val isLoading: Boolean = true,
    /** True while watching for a top-up to land after returning from 5sim. */
    val isAwaitingPayment: Boolean = false,
    /** Set once a balance increase is detected, so the screen can celebrate it. */
    val creditedAmount: Double? = null,
    val error: String? = null,
)

/**
 * Drives the top-up flow.
 *
 * 5sim's API is read-only where money is concerned — it exposes payment history
 * but no endpoint to create a deposit — so the payment itself happens on their
 * hosted page. What this can do is notice the moment the money lands: the
 * balance is snapshotted before handing off, then polled on return until it
 * changes, which turns an otherwise blind round trip into a confirmation.
 */
@HiltViewModel
class DepositViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DepositUiState())
    val uiState: StateFlow<DepositUiState> = _uiState.asStateFlow()

    private var balanceBeforePayment: Double? = null
    private var pollJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val profile = accountRepository.profile()
            val payments = accountRepository.payments(limit = PAYMENTS_PAGE, offset = 0)
            _uiState.update { current ->
                current.copy(
                    balance = profile.getOrNull()?.balance ?: current.balance,
                    recentPayments = payments.getOrNull()?.payments ?: current.recentPayments,
                    isLoading = false,
                    error = profile.exceptionOrNull()?.toFiveSimError()?.userMessage,
                )
            }
        }
    }

    /** Called as the hosted payment page is handed off to. */
    fun onPaymentPageOpened() {
        balanceBeforePayment = _uiState.value.balance
        _uiState.update { it.copy(creditedAmount = null) }
    }

    /**
     * Called when the screen comes back to the foreground. Payment providers
     * confirm asynchronously, so the balance is polled for a while rather than
     * read once — but only after a hand-off, so an ordinary return to the
     * screen does not start a pointless poll.
     */
    fun onReturnedFromPayment() {
        val before = balanceBeforePayment ?: return
        if (pollJob?.isActive == true) return

        _uiState.update { it.copy(isAwaitingPayment = true, error = null) }
        pollJob = viewModelScope.launch {
            repeat(POLL_ATTEMPTS) {
                val profile = accountRepository.profile().getOrNull()
                val payments = accountRepository.payments(limit = PAYMENTS_PAGE, offset = 0).getOrNull()

                if (profile != null) {
                    val credited = profile.balance - before
                    _uiState.update { current ->
                        current.copy(
                            balance = profile.balance,
                            recentPayments = payments?.payments ?: current.recentPayments,
                        )
                    }
                    if (credited > CREDIT_EPSILON) {
                        balanceBeforePayment = null
                        _uiState.update {
                            it.copy(isAwaitingPayment = false, creditedAmount = credited)
                        }
                        return@launch
                    }
                }
                delay(POLL_INTERVAL_MILLIS)
            }
            // Nothing arrived in the window. That is not an error — bank
            // transfers and some crypto rails simply take longer.
            _uiState.update { it.copy(isAwaitingPayment = false) }
        }
    }

    fun stopWaiting() {
        pollJob?.cancel()
        _uiState.update { it.copy(isAwaitingPayment = false) }
    }

    fun consumeCredited() = _uiState.update { it.copy(creditedAmount = null) }

    private companion object {
        const val PAYMENTS_PAGE = 10
        const val POLL_ATTEMPTS = 18
        const val POLL_INTERVAL_MILLIS = 5_000L

        /** Ignore sub-kopek float noise when comparing balances. */
        const val CREDIT_EPSILON = 0.001
    }
}
