package com.wallet.manager.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.wallet.manager.ai.GeminiChatAssistant
import com.wallet.manager.data.local.db.AppDatabase
import com.wallet.manager.data.local.db.ChatDao
import com.wallet.manager.data.local.entity.ChatMessageEntity
import com.wallet.manager.data.repository.ExpenseRepository
import com.wallet.manager.data.repository.ExpenseRepositoryImpl
import com.wallet.manager.data.repository.FriendRepository
import com.wallet.manager.data.repository.FriendRepositoryImpl
import com.wallet.manager.data.secure.SecurePrefsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

data class ChatMessage(val isUser: Boolean, val text: String)

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val input: String = "",
    val isSending: Boolean = false,
    val isApiKeyMissing: Boolean = false
)

class ChatViewModel(
    private val repo: ExpenseRepository,
    private val friendRepo: FriendRepository,
    private val chatDao: ChatDao,
    private val secure: SecurePrefsManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    private val apiKeyFlow = MutableStateFlow(secure.getGeminiApiKey() ?: "")

    val uiState: StateFlow<ChatUiState> = combine(
        _uiState, 
        apiKeyFlow,
        chatDao.getAllMessages().map { entities -> 
            entities.map { ChatMessage(it.isUser, it.text) }
        }
    ) { state, key, history ->
        state.copy(
            isApiKeyMissing = key.isEmpty(),
            messages = history
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ChatUiState()
    )

    fun onInputChange(text: String) {
        _uiState.update { it.copy(input = text) }
    }

    fun clearHistory() {
        viewModelScope.launch {
            chatDao.clearHistory()
        }
    }

    fun sendMessage() {
        val currentKey = secure.getGeminiApiKey().orEmpty()
        if (currentKey.isEmpty()) {
            viewModelScope.launch {
                chatDao.insertMessage(ChatMessageEntity(isUser = false, text = "Chưa cấu hình Gemini API key trong Cài đặt."))
            }
            return
        }

        val messageText = _uiState.value.input.trim()
        if (messageText.isEmpty()) return

        _uiState.update { it.copy(input = "", isSending = true) }

        viewModelScope.launch {
            // Lưu tin nhắn người dùng
            chatDao.insertMessage(ChatMessageEntity(isUser = true, text = messageText))

            try {
                val assistant = GeminiChatAssistant(currentKey)
                val expensesWithFriends = repo.getAllExpensesWithFriends().first()
                val allFriends = friendRepo.getAllFriends().first()
                val friendsMap = allFriends.associateBy { it.id }

                val arr = JSONArray()
                expensesWithFriends.forEach { item ->
                    val e = item.expense
                    val obj = JSONObject()
                    obj.put("type", e.type)
                    obj.put("title", e.title)
                    obj.put("amount", e.amount)
                    obj.put("date", e.date)
                    obj.put("isSplit", e.isSplit)
                    
                    val payerName = if (e.payerId == null) "Tôi" else friendsMap[e.payerId]?.name ?: "Người lạ"
                    obj.put("payer", payerName)
                    
                    val friendsInvolved = JSONArray()
                    item.friends.forEach { f ->
                        friendsInvolved.put(f.name)
                    }
                    obj.put("friendsInvolved", friendsInvolved)
                    
                    arr.put(obj)
                }
                
                val summary = JSONObject()
                summary.put("expenses", arr)
                summary.put("note", "Nếu isSplit là true, số tiền được chia đều cho Tôi và tất cả những người trong friendsInvolved. Nếu Payer là Tôi, những người khác nợ tôi. Nếu Payer là một người bạn, tôi nợ họ phần của tôi.")

                val reply = assistant.chatWithContext(messageText, summary.toString())

                // Lưu phản hồi của bot
                chatDao.insertMessage(ChatMessageEntity(isUser = false, text = reply))
                _uiState.update { it.copy(isSending = false) }
            } catch (e: Exception) {
                chatDao.insertMessage(ChatMessageEntity(isUser = false, text = "Lỗi kết nối Gemini: ${e.message}"))
                _uiState.update { it.copy(isSending = false) }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val appContext =
                    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                        ?: throw IllegalArgumentException("Application context missing")
                val db = AppDatabase.get(appContext)
                val repo: ExpenseRepository = ExpenseRepositoryImpl(db.expenseDao())
                val friendRepo: FriendRepository = FriendRepositoryImpl(db.friendDao(), db.expenseDao())
                val chatDao = db.chatDao()
                val secure = SecurePrefsManager.getInstance(appContext)
                ChatViewModel(repo, friendRepo, chatDao, secure)
            }
        }
    }
}
