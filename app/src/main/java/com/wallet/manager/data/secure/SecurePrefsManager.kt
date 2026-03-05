package com.wallet.manager.data.secure

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

class SecurePrefsManager private constructor(context: Context) {

    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

    private val prefs = EncryptedSharedPreferences.create(
        "secure_prefs",
        masterKeyAlias,
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val KEY_GEMINI_API = "gemini_api_key"
    private val KEY_PASSCODE = "app_passcode"
    
    private var cachedApiKey: String? = null

    fun getGeminiApiKey(): String? {
        if (cachedApiKey == null) {
            cachedApiKey = prefs.getString(KEY_GEMINI_API, null)
        }
        return cachedApiKey
    }

    fun setGeminiApiKey(value: String) {
        cachedApiKey = value
        prefs.edit().putString(KEY_GEMINI_API, value).apply()
    }

    fun getPasscode(): String? {
        return prefs.getString(KEY_PASSCODE, null)
    }

    fun setPasscode(value: String) {
        prefs.edit().putString(KEY_PASSCODE, value).apply()
    }

    fun clearPasscode() {
        prefs.edit().remove(KEY_PASSCODE).apply()
    }

    fun hasPasscode(): Boolean = !getPasscode().isNullOrEmpty()

    companion object {
        @Volatile
        private var INSTANCE: SecurePrefsManager? = null

        fun getInstance(context: Context): SecurePrefsManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: SecurePrefsManager(context.applicationContext).also { INSTANCE = it }
            }
    }
}
