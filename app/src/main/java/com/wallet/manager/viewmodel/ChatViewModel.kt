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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    private val secure: SecurePrefsManager,
    private val db: AppDatabase
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
                val allCards = db.creditCardDao().getAllCardsList()
                val friendsMap = allFriends.associateBy { it.id }
                val cardsMap = allCards.associateBy { it.id }
                val dateFormatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

                val expensesJson = JSONArray()
                expensesWithFriends.forEach { item ->
                    val expense = item.expense
                    val obj = JSONObject()
                    obj.put("id", expense.id)
                    obj.put("type", expense.type)
                    obj.put("title", expense.title)
                    obj.put("content", expense.content)
                    obj.put("amount", expense.amount)
                    obj.put("date", expense.date)
                    obj.put("dateLabel", dateFormatter.format(Date(expense.date)))
                    obj.put("isSplit", expense.isSplit)
                    obj.put("myShareCount", expense.myShareCount)
                    obj.put("creditCardId", expense.creditCardId)
                    obj.put("creditCardName", cardsMap[expense.creditCardId]?.name ?: JSONObject.NULL)

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
                    expensesJson.put(obj)
                }

                val creditCardsJson = JSONArray()
                val creditCardTransactionsJson = JSONArray()
                allCards.forEach { card ->
                    val cardExpenses = expensesWithFriends
                        .map { it.expense }
                        .filter { it.creditCardId == card.id }
                        .sortedByDescending { it.date }

                    val now = java.util.Calendar.getInstance()
                    val latestStatement = buildStatementDate(card.statementDay, now, latest = true)
                    val previousStatement = buildStatementDate(card.statementDay, latestStatement, latest = true, monthOffset = -1)
                    val nextStatement = buildStatementDate(card.statementDay, now, latest = false)
                    val dueAmount = cardExpenses
                        .filter { it.date > previousStatement.timeInMillis && it.date <= latestStatement.timeInMillis }
                        .sumOf { it.amount }
                    val currentCycleAmount = cardExpenses
                        .filter { it.date > latestStatement.timeInMillis && it.date <= now.timeInMillis }
                        .sumOf { it.amount }

                    val cardObj = JSONObject()
                    cardObj.put("id", card.id)
                    cardObj.put("name", card.name)
                    cardObj.put("holderName", card.holderName)
                    cardObj.put("last4Digits", card.last4Digits)
                    cardObj.put("statementDay", card.statementDay)
                    cardObj.put("latestStatementDate", dateFormatter.format(Date(latestStatement.timeInMillis)))
                    cardObj.put("nextStatementDate", dateFormatter.format(Date(nextStatement.timeInMillis)))
                    cardObj.put("dueAmount", dueAmount)
                    cardObj.put("currentCycleAmount", currentCycleAmount)
                    creditCardsJson.put(cardObj)

                    cardExpenses.forEach { expense ->
                        val tx = JSONObject()
                        tx.put("creditCardId", card.id)
                        tx.put("creditCardName", card.name)
                        tx.put("title", expense.title)
                        tx.put("amount", expense.amount)
                        tx.put("date", expense.date)
                        tx.put("dateLabel", dateFormatter.format(Date(expense.date)))
                        tx.put("type", expense.type)
                        creditCardTransactionsJson.put(tx)
                    }
                }

                val summary = JSONObject()
                summary.put("expenses", expensesJson)
                summary.put("credit_cards", creditCardsJson)
                summary.put("credit_card_transactions", creditCardTransactionsJson)
                summary.put(
                    "instruction",
                    """
                    Dữ liệu đã được chia thành 3 nhóm:
                    1. expenses: toàn bộ chi tiêu
                    2. credit_cards: thông tin từng thẻ tín dụng và tổng tiền theo kỳ
                    3. credit_card_transactions: các giao dịch có gắn thẻ tín dụng

                    Khi câu hỏi nói về "tín dụng", "thẻ tín dụng", "sao kê", "giao dịch thẻ", "chi tiêu bằng thẻ":
                    - Chỉ đọc credit_cards và credit_card_transactions.
                    - Không kéo sang phân tích nợ/chia tiền nếu người dùng không hỏi.

                    Khi câu hỏi nói về "nợ", "chia tiền", "ai nợ ai":
                    - Chỉ đọc expenses cùng participants/payer/isSettled.
                    - Không kéo sang thẻ tín dụng nếu người dùng không hỏi.
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

    private fun buildStatementDate(
        statementDay: Int,
        reference: java.util.Calendar,
        latest: Boolean,
        monthOffset: Int = 0
    ): java.util.Calendar {
        val cal = (reference.clone() as java.util.Calendar).apply {
            add(java.util.Calendar.MONTH, monthOffset)
            set(java.util.Calendar.HOUR_OF_DAY, 23)
            set(java.util.Calendar.MINUTE, 59)
            set(java.util.Calendar.SECOND, 59)
            set(java.util.Calendar.MILLISECOND, 999)
            val maxDay = getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
            set(java.util.Calendar.DAY_OF_MONTH, statementDay.coerceAtMost(maxDay))
        }
        if (latest && cal.after(reference)) {
            cal.add(java.util.Calendar.MONTH, -1)
            val maxDay = cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
            cal.set(java.util.Calendar.DAY_OF_MONTH, statementDay.coerceAtMost(maxDay))
        }
        if (!latest && !cal.after(reference)) {
            cal.add(java.util.Calendar.MONTH, 1)
            val maxDay = cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
            cal.set(java.util.Calendar.DAY_OF_MONTH, statementDay.coerceAtMost(maxDay))
        }
        return cal
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
                ChatViewModel(repo, friendRepo, chatDao, secure, db)
            }
        }
    }
}
