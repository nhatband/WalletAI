package com.wallet.manager.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.wallet.manager.data.prefs.SettingsDataStore
import com.wallet.manager.data.secure.SecurePrefsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val darkTheme: Boolean = false,
    val requirePasscode: Boolean = false,
    val requireBiometric: Boolean = false,
    val hasPasscode: Boolean = false,
    val apiKeyText: String = "",
    val showPlainApiKey: Boolean = false,
    val isEditingApiKey: Boolean = false,
    val hasStoredApiKey: Boolean = false,
    val language: AppLanguage = AppLanguage.VI,
    val showPasscodeSetup: Boolean = false
)

enum class AppLanguage { VI, EN }

class SettingsViewModel(
    private val settings: SettingsDataStore,
    private val secure: SecurePrefsManager
) : ViewModel() {

    private val pendingApiKey = MutableStateFlow("")
    private val showPlain = MutableStateFlow(false)
    private val isEditing = MutableStateFlow(false)
    private val refreshTrigger = MutableStateFlow(0)
    private val _showPasscodeSetup = MutableStateFlow(false)

    private val apiStateFlow = combine(
        pendingApiKey,
        showPlain,
        isEditing,
        refreshTrigger
    ) { pending, show, editing, _ ->
        val stored = secure.getGeminiApiKey().orEmpty()
        val hasStored = stored.isNotEmpty()
        
        val effectivelyEditing = editing || !hasStored
        
        val displayValue = if (editing) {
            pending
        } else if (!hasStored) {
            pending
        } else {
            stored
        }

        ApiInternalState(
            displayValue = displayValue,
            showPlain = show || effectivelyEditing,
            isEditing = effectivelyEditing,
            hasStored = hasStored
        )
    }

    val uiState: StateFlow<SettingsUiState> =
        combine(
            settings.darkThemeFlow,
            settings.requirePasscodeFlow,
            settings.requireBiometricFlow,
            settings.languageFlow,
            apiStateFlow
        ) { dark, pass, bio, lang, api ->
            SettingsUiState(
                darkTheme = dark,
                requirePasscode = pass,
                requireBiometric = bio,
                hasPasscode = secure.hasPasscode(),
                apiKeyText = if (api.showPlain) api.displayValue else if (api.displayValue.isEmpty()) "" else "************************",
                showPlainApiKey = api.showPlain,
                isEditingApiKey = api.isEditing,
                hasStoredApiKey = api.hasStored,
                language = lang
            )
        }.combine(_showPasscodeSetup) { state, showPassSetup ->
            state.copy(showPasscodeSetup = showPassSetup)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SettingsUiState()
        )

    fun setDarkTheme(enabled: Boolean) {
        viewModelScope.launch {
            settings.setDarkTheme(enabled)
        }
    }

    fun setRequirePasscode(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) {
                if (!secure.hasPasscode()) {
                    _showPasscodeSetup.value = true
                } else {
                    settings.setRequirePasscode(true)
                }
            } else {
                settings.setRequirePasscode(false)
                settings.setRequireBiometric(false)
                secure.clearPasscode()
                refreshTrigger.value += 1
            }
        }
    }

    fun setRequireBiometric(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) {
                if (!secure.hasPasscode()) {
                    _showPasscodeSetup.value = true
                } else {
                    settings.setRequirePasscode(true)
                    settings.setRequireBiometric(true)
                }
            } else {
                settings.setRequireBiometric(false)
            }
        }
    }

    fun setPasscode(code: String) {
        secure.setPasscode(code)
        _showPasscodeSetup.value = false
        viewModelScope.launch {
            settings.setRequirePasscode(true)
        }
        refreshTrigger.value += 1
    }
    
    fun cancelPasscodeSetup() {
        _showPasscodeSetup.value = false
    }

    fun onApiKeyChange(text: String) {
        pendingApiKey.value = text
        isEditing.value = true
    }

    fun saveApiKey() {
        val key = pendingApiKey.value
        if (key.isNotEmpty()) {
            secure.setGeminiApiKey(key)
        }
        pendingApiKey.value = ""
        isEditing.value = false
        showPlain.value = false
        refreshTrigger.value += 1
    }

    fun startEditFromStored() {
        val stored = secure.getGeminiApiKey().orEmpty()
        pendingApiKey.value = stored
        isEditing.value = true
        showPlain.value = true
    }

    fun setShowPlain(show: Boolean) {
        showPlain.value = show
    }

    fun setLanguage(lang: AppLanguage) {
        viewModelScope.launch {
            settings.setLanguage(lang)
        }
    }

    fun showUpdatePasscode() {
        _showPasscodeSetup.value = true
    }

    private data class ApiInternalState(
        val displayValue: String,
        val showPlain: Boolean,
        val isEditing: Boolean,
        val hasStored: Boolean
    )

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val appContext =
                    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                        ?: throw IllegalArgumentException("Application context missing")
                val settings = SettingsDataStore(appContext)
                val secure = SecurePrefsManager.getInstance(appContext)
                SettingsViewModel(settings, secure)
            }
        }
    }
}
