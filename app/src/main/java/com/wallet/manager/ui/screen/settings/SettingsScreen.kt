package com.wallet.manager.ui.screen.settings

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
            onDismiss = vm::cancelPasscodeSetup,
            onConfirm = vm::setPasscode
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
                        Icon(Icons.Default.Menu, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState())
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AccountCard(
                signedInEmail = state.signedInEmail,
                onSignOut = vm::signOut
            )

            SettingsSectionCard(title = stringResource(R.string.security)) {
                SettingToggleRow(
                    title = stringResource(R.string.use_passcode),
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

                if (state.requirePasscode) {
                    Spacer(Modifier.size(12.dp))
                    SettingToggleRow(
                        title = stringResource(R.string.use_biometric),
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

                if (state.requirePasscode && state.hasPasscode) {
                    Spacer(Modifier.size(8.dp))
                    TextButton(
                        onClick = {
                            authenticateBiometric(
                                context = context,
                                title = context.getString(R.string.auth_to_change_pin),
                                onSuccess = vm::showUpdatePasscode
                            )
                        },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.update_pin))
                    }
                }
            }

            SettingsSectionCard(title = stringResource(R.string.appearance)) {
                SettingToggleRow(
                    title = stringResource(R.string.dark_mode),
                    checked = state.darkTheme,
                    onCheckedChange = vm::setDarkTheme
                )
            }

            SettingsSectionCard(title = stringResource(R.string.gemini_config)) {
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
                            IconButton(
                                onClick = {
                                    if (state.requirePasscode) {
                                        authenticateBiometric(
                                            context = context,
                                            title = context.getString(R.string.auth_to_view_api),
                                            onSuccess = { vm.setShowPlain(!state.showPlainApiKey) }
                                        )
                                    } else {
                                        vm.setShowPlain(!state.showPlainApiKey)
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (state.showPlainApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null
                                )
                            }
                        }
                    }
                )

                Spacer(Modifier.size(10.dp))
                Button(
                    onClick = {
                        if (state.isEditingApiKey) {
                            if (state.requirePasscode && state.hasStoredApiKey) {
                                authenticateBiometric(
                                    context = context,
                                    title = context.getString(R.string.auth_to_save_api),
                                    onSuccess = vm::saveApiKey
                                )
                            } else {
                                vm.saveApiKey()
                            }
                        } else {
                            if (state.requirePasscode) {
                                authenticateBiometric(
                                    context = context,
                                    title = context.getString(R.string.auth_to_edit_api),
                                    onSuccess = vm::startEditFromStored
                                )
                            } else {
                                vm.startEditFromStored()
                            }
                        }
                    }
                ) {
                    Text(if (state.isEditingApiKey) stringResource(R.string.update) else stringResource(R.string.edit))
                }
            }

            SettingsSectionCard(title = stringResource(R.string.language)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LanguageChip(
                        label = stringResource(R.string.vietnamese),
                        selected = state.language == AppLanguage.VI,
                        onClick = { vm.setLanguage(AppLanguage.VI) }
                    )
                    LanguageChip(
                        label = stringResource(R.string.english),
                        selected = state.language == AppLanguage.EN,
                        onClick = { vm.setLanguage(AppLanguage.EN) }
                    )
                }
            }

            SettingsSectionCard(title = stringResource(R.string.cloud_sync_title)) {
                state.lastCloudSyncLabel?.let { lastSync ->
                    Text(
                        text = stringResource(R.string.last_cloud_sync, lastSync),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.size(8.dp))
                }

                Text(
                    text = stringResource(R.string.cloud_sync_auto_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.size(12.dp))

                Button(
                    onClick = vm::restoreFromCloud,
                    enabled = !state.isCloudSyncing
                ) {
                    Text(stringResource(R.string.restore_from_cloud))
                }

                if (state.isCloudSyncing) {
                    Spacer(Modifier.size(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Text(stringResource(R.string.cloud_sync_in_progress))
                    }
                }

                state.cloudSyncMessage?.let { message ->
                    Spacer(Modifier.size(12.dp))
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(Modifier.size(12.dp))
        }
    }
}

@Composable
private fun AccountCard(
    signedInEmail: String?,
    onSignOut: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                val initial = signedInEmail?.firstOrNull()?.uppercaseChar()?.toString()
                if (initial.isNullOrBlank()) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(28.dp)
                    )
                } else {
                    Text(
                        text = initial,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(stringResource(R.string.account_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = signedInEmail ?: stringResource(R.string.not_signed_in),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = onSignOut, contentPadding = PaddingValues(0.dp)) {
                    Text(stringResource(R.string.sign_out))
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            content = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.size(8.dp))
                content()
            }
        )
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(Modifier.size(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun LanguageChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        }
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
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
    val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
        BiometricManager.Authenticators.DEVICE_CREDENTIAL

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
