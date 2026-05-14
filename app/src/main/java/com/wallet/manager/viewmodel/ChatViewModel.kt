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
import com.wallet.manager.data.local.entity.TRANSACTION_KIND_EXPENSE
import com.wallet.manager.data.local.entity.TRANSACTION_KIND_INCOME
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
                val nowMillis = System.currentTimeMillis()

                val transactionsJson = JSONArray()
                val expensesJson = JSONArray()
                val incomesJson = JSONArray()
                val splitTransactionsJson = JSONArray()
                val debtItemsJson = JSONArray()
                var totalExpense = 0.0
                var totalIncome = 0.0
                var totalFriendsOweMe = 0.0
                var totalIOweFriends = 0.0
                val expenseByCategory = mutableMapOf<String, Double>()
                val incomeByCategory = mutableMapOf<String, Double>()

                expensesWithFriends.forEach { item ->
                    val expense = item.expense
                    val obj = JSONObject()
                    obj.put("id", expense.id)
                    obj.put("transactionKind", expense.transactionKind)
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
                    transactionsJson.put(obj)

                    if (expense.transactionKind == TRANSACTION_KIND_INCOME) {
                        totalIncome += expense.amount
                        incomeByCategory[expense.type] = (incomeByCategory[expense.type] ?: 0.0) + expense.amount
                        incomesJson.put(obj)
                    } else {
                        totalExpense += expense.amount
                        expenseByCategory[expense.type] = (expenseByCategory[expense.type] ?: 0.0) + expense.amount
                        expensesJson.put(obj)
                    }

                    if (expense.isSplit && expense.transactionKind == TRANSACTION_KIND_EXPENSE) {
                        splitTransactionsJson.put(obj)
                        val totalShares = expense.myShareCount + item.friendCrossRefs.sumOf { it.shareCount }
                        if (totalShares > 0) {
                            val amountPerShare = expense.amount / totalShares
                            if (expense.payerId == null) {
                                item.friendCrossRefs
                                    .filter { ref -> ref.shareCount > 0 && !ref.isSettled }
                                    .forEach { ref ->
                                        val friendName = friendsMap[ref.friendId]?.name ?: "Người lạ"
                                        val owedAmount = amountPerShare * ref.shareCount
                                        totalFriendsOweMe += owedAmount
                                        debtItemsJson.put(
                                            JSONObject()
                                                .put("direction", "friend_owes_me")
                                                .put("person", friendName)
                                                .put("expenseTitle", expense.title)
                                                .put("amount", owedAmount)
                                                .put("dateLabel", dateFormatter.format(Date(expense.date)))
                                                .put("isSettled", false)
                                        )
                                    }
                            } else if (!expense.isSettled && expense.myShareCount > 0) {
                                val payerName = friendsMap[expense.payerId]?.name ?: "Người lạ"
                                val owedAmount = amountPerShare * expense.myShareCount
                                totalIOweFriends += owedAmount
                                debtItemsJson.put(
                                    JSONObject()
                                        .put("direction", "i_owe_friend")
                                        .put("person", payerName)
                                        .put("expenseTitle", expense.title)
                                        .put("amount", owedAmount)
                                        .put("dateLabel", dateFormatter.format(Date(expense.date)))
                                        .put("isSettled", false)
                                )
                            }
                        }
                    }
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
                summary.put(
                    "summary",
                    JSONObject()
                        .put("generatedAt", dateFormatter.format(Date(nowMillis)))
                        .put("totalExpense", totalExpense)
                        .put("totalIncome", totalIncome)
                        .put("netCashFlow", totalIncome - totalExpense)
                        .put("transactionCount", transactionsJson.length())
                        .put("expenseCount", expensesJson.length())
                        .put("incomeCount", incomesJson.length())
                        .put("expenseByCategory", expenseByCategory.toJsonArray())
                        .put("incomeByCategory", incomeByCategory.toJsonArray())
                )
                summary.put(
                    "debt_split_summary",
                    JSONObject()
                        .put("friendsOweMeTotal", totalFriendsOweMe)
                        .put("iOweFriendsTotal", totalIOweFriends)
                        .put("netDebtPosition", totalFriendsOweMe - totalIOweFriends)
                        .put("openDebtItems", debtItemsJson)
                )
                summary.put("transactions", transactionsJson)
                summary.put("expenses", expensesJson)
                summary.put("incomes", incomesJson)
                summary.put("split_transactions", splitTransactionsJson)
                summary.put("credit_cards", creditCardsJson)
                summary.put("credit_card_transactions", creditCardTransactionsJson)
                summary.put(
                    "instruction",
                    """
                    Dữ liệu đã được chia thành các nhóm:
                    1. summary: tổng thu, tổng chi, chênh lệch và tổng theo danh mục
                    2. transactions: toàn bộ giao dịch
                    3. expenses: chỉ giao dịch chi tiêu
                    4. incomes: chỉ giao dịch thu nhập
                    5. debt_split_summary: các khoản nợ/chia tiền còn mở đã tính sẵn
                    6. split_transactions: giao dịch có chia tiền
                    7. credit_cards: thông tin từng thẻ tín dụng và tổng tiền theo kỳ
                    8. credit_card_transactions: các giao dịch có gắn thẻ tín dụng

                    Khi câu hỏi nói về "tín dụng", "thẻ tín dụng", "sao kê", "giao dịch thẻ", "chi tiêu bằng thẻ":
                    - Chỉ đọc credit_cards và credit_card_transactions.
                    - Không kéo sang phân tích nợ/chia tiền nếu người dùng không hỏi.

                    Khi câu hỏi nói về "nợ", "chia tiền", "ai nợ ai":
                    - Ưu tiên đọc debt_split_summary.
                    - Có thể dùng split_transactions để giải thích chi tiết từng khoản.
                    - Không kéo sang thẻ tín dụng nếu người dùng không hỏi.

                    Khi câu hỏi nói về thu nhập:
                    - Chỉ đọc incomes và summary.incomeByCategory.

                    Khi câu hỏi nói về chi tiêu:
                    - Chỉ đọc expenses và summary.expenseByCategory.
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

    private fun Map<String, Double>.toJsonArray(): JSONArray {
        val result = JSONArray()
        entries
            .sortedByDescending { it.value }
            .forEach { (category, amount) ->
                result.put(
                    JSONObject()
                        .put("category", category)
                        .put("amount", amount)
                )
            }
        return result
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
