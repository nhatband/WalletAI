package com.wallet.manager.ui.screen.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

@Composable
fun PasscodeDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var code by remember { mutableStateFlowOf("") }
    var error by remember { mutableStateFlowOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = code,
                    onValueChange = { 
                        if (it.length <= 6 && it.all { char -> char.isDigit() }) {
                            code = it
                            error = null
                        }
                    },
                    label = { Text("Mã PIN (6 số)") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    isError = error != null,
                    supportingText = { error?.let { Text(it) } }
                )
                
                Spacer(Modifier.height(24.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Hủy") }
                    Button(
                        onClick = {
                            if (code.length == 6) {
                                onConfirm(code)
                            } else {
                                error = "Vui lòng nhập đủ 6 số"
                            }
                        }
                    ) {
                        Text("Xác nhận")
                    }
                }
            }
        }
    }
}

private fun <T> mutableStateFlowOf(value: T) = mutableStateOf(value)
