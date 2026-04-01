package com.wallet.manager.ui.screen.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wallet.manager.viewmodel.AuthViewModel

@Composable
fun AuthScreen(
    vm: AuthViewModel = viewModel(factory = AuthViewModel.Factory)
) {
    val state by vm.uiState.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 2.dp,
            shadowElevation = 10.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Wallet AI",
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = "Minimal personal finance, synced to your account.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                TabRow(selectedTabIndex = if (state.isRegister) 1 else 0) {
                    Tab(selected = !state.isRegister, onClick = { vm.setMode(false) }, text = { Text("Login") })
                    Tab(selected = state.isRegister, onClick = { vm.setMode(true) }, text = { Text("Register") })
                }

                OutlinedTextField(
                    value = state.email,
                    onValueChange = vm::onEmailChange,
                    label = { Text("Email") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = state.password,
                    onValueChange = vm::onPasswordChange,
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )

                if (state.isRegister) {
                    OutlinedTextField(
                        value = state.confirmPassword,
                        onValueChange = vm::onConfirmPasswordChange,
                        label = { Text("Confirm password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                state.errorMessage?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Button(
                    onClick = vm::submit,
                    enabled = !state.isSubmitting,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (state.isSubmitting) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.height(18.dp))
                            Text("Please wait")
                        }
                    } else {
                        Text(if (state.isRegister) "Create account" else "Sign in")
                    }
                }

                Spacer(Modifier.height(4.dp))
                TextButton(onClick = { vm.setMode(!state.isRegister) }) {
                    Text(if (state.isRegister) "Already have an account?" else "Create a new account")
                }
            }
        }
    }
}
