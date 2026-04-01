package com.wallet.manager.ui.screen.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wallet.manager.R
import com.wallet.manager.viewmodel.ChatViewModel
import com.wallet.manager.viewmodel.ChatMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onOpenDrawer: () -> Unit,
    vm: ChatViewModel = viewModel(factory = ChatViewModel.Factory)
) {
    val state by vm.uiState.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.chat_title)) },
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
                },
                actions = {
                    if (state.messages.isNotEmpty()) {
                        IconButton(onClick = vm::clearHistory) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.clear_history),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                reverseLayout = true,
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(state.messages.reversed()) { msg ->
                    ChatBubble(message = msg)
                    Spacer(Modifier.height(8.dp))
                }
            }

            if (state.isSending) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 1.dp,
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                        .navigationBarsPadding()
                        .imePadding(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = state.input,
                        onValueChange = vm::onInputChange,
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(stringResource(R.string.ask_placeholder)) },
                        maxLines = 4
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = vm::sendMessage,
                        enabled = !state.isSending && state.input.isNotBlank(),
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Gửi")
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    val isUser = message.isUser
    val alignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    val bg = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer
    val textColor = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
    val shape = if (isUser) {
        MaterialTheme.shapes.medium.copy(bottomEnd = CornerSize(0.dp))
    } else {
        MaterialTheme.shapes.medium.copy(bottomStart = CornerSize(0.dp))
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        Surface(
            color = bg,
            shape = shape,
            tonalElevation = 1.dp
        ) {
            SelectionContainer {
                if (isUser) {
                    Text(
                        text = message.text,
                        modifier = Modifier.padding(12.dp),
                        color = textColor,
                        style = MaterialTheme.typography.bodyLarge
                    )
                } else {
                    MarkdownText(
                        text = message.text,
                        modifier = Modifier.padding(12.dp),
                        color = textColor
                    )
                }
            }
        }
    }
}

@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    val annotatedString = remember(text) { parseMarkdown(text) }
    Text(
        text = annotatedString,
        modifier = modifier,
        color = color,
        style = MaterialTheme.typography.bodyLarge
    )
}

/**
 * A simple markdown parser for bold (**) and italic (*) text.
 */
fun parseMarkdown(text: String): AnnotatedString {
    return buildAnnotatedString {
        val boldItalicRegex = Regex("""\*\*\*(.*?)\*\*\*""")
        val boldRegex = Regex("""\*\*(.*?)\*\*""")
        val italicRegex = Regex("""\*(.*?)\*""")
        
        val matches = (boldItalicRegex.findAll(text) + boldRegex.findAll(text) + italicRegex.findAll(text))
            .sortedBy { it.range.first }
            .toList()

        var lastIndex = 0
        for (match in matches) {
            // Check for overlap with previous match
            if (match.range.first < lastIndex) continue

            // Append text before the match
            append(text.substring(lastIndex, match.range.first))
            
            val content = match.groupValues[1]
            when {
                match.value.startsWith("***") -> {
                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)) {
                        append(content)
                    }
                }
                match.value.startsWith("**") -> {
                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(content)
                    }
                }
                match.value.startsWith("*") -> {
                    withStyle(style = SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(content)
                    }
                }
            }
            lastIndex = match.range.last + 1
        }
        
        if (lastIndex < text.length) {
            append(text.substring(lastIndex))
        }
    }
}
