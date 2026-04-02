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
import com.wallet.manager.util.NetworkUtils
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
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
        val application = getApplication<Application>()

        if (state.email.isBlank() || state.password.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Vui lòng nhập email và mật khẩu.") }
            return
        }
        if (state.isRegister && state.password != state.confirmPassword) {
            _uiState.update { it.copy(errorMessage = "Mật khẩu xác nhận không khớp.") }
            return
        }
        if (!NetworkUtils.isInternetAvailable(application)) {
            _uiState.update {
                it.copy(errorMessage = "Không có kết nối Internet. Vui lòng kiểm tra mạng và thử lại.")
            }
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
                        "Phiên đăng nhập chưa được tạo. Có thể hệ thống đang yêu cầu xác nhận email trước khi đăng nhập."
                    )
                }

                if (previousEmail != null && previousEmail != trimmedEmail) {
                    SupabaseRestoreManager.clearLocalData(application)
                }

                settings.resetCloudRestoreState()
                settings.setSignedIn(trimmedEmail)
                runCatching { SupabaseRestoreManager.refreshForSignedInUser(application) }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        errorMessage = throwable.toFriendlyAuthMessage()
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

    private fun Throwable.toFriendlyAuthMessage(): String {
        val fullMessage = buildString {
            append(message.orEmpty())
            generateSequence(cause) { it.cause }
                .mapNotNull { it.message }
                .filter { it.isNotBlank() }
                .forEach {
                    append('\n')
                    append(it)
                }
        }

        return when {
            this is UnknownHostException ||
                this is SocketTimeoutException ||
                this is IOException ||
                fullMessage.contains("Unable to resolve host", ignoreCase = true) ||
                fullMessage.contains("No address associated with hostname", ignoreCase = true) ||
                fullMessage.contains("timeout", ignoreCase = true) ||
                fullMessage.contains("network", ignoreCase = true) ->
                "Không thể kết nối tới máy chủ. Vui lòng kiểm tra mạng và thử lại."
            fullMessage.contains("Anonymous sign-ins are disabled", ignoreCase = true) ->
                "Hệ thống đăng ký đang tạm thời gặp sự cố. Vui lòng thử lại sau."
            fullMessage.contains("Phiên đăng nhập chưa được tạo", ignoreCase = true) ->
                "Tài khoản có thể chưa sẵn sàng để đăng nhập. Vui lòng thử lại sau."
            fullMessage.contains("User already registered", ignoreCase = true) ->
                "Email này đã được đăng ký."
            fullMessage.contains("Invalid login credentials", ignoreCase = true) ->
                "Email hoặc mật khẩu không đúng."
            else -> "Xác thực thất bại. Vui lòng thử lại."
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
