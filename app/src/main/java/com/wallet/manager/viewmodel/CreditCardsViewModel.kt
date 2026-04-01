package com.wallet.manager.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.wallet.manager.data.local.db.AppDatabase
import com.wallet.manager.data.local.entity.CreditCard
import com.wallet.manager.data.local.entity.Expense
import com.wallet.manager.data.repository.CreditCardRepository
import com.wallet.manager.data.repository.CreditCardRepositoryImpl
import com.wallet.manager.data.repository.ExpenseRepository
import com.wallet.manager.data.repository.ExpenseRepositoryImpl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class CreditCardSummary(
    val card: CreditCard,
    val dueAmount: Double,
    val currentCycleAmount: Double,
    val isDue: Boolean,
    val latestStatementLabel: String,
    val nextStatementLabel: String,
    val recentExpenses: List<Expense>
)

data class CreditCardsUiState(
    val cards: List<CreditCardSummary> = emptyList(),
    val isAddSheetOpen: Boolean = false,
    val editingCardId: Long? = null,
    val formName: String = "",
    val formHolderName: String = "",
    val formLast4Digits: String = "",
    val formStatementDay: String = "",
    val formImageUri: String? = null,
    val errorMessage: String? = null
)

class CreditCardsViewModel(
    private val cardRepo: CreditCardRepository,
    private val expenseRepo: ExpenseRepository
) : ViewModel() {
    private val formState = MutableStateFlow(CreditCardsUiState())

    val uiState: StateFlow<CreditCardsUiState> = combine(
        cardRepo.getAllCards(),
        expenseRepo.getAllExpenses(),
        formState
    ) { cards, expenses, current ->
        current.copy(cards = cards.map { buildSummary(it, expenses) })
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CreditCardsUiState()
    )

    fun openAddSheet() {
        formState.update {
            it.copy(
                isAddSheetOpen = true,
                editingCardId = null,
                formName = "",
                formHolderName = "",
                formLast4Digits = "",
                formStatementDay = "",
                formImageUri = null,
                errorMessage = null
            )
        }
    }

    fun openEditSheet(card: CreditCard) {
        formState.update {
            it.copy(
                isAddSheetOpen = true,
                editingCardId = card.id,
                formName = card.name,
                formHolderName = card.holderName,
                formLast4Digits = card.last4Digits,
                formStatementDay = card.statementDay.toString(),
                formImageUri = card.imageUri,
                errorMessage = null
            )
        }
    }

    fun closeAddSheet() {
        formState.update { it.copy(isAddSheetOpen = false, errorMessage = null) }
    }

    fun onNameChange(value: String) {
        formState.update { it.copy(formName = value, errorMessage = null) }
    }

    fun onHolderNameChange(value: String) {
        formState.update { it.copy(formHolderName = value, errorMessage = null) }
    }

    fun onLast4DigitsChange(value: String) {
        if (value.all(Char::isDigit) && value.length <= 4) {
            formState.update { it.copy(formLast4Digits = value, errorMessage = null) }
        }
    }

    fun onStatementDayChange(value: String) {
        if (value.all(Char::isDigit) && value.length <= 2) {
            formState.update { it.copy(formStatementDay = value, errorMessage = null) }
        }
    }

    fun onImageChange(value: String) {
        formState.update { it.copy(formImageUri = value, errorMessage = null) }
    }

    fun saveCard() {
        val state = formState.value
        val statementDay = state.formStatementDay.toIntOrNull()
        if (state.formName.isBlank() || state.formLast4Digits.length != 4 || statementDay == null || statementDay !in 1..28) {
            formState.update { it.copy(errorMessage = "Vui lòng nhập đủ tên thẻ, 4 số cuối và ngày quyết toán từ 1 đến 28.") }
            return
        }

        viewModelScope.launch {
            cardRepo.saveCard(
                CreditCard(
                    id = state.editingCardId ?: 0L,
                    name = state.formName.trim(),
                    holderName = state.formHolderName.trim(),
                    last4Digits = state.formLast4Digits,
                    statementDay = statementDay,
                    imageUri = state.formImageUri
                )
            )
            closeAddSheet()
        }
    }

    fun deleteCard(card: CreditCard) {
        viewModelScope.launch {
            cardRepo.deleteCard(card)
        }
    }

    private fun buildSummary(card: CreditCard, allExpenses: List<Expense>): CreditCardSummary {
        val cardExpenses = allExpenses
            .filter { it.creditCardId == card.id }
            .sortedByDescending { it.date }

        val now = Calendar.getInstance()
        val latestStatement = statementCalendar(card.statementDay, now, isLatest = true)
        val previousStatement = statementCalendar(card.statementDay, latestStatement, monthsDelta = -1)
        val nextStatement = statementCalendar(card.statementDay, now, isLatest = false)
        val isDue = latestStatement.get(Calendar.MONTH) == now.get(Calendar.MONTH) &&
            latestStatement.get(Calendar.YEAR) == now.get(Calendar.YEAR)

        val dueAmount = if (isDue) {
            cardExpenses
                .filter { it.date > previousStatement.timeInMillis && it.date <= latestStatement.timeInMillis }
                .sumOf { it.amount }
        } else {
            0.0
        }

        val currentCycleAmount = cardExpenses
            .filter { it.date > latestStatement.timeInMillis && it.date <= now.timeInMillis }
            .sumOf { it.amount }

        val formatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        return CreditCardSummary(
            card = card,
            dueAmount = dueAmount,
            currentCycleAmount = currentCycleAmount,
            isDue = isDue,
            latestStatementLabel = formatter.format(Date(latestStatement.timeInMillis)),
            nextStatementLabel = formatter.format(Date(nextStatement.timeInMillis)),
            recentExpenses = cardExpenses.take(5)
        )
    }

    private fun statementCalendar(
        statementDay: Int,
        reference: Calendar,
        isLatest: Boolean? = null,
        monthsDelta: Int = 0
    ): Calendar {
        val cal = (reference.clone() as Calendar).apply {
            add(Calendar.MONTH, monthsDelta)
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
            val maxDay = getActualMaximum(Calendar.DAY_OF_MONTH)
            set(Calendar.DAY_OF_MONTH, statementDay.coerceAtMost(maxDay))
        }

        if (isLatest == true && cal.after(reference)) {
            cal.add(Calendar.MONTH, -1)
            val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
            cal.set(Calendar.DAY_OF_MONTH, statementDay.coerceAtMost(maxDay))
        }
        if (isLatest == false && !cal.after(reference)) {
            cal.add(Calendar.MONTH, 1)
            val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
            cal.set(Calendar.DAY_OF_MONTH, statementDay.coerceAtMost(maxDay))
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
                CreditCardsViewModel(
                    cardRepo = CreditCardRepositoryImpl(db.creditCardDao()),
                    expenseRepo = ExpenseRepositoryImpl(db.expenseDao())
                )
            }
        }
    }
}
