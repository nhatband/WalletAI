package com.wallet.manager

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import com.wallet.manager.data.prefs.SettingsDataStore
import com.wallet.manager.data.secure.SecurePrefsManager
import com.wallet.manager.ui.navigation.AppDestination
import com.wallet.manager.ui.navigation.WalletApp
import com.wallet.manager.ui.theme.WalletTheme
import com.wallet.manager.viewmodel.AppLanguage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val settings = SettingsDataStore(this)
        val secure = SecurePrefsManager.getInstance(this)

        setContent {
            val darkTheme by settings.darkThemeFlow.collectAsState(initial = false)
            val language by settings.languageFlow.collectAsState(initial = AppLanguage.VI)
            
            val locale = remember(language) { 
                if (language == AppLanguage.EN) Locale.US else Locale("vi", "VN")
            }
            
            val configuration = LocalConfiguration.current
            val context = LocalContext.current
            
            val localizedContext = remember(locale, context) {
                Locale.setDefault(locale)
                val config = Configuration(configuration)
                config.setLocale(locale)
                config.setLayoutDirection(locale)
                context.createConfigurationContext(config)
            }

            var isAppLocked by remember { mutableStateOf(false) }
            val composeScope = rememberCoroutineScope()

            LaunchedEffect(Unit) {
                if (settings.requirePasscodeFlow.first()) {
                    isAppLocked = true
                    val useBio = settings.requireBiometricFlow.first()
                    if (useBio) {
                        showBiometricGate(
                            onSuccess = { isAppLocked = false },
                            onFallback = { /* Wait for passcode */ }
                        )
                    }
                }
            }

            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalConfiguration provides localizedContext.resources.configuration,
                LocalActivityResultRegistryOwner provides this
            ) {
                key(language) {
                    WalletTheme(darkTheme = darkTheme) {
                        Surface(modifier = Modifier.fillMaxSize()) {
                            val navController = rememberNavController()
                            val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

                            WalletApp(
                                navController = navController,
                                drawerState = drawerState,
                                onNavigate = { dest ->
                                    composeScope.launch {
                                        drawerState.close()
                                        navController.navigate(dest.route) {
                                            launchSingleTop = true
                                            popUpTo(AppDestination.HOME.route)
                                        }
                                    }
                                },
                                onOpenDrawer = {
                                    composeScope.launch { drawerState.open() }
                                }
                            )

                            if (isAppLocked) {
                                AppLockScreen(
                                    onPasscodeEntered = { input ->
                                        if (input == secure.getPasscode()) {
                                            isAppLocked = false
                                            true
                                        } else {
                                            false
                                        }
                                    },
                                    onBiometricClick = {
                                        showBiometricGate(
                                            onSuccess = { isAppLocked = false },
                                            onFallback = {}
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun showBiometricGate(onSuccess: () -> Unit, onFallback: () -> Unit) {
        val biometricManager = BiometricManager.from(this)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        
        if (biometricManager.canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            onFallback()
            return
        }

        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationError(code: Int, errString: CharSequence) {
                    super.onAuthenticationError(code, errString)
                    onFallback()
                }
            }
        )

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.lock_prompt_title))
            .setSubtitle(getString(R.string.lock_prompt_sub))
            .setAllowedAuthenticators(authenticators)
            .build()

        prompt.authenticate(info)
    }
}

@Composable
fun AppLockScreen(
    onPasscodeEntered: (String) -> Boolean,
    onBiometricClick: () -> Unit
) {
    var passcode by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    BackHandler {
        // Do nothing, prevent going back
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) {}
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { /* Block */ }
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.lock_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.lock_desc),
                style = MaterialTheme.typography.bodyMedium
            )
            
            Spacer(Modifier.height(32.dp))
            
            OutlinedTextField(
                value = passcode,
                onValueChange = {
                    if (it.length <= 6 && it.all { c -> c.isDigit() }) {
                        passcode = it
                        error = false
                        if (it.length == 6) {
                            if (onPasscodeEntered(it)) {
                                // Success
                            } else {
                                error = true
                                passcode = ""
                            }
                        }
                    }
                },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                label = { Text("PIN") },
                isError = error,
                supportingText = { if (error) Text(stringResource(R.string.lock_error)) },
                modifier = Modifier.width(200.dp)
            )
            
            Spacer(Modifier.height(24.dp))
            
            TextButton(
                onClick = onBiometricClick
            ) {
                Text(stringResource(R.string.lock_biometric))
            }
        }
    }
}
