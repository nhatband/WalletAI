package com.wallet.manager.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.wallet.manager.data.prefs.SettingsDataStore
import com.wallet.manager.data.remote.supabase.SupabaseConfig
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val isRegister: Boolean = false,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null
)

class AuthViewModel(
    application: Application,
    private val settings: SettingsDataStore
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun onEmailChange(value: String) {
        _uiState.update { it.copy(email = value, errorMessage = null) }
    }

    fun onPasswordChange(value: String) {
        _uiState.update { it.copy(password = value, errorMessage = null) }
    }

    fun onConfirmPasswordChange(value: String) {
        _uiState.update { it.copy(confirmPassword = value, errorMessage = null) }
    }

    fun setMode(isRegister: Boolean) {
        _uiState.update { it.copy(isRegister = isRegister, errorMessage = null) }
    }

    fun submit() {
        val state = _uiState.value
        if (state.email.isBlank() || state.password.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please fill in email and password.") }
            return
        }
        if (state.isRegister && state.password != state.confirmPassword) {
            _uiState.update { it.copy(errorMessage = "Password confirmation does not match.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            runCatching {
                if (state.isRegister) {
                    SupabaseConfig.client.auth.signUpWith(Email) {
                        email = state.email.trim()
                        password = state.password
                    }
                } else {
                    SupabaseConfig.client.auth.signInWith(Email) {
                        email = state.email.trim()
                        password = state.password
                    }
                }
                settings.setSignedIn(state.email.trim())
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        errorMessage = throwable.message ?: "Authentication failed."
                    )
                }
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        password = "",
                        confirmPassword = "",
                        errorMessage = null
                    )
                }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as? Application
                    ?: throw IllegalArgumentException("Application context missing")
                AuthViewModel(application, SettingsDataStore(application))
            }
        }
    }
}
