package com.buyanumber.app.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buyanumber.app.data.repository.AccountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Whether the app should show the sign-in screen or the main shell. */
enum class SessionState { LOADING, SIGNED_OUT, SIGNED_IN }

@HiltViewModel
class AppViewModel @Inject constructor(
    accountRepository: AccountRepository,
) : ViewModel() {

    val sessionState: StateFlow<SessionState> = accountRepository.isSignedIn
        .map { if (it) SessionState.SIGNED_IN else SessionState.SIGNED_OUT }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            // Until the encrypted key has been read, neither screen is correct.
            initialValue = SessionState.LOADING,
        )
}
