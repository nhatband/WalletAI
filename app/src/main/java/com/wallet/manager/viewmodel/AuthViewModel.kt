package com.wallet.manager.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.wallet.manager.data.prefs.SettingsDataStore
import com.wallet.manager.data.remote.supabase.SupabaseConfig
import com.wallet.manager.data.remote.supabase.SupabaseRestoreManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
            _uiState.update { it.copy(errorMessage = "Vui lòng nhập email và mật khẩu.") }
            return
        }
        if (state.isRegister && state.password != state.confirmPassword) {
            _uiState.update { it.copy(errorMessage = "Mật khẩu xác nhận không khớp.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            runCatching {
                val trimmedEmail = state.email.trim()
                val previousEmail = settings.signedInEmailFlow.first()

                if (state.isRegister) {
                    SupabaseConfig.client.auth.signUpWith(Email) {
                        email = trimmedEmail
                        password = state.password
                    }
                } else {
                    SupabaseConfig.client.auth.signInWith(Email) {
                        email = trimmedEmail
                        password = state.password
                    }
                }

                if (SupabaseConfig.client.auth.currentSessionOrNull() == null) {
                    throw IllegalStateException(
                        "Phiên đăng nhập chưa được tạo. Có thể Supabase đang yêu cầu xác nhận email trước khi đăng nhập."
                    )
                }

                if (previousEmail != null && previousEmail != trimmedEmail) {
                    SupabaseRestoreManager.clearLocalData(getApplication())
                }

                settings.resetCloudRestoreState()
                settings.setSignedIn(trimmedEmail)
                runCatching { SupabaseRestoreManager.refreshForSignedInUser(getApplication()) }
            }.onFailure { throwable ->
                val friendlyMessage = when {
                    throwable.message?.contains("Anonymous sign-ins are disabled", ignoreCase = true) == true ->
                        "Supabase đang hiểu request đăng ký sai kiểu xác thực. Hãy thử lại sau khi build bản mới."
                    throwable.message?.contains("Phiên đăng nhập chưa được tạo", ignoreCase = true) == true ->
                        "Tài khoản có thể đã tạo nhưng chưa có session đăng nhập. Hãy kiểm tra cấu hình xác nhận email trong Supabase Auth."
                    throwable.message?.contains("User already registered", ignoreCase = true) == true ->
                        "Email này đã được đăng ký."
                    throwable.message?.contains("Invalid login credentials", ignoreCase = true) == true ->
                        "Email hoặc mật khẩu không đúng."
                    else -> throwable.message ?: "Xác thực thất bại."
                }
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        errorMessage = friendlyMessage
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
