package com.wallet.manager.ui.screen.settings

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wallet.manager.R
import com.wallet.manager.viewmodel.AppLanguage
import com.wallet.manager.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenDrawer: () -> Unit,
    vm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
) {
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current

    if (state.showPasscodeSetup) {
        PasscodeDialog(
            title = if (state.hasPasscode) stringResource(R.string.update_pin) else stringResource(R.string.setup_pin),
            onDismiss = { vm.cancelPasscodeSetup() },
            onConfirm = { vm.setPasscode(it) }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Menu"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
                .fillMaxSize()
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        val accountInitial = state.signedInEmail?.firstOrNull()?.uppercaseChar()?.toString()
                        if (accountInitial.isNullOrBlank()) {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(28.dp)
                            )
                        } else {
                            Text(
                                text = accountInitial,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("Account", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = state.signedInEmail ?: "Not signed in",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(
                            onClick = vm::signOut,
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("Sign out")
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            Text(stringResource(R.string.security), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            
            // Switch Passcode
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.use_passcode))
                Switch(
                    checked = state.requirePasscode,
                    onCheckedChange = { checked ->
                        if (!checked && (state.requirePasscode || state.requireBiometric)) {
                            authenticateBiometric(
                                context = context,
                                title = context.getString(R.string.auth_to_disable_security),
                                onSuccess = { vm.setRequirePasscode(false) }
                            )
                        } else {
                            vm.setRequirePasscode(checked)
                        }
                    }
                )
            }
            
            // Switch Biometric (Chỉ hiện nếu đã bật Passcode)
            if (state.requirePasscode) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.use_biometric))
                    Switch(
                        checked = state.requireBiometric,
                        onCheckedChange = { checked ->
                            if (!checked) {
                                authenticateBiometric(
                                    context = context,
                                    title = context.getString(R.string.auth_to_disable_security),
                                    onSuccess = { vm.setRequireBiometric(false) }
                                )
                            } else {
                                vm.setRequireBiometric(true)
                            }
                        }
                    )
                }
            }
            
            if (state.requirePasscode && state.hasPasscode) {
                TextButton(
                    onClick = {
                        authenticateBiometric(
                            context = context,
                            title = context.getString(R.string.auth_to_change_pin),
                            onSuccess = { vm.showUpdatePasscode() }
                        )
                    },
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.update_pin))
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(stringResource(R.string.appearance), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.dark_mode))
                Switch(
                    checked = state.darkTheme,
                    onCheckedChange = { vm.setDarkTheme(it) }
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(stringResource(R.string.gemini_config), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = state.apiKeyText,
                onValueChange = { if (state.isEditingApiKey) vm.onApiKeyChange(it) },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.isEditingApiKey,
                label = { Text(stringResource(R.string.api_key_label)) },
                visualTransformation = if (state.showPlainApiKey) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    if (state.hasStoredApiKey && !state.isEditingApiKey) {
                        IconButton(onClick = {
                            if (state.requirePasscode) {
                                authenticateBiometric(
                                    context = context,
                                    title = context.getString(R.string.auth_to_view_api),
                                    onSuccess = { vm.setShowPlain(!state.showPlainApiKey) }
                                )
                            } else {
                                vm.setShowPlain(!state.showPlainApiKey)
                            }
                        }) {
                            Icon(
                                imageVector = if (state.showPlainApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null
                            )
                        }
                    }
                }
            )

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    if (state.isEditingApiKey) {
                        if (state.requirePasscode && state.hasStoredApiKey) {
                            authenticateBiometric(
                                context = context,
                                title = context.getString(R.string.auth_to_save_api),
                                onSuccess = { vm.saveApiKey() }
                            )
                        } else {
                            vm.saveApiKey()
                        }
                    } else {
                        if (state.requirePasscode) {
                            authenticateBiometric(
                                context = context,
                                title = context.getString(R.string.auth_to_edit_api),
                                onSuccess = { vm.startEditFromStored() }
                            )
                        } else {
                            vm.startEditFromStored()
                        }
                    }
                }
            ) {
                Text(if (state.isEditingApiKey) stringResource(R.string.update) else stringResource(R.string.edit))
            }

            Spacer(Modifier.height(24.dp))

            Text(stringResource(R.string.language), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LanguageChip(
                    label = stringResource(R.string.vietnamese),
                    emoji = "🇻🇳",
                    selected = state.language == AppLanguage.VI,
                    onClick = { vm.setLanguage(AppLanguage.VI) }
                )
                LanguageChip(
                    label = stringResource(R.string.english),
                    emoji = "🇺🇸",
                    selected = state.language == AppLanguage.EN,
                    onClick = { vm.setLanguage(AppLanguage.EN) }
                )
            }

            Spacer(Modifier.height(24.dp))

            Text(stringResource(R.string.cloud_sync_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            state.lastCloudSyncLabel?.let { lastSync ->
                Text(
                    text = stringResource(R.string.last_cloud_sync, lastSync),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
            }

            Text(
                text = "Local changes are synced to cloud automatically. Use restore only when reinstalling or recovering this device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { vm.restoreFromCloud() },
                enabled = !state.isCloudSyncing
            ) {
                Text(stringResource(R.string.restore_from_cloud))
            }

            if (state.isCloudSyncing) {
                Spacer(Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.cloud_sync_in_progress))
                }
            }

            state.cloudSyncMessage?.let { message ->
                Spacer(Modifier.height(12.dp))
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun LanguageChip(
    label: String,
    emoji: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        tonalElevation = if (selected) 4.dp else 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(emoji)
            Text(label)
        }
    }
}

private fun authenticateBiometric(
    context: android.content.Context,
    title: String,
    onSuccess: () -> Unit
) {
    val activity = context as? FragmentActivity ?: run {
        onSuccess() 
        return
    }
    val biometricManager = BiometricManager.from(context)
    val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
    
    if (biometricManager.canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
        onSuccess()
        return
    }

    val executor = ContextCompat.getMainExecutor(context)
    val prompt = BiometricPrompt(
        activity,
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }
        }
    )

    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setSubtitle(context.getString(R.string.security_sub))
        .setAllowedAuthenticators(authenticators)
        .build()

    prompt.authenticate(info)
}
