package com.example.momento.feature.profile.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.momento.R
import com.example.momento.feature.profile.domain.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUiState(
    val isRefreshing: Boolean = false,
    val isSigningOut: Boolean = false,
    val isSignedOut: Boolean = false,
    val error: Int? = null,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val repository: ProfileRepository,
) : ViewModel() {
    val currentUser = repository.currentUser
    private val _state = MutableStateFlow(ProfileUiState())
    val state = _state.asStateFlow()
    private var refreshJob: Job? = null

    init {
        refreshProfile()
    }

    fun refreshProfile() {
        if (_state.value.isRefreshing || _state.value.isSigningOut || _state.value.isSignedOut) return
        _state.update { it.copy(isRefreshing = true, error = null) }
        refreshJob = viewModelScope.launch {
            val result = repository.refreshProfile()
            _state.update {
                it.copy(
                    isRefreshing = false,
                    error = if (result.isFailure) R.string.profile_load_error else null,
                )
            }
        }
    }

    fun signOut() {
        if (_state.value.isSigningOut || _state.value.isSignedOut) return
        refreshJob?.cancel()
        _state.update { it.copy(isRefreshing = false, isSigningOut = true, error = null) }
        viewModelScope.launch {
            val result = repository.signOut()
            // Auth clears the local session even when the backend logout fails.
            val signedOut = result.isSuccess || repository.currentUser.value == null
            _state.update {
                it.copy(
                    isSigningOut = false,
                    isSignedOut = signedOut,
                    error = if (signedOut) null else R.string.profile_logout_error,
                )
            }
        }
    }
}
