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
                chatDao.insertMessage(
                    ChatMessageEntity(
                        isUser = false,
                        text = "Chưa cấu hình Gemini API key trong Cài đặt."
                    )
                )
            }
            return
        }

        val messageText = _uiState.value.input.trim()
        if (messageText.isEmpty()) return

        _uiState.update { it.copy(input = "", isSending = true) }

        viewModelScope.launch {
            chatDao.insertMessage(ChatMessageEntity(isUser = true, text = messageText))

            try {
                val assistant = GeminiChatAssistant(currentKey)
                val expensesWithFriends = repo.getAllExpensesWithFriends().first()
                val allFriends = friendRepo.getAllFriends().first()
                val friendsMap = allFriends.associateBy { it.id }

                val arr = JSONArray()
                expensesWithFriends.forEach { item ->
                    val expense = item.expense
                    val obj = JSONObject()
                    obj.put("id", expense.id)
                    obj.put("type", expense.type)
                    obj.put("title", expense.title)
                    obj.put("amount", expense.amount)
                    obj.put("date", expense.date)
                    obj.put("isSplit", expense.isSplit)
                    obj.put("myShareCount", expense.myShareCount)

                    val payerName = if (expense.payerId == null) {
                        "Tôi"
                    } else {
                        friendsMap[expense.payerId]?.name ?: "Người lạ"
                    }
                    obj.put("payer", payerName)

                    val participants = JSONArray()
                    item.friendCrossRefs.forEach { ref ->
                        val friend = friendsMap[ref.friendId]
                        if (friend != null) {
                            val friendObj = JSONObject()
                            friendObj.put("name", friend.name)
                            friendObj.put("shareCount", ref.shareCount)
                            friendObj.put("isSettled", ref.isSettled)
                            participants.put(friendObj)
                        }
                    }
                    obj.put("participants", participants)
                    arr.put(obj)
                }

                val summary = JSONObject()
                summary.put("expenses", arr)
                summary.put(
                    "instruction",
                    """
                    Bạn là một trợ lý tài chính cá nhân thân thiện, thông minh tên là "Ví Thông Minh".
                    Nhiệm vụ của bạn là giúp người dùng phân tích nợ nần và chi tiêu một cách dễ hiểu, gần gũi như một người bạn.

                    PHONG CÁCH TRẢ LỜI:
                    - Thân thiện, tự nhiên, dễ đọc.
                    - Có thể dùng emoji phù hợp để câu trả lời sinh động hơn.
                    - Ưu tiên giải thích rõ ai nợ ai, số tiền bao nhiêu, khoản nào đã thanh toán và khoản nào chưa.
                    - Nếu có nhiều số liệu, hãy trình bày thành danh sách ngắn gọn.

                    DỮ LIỆU PHÂN TÍCH:
                    $arr

                    QUY TẮC TÍNH TOÁN:
                    1. Mỗi chi tiêu có tổng số suất = myShareCount + tổng shareCount của các participants.
                    2. Số tiền mỗi suất = amount / tổng số suất.
                    3. Nếu người trả là "Tôi":
                       - Bạn bè trong participants nợ "Tôi" số tiền = số suất của họ * số tiền mỗi suất.
                       - Nếu isSettled = true: họ đã trả rồi.
                       - Nếu isSettled = false: họ vẫn còn nợ.
                    4. Nếu người trả là một người bạn:
                       - "Tôi" nợ người đó số tiền = myShareCount * số tiền mỗi suất.
                       - Nếu isSettled = true: tôi đã trả rồi.
                       - Nếu isSettled = false: tôi vẫn còn nợ.

                    Hãy trả lời câu hỏi của người dùng dựa trên dữ liệu trên. Nếu người dùng chỉ chào hỏi, hãy giới thiệu bản thân ngắn gọn và thân thiện.
                    """.trimIndent()
                )

                val reply = assistant.chatWithContext(messageText, summary.toString())
                chatDao.insertMessage(ChatMessageEntity(isUser = false, text = reply))
                _uiState.update { it.copy(isSending = false) }
            } catch (t: Throwable) {
                chatDao.insertMessage(
                    ChatMessageEntity(
                        isUser = false,
                        text = "Tính năng AI hiện không khả dụng. ${t.message ?: ""}".trim()
                    )
                )
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
