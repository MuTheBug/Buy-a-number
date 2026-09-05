package com.buyanumber.app.ui.screens.signin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buyanumber.app.core.toFiveSimError
import com.buyanumber.app.data.repository.AccountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SignInUiState(
    val apiKey: String = "",
    val isSubmitting: Boolean = false,
    val error: String? = null,
) {
    val canSubmit: Boolean get() = apiKey.isNotBlank() && !isSubmitting
}

@HiltViewModel
class SignInViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SignInUiState())
    val uiState: StateFlow<SignInUiState> = _uiState.asStateFlow()

    fun onApiKeyChange(value: String) {
        // Keys are usually pasted, and a stray newline from the clipboard
        // would otherwise be sent in the Authorization header.
        _uiState.update { it.copy(apiKey = value.trim(), error = null) }
    }

    fun signIn() {
        val key = _uiState.value.apiKey
        if (key.isBlank() || _uiState.value.isSubmitting) return

        _uiState.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            accountRepository.signIn(key)
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(isSubmitting = false, error = throwable.toFiveSimError().userMessage)
                    }
                }
            // On success the app-level sign-in flag flips and navigation
            // swaps this screen out, so no state update is needed here.
        }
    }
}
