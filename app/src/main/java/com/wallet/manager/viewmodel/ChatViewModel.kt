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
                chatDao.insertMessage(ChatMessageEntity(isUser = false, text = "ChÆ°a cáº¥u hÃ¬nh Gemini API key trong CÃ i Ä‘áº·t."))
            }
            return
        }

        val messageText = _uiState.value.input.trim()
        if (messageText.isEmpty()) return

        _uiState.update { it.copy(input = "", isSending = true) }

        viewModelScope.launch {
            // LÆ°u tin nháº¯n ngÆ°á»i dÃ¹ng
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

                    val payerName = if (e.payerId == null) "TÃ´i" else friendsMap[e.payerId]?.name ?: "NgÆ°á»i láº¡"
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
                    Báº¡n lÃ  má»™t trá»£ lÃ½ tÃ i chÃ­nh cÃ¡ nhÃ¢n thÃ¢n thiá»‡n, thÃ´ng minh tÃªn lÃ  "VÃ­ ThÃ´ng Minh".
                    Nhiá»‡m vá»¥ cá»§a báº¡n lÃ  giÃºp ngÆ°á»i dÃ¹ng phÃ¢n tÃ­ch ná»£ náº§n vÃ  chi tiÃªu má»™t cÃ¡ch dá»… hiá»ƒu, gáº§n gÅ©i nhÆ° má»™t ngÆ°á»i báº¡n.

                    PHONG CÃCH TRáº¢ Lá»œI:
                    - ThÃ¢n thiá»‡n, sá»­ dá»¥ng ngÃ´n ngá»¯ tá»± nhiÃªn (vÃ­ dá»¥: "ChÃ o báº¡n nÃ¨!", "Äá»ƒ mÃ¬nh kiá»ƒm tra giÃºp nhÃ©...", "Äá»«ng lo, mÃ¬nh tÃ­nh xong rá»“i Ä‘Ã¢y!").
                    - Sá»­ dá»¥ng emoji phÃ¹ há»£p Ä‘á»ƒ lÃ m cÃ¢u tráº£ lá»i sinh Ä‘á»™ng (ðŸ’°, ðŸ¤, ðŸ“Š, âœ¨).
                    - TrÃ¬nh bÃ y thÃ´ng tin rÃµ rÃ ng, Æ°u tiÃªn sá»­ dá»¥ng danh sÃ¡ch (bullet points) hoáº·c báº£ng Ä‘Æ¡n giáº£n náº¿u cÃ³ nhiá»u sá»‘ liá»‡u.
                    - Táº­p trung vÃ o viá»‡c giáº£i quyáº¿t ná»—i lo vá» ná»£ náº§n: nháº¥n máº¡nh ai ná»£ ai bao nhiÃªu vÃ  khoáº£n nÃ o Ä‘Ã£ tráº£/chÆ°a tráº£.

                    Dá»® LIá»†U PHÃ‚N TÃCH:
                    ${arr}

                    QUY Táº®C TÃNH TOÃN (HÃ£y tá»± tÃ­nh toÃ¡n dá»±a trÃªn logic nÃ y):
                    1. Má»—i chi tiÃªu cÃ³ tá»•ng sá»‘ suáº¥t (shares) = myShareCount + tá»•ng shareCount cá»§a cÃ¡c participants.
                    2. Sá»‘ tiá»n má»—i suáº¥t = amount / tá»•ng sá»‘ suáº¥t.
                    3. Náº¿u Payer (NgÆ°á»i tráº£) lÃ  'TÃ´i':
                       - Báº¡n bÃ¨ trong 'participants' ná»£ 'TÃ´i' sá»‘ tiá»n = (sá»‘ suáº¥t cá»§a há») * (sá»‘ tiá»n má»—i suáº¥t).
                       - Náº¿u isSettled = true: há» Ä‘Ã£ tráº£ rá»“i (ghi nháº­n lÃ  'ÄÃ£ thanh toÃ¡n').
                       - Náº¿u isSettled = false: há» váº«n cÃ²n ná»£ (ghi nháº­n lÃ  'ChÆ°a thanh toÃ¡n').
                    4. Náº¿u Payer lÃ  má»™t ngÆ°á»i báº¡n (vÃ­ dá»¥ 'Nguyá»…n VÄƒn B'):
                       - 'TÃ´i' ná»£ 'Nguyá»…n VÄƒn B' sá»‘ tiá»n = myShareCount * (sá»‘ tiá»n má»—i suáº¥t).
                       - Náº¿u isSettled = true: tÃ´i Ä‘Ã£ tráº£ rá»“i.
                       - Náº¿u isSettled = false: tÃ´i váº«n cÃ²n ná»£ báº¡n Ä‘Ã³.

                    HÃ£y tráº£ lá»i cÃ¢u há»i cá»§a ngÆ°á»i dÃ¹ng dá»±a trÃªn dá»¯ liá»‡u vÃ  phong cÃ¡ch trÃªn. Náº¿u ngÆ°á»i dÃ¹ng chá»‰ chÃ o há»i, hÃ£y giá»›i thiá»‡u báº£n thÃ¢n má»™t cÃ¡ch dá»… thÆ°Æ¡ng.
                """.trimIndent())

                val reply = assistant.chatWithContext(messageText, summary.toString())

                // LÆ°u pháº£n há»“i cá»§a bot
                chatDao.insertMessage(ChatMessageEntity(isUser = false, text = reply))
                _uiState.update { it.copy(isSending = false) }
            } catch (t: Throwable) {
                chatDao.insertMessage(
                    ChatMessageEntity(
                        isUser = false,
                        text = "Tinh nang AI hien khong kha dung do xung dot thu vien Ktor/Gemini. ${t.message ?: ""}".trim()
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
