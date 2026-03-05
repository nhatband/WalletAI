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
                    obj.put("id", e.id)
                    obj.put("type", e.type)
                    obj.put("title", e.title)
                    obj.put("amount", e.amount)
                    obj.put("date", e.date)
                    obj.put("isSplit", e.isSplit)
                    obj.put("myShareCount", e.myShareCount)
                    
                    val payerName = if (e.payerId == null) "Tôi" else friendsMap[e.payerId]?.name ?: "Người lạ"
                    obj.put("payer", payerName)
                    
                    val friendsInvolved = JSONArray()
                    item.friendCrossRefs.forEach { ref ->
                        val f = friendsMap[ref.friendId]
                        if (f != null) {
                            val fObj = JSONObject()
                            fObj.put("name", f.name)
                            fObj.put("shareCount", ref.shareCount)
                            fObj.put("isSettled", ref.isSettled)
                            friendsInvolved.put(fObj)
                        }
                    }
                    obj.put("participants", friendsInvolved)
                    
                    arr.put(obj)
                }
                
                val summary = JSONObject()
                summary.put("expenses", arr)
                summary.put("instruction", """
                    Bạn là một trợ lý tài chính cá nhân thân thiện, thông minh tên là "Ví Thông Minh". 
                    Nhiệm vụ của bạn là giúp người dùng phân tích nợ nần và chi tiêu một cách dễ hiểu, gần gũi như một người bạn.

                    PHONG CÁCH TRẢ LỜI:
                    - Thân thiện, sử dụng ngôn ngữ tự nhiên (ví dụ: "Chào bạn nè!", "Để mình kiểm tra giúp nhé...", "Đừng lo, mình tính xong rồi đây!").
                    - Sử dụng emoji phù hợp để làm câu trả lời sinh động (💰, 🤝, 📊, ✨).
                    - Trình bày thông tin rõ ràng, ưu tiên sử dụng danh sách (bullet points) hoặc bảng đơn giản nếu có nhiều số liệu.
                    - Tập trung vào việc giải quyết nỗi lo về nợ nần: nhấn mạnh ai nợ ai bao nhiêu và khoản nào đã trả/chưa trả.

                    DỮ LIỆU PHÂN TÍCH:
                    ${arr}

                    QUY TẮC TÍNH TOÁN (Hãy tự tính toán dựa trên logic này):
                    1. Mỗi chi tiêu có tổng số suất (shares) = myShareCount + tổng shareCount của các participants.
                    2. Số tiền mỗi suất = amount / tổng số suất.
                    3. Nếu Payer (Người trả) là 'Tôi':
                       - Bạn bè trong 'participants' nợ 'Tôi' số tiền = (số suất của họ) * (số tiền mỗi suất).
                       - Nếu isSettled = true: họ đã trả rồi (ghi nhận là 'Đã thanh toán').
                       - Nếu isSettled = false: họ vẫn còn nợ (ghi nhận là 'Chưa thanh toán').
                    4. Nếu Payer là một người bạn (ví dụ 'Nguyễn Văn B'):
                       - 'Tôi' nợ 'Nguyễn Văn B' số tiền = myShareCount * (số tiền mỗi suất).
                       - Nếu isSettled = true: tôi đã trả rồi.
                       - Nếu isSettled = false: tôi vẫn còn nợ bạn đó.

                    Hãy trả lời câu hỏi của người dùng dựa trên dữ liệu và phong cách trên. Nếu người dùng chỉ chào hỏi, hãy giới thiệu bản thân một cách dễ thương.
                """.trimIndent())

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
