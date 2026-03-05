package com.wallet.manager.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("settings")

class SettingsDataStore(private val context: Context) {

    private val KEY_DARK_THEME = booleanPreferencesKey("dark_theme")
    private val KEY_REQUIRE_PASSCODE = booleanPreferencesKey("require_passcode")
    private val KEY_REQUIRE_BIOMETRIC = booleanPreferencesKey("require_biometric")
    private val KEY_LANGUAGE = stringPreferencesKey("language")

    val darkThemeFlow: Flow<Boolean> = context.dataStore.data.map { prefs: Preferences ->
        prefs[KEY_DARK_THEME] ?: false
    }

    val requirePasscodeFlow: Flow<Boolean> = context.dataStore.data.map { prefs: Preferences ->
        prefs[KEY_REQUIRE_PASSCODE] ?: false
    }

    val requireBiometricFlow: Flow<Boolean> = context.dataStore.data.map { prefs: Preferences ->
        prefs[KEY_REQUIRE_BIOMETRIC] ?: false
    }

    val languageFlow: Flow<com.wallet.manager.viewmodel.AppLanguage> =
        context.dataStore.data.map { prefs: Preferences ->
            when (prefs[KEY_LANGUAGE]) {
                "EN" -> com.wallet.manager.viewmodel.AppLanguage.EN
                else -> com.wallet.manager.viewmodel.AppLanguage.VI
            }
        }

    suspend fun setDarkTheme(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_DARK_THEME] = enabled
        }
    }

    suspend fun setRequirePasscode(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_REQUIRE_PASSCODE] = enabled
        }
    }

    suspend fun setRequireBiometric(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_REQUIRE_BIOMETRIC] = enabled
        }
    }

    suspend fun setLanguage(lang: com.wallet.manager.viewmodel.AppLanguage) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LANGUAGE] = if (lang == com.wallet.manager.viewmodel.AppLanguage.EN) "EN" else "VI"
        }
    }
}
