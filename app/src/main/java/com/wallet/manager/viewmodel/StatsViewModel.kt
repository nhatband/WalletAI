package com.wallet.manager.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.wallet.manager.data.local.db.AppDatabase
import com.wallet.manager.data.local.db.ExpenseWithFriends
import com.wallet.manager.data.local.db.TypeTotalProjection
import com.wallet.manager.data.local.entity.TRANSACTION_KIND_EXPENSE
import com.wallet.manager.data.local.entity.TRANSACTION_KIND_INCOME
import com.wallet.manager.data.repository.ExpenseRepository
import com.wallet.manager.data.repository.ExpenseRepositoryImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.util.Calendar
import java.util.Locale

enum class StatsFilter { DAY, WEEK, MONTH, YEAR, CUSTOM }

data class FriendDebt(
    val friendName: String,
    val netAmount: Double
)

data class DailySpending(
    val dateMillis: Long,
    val dateLabel: String,
    val amount: Double
)

data class StatsUiState(
    val filter: StatsFilter = StatsFilter.MONTH,
    val customStartDate: Long = System.currentTimeMillis(),
    val customEndDate: Long = System.currentTimeMillis(),
    val expenses: List<ExpenseWithFriends> = emptyList(),
    val totalByType: List<TypeTotalProjection> = emptyList(),
    val incomeByType: List<TypeTotalProjection> = emptyList(),
    val totalSpentByMe: Double = 0.0,
    val totalIncome: Double = 0.0,
    val totalOwedToMe: Double = 0.0,
    val totalIOweOthers: Double = 0.0,
    val friendDebts: List<FriendDebt> = emptyList(),
    val dailySpending: List<DailySpending> = emptyList(),
    val dailyIncome: List<DailySpending> = emptyList(),
    val maxExpense: ExpenseWithFriends? = null,
    val avgPerDay: Double = 0.0
)

class StatsViewModel(
    private val repo: ExpenseRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatsUiState())

    val uiState: StateFlow<StatsUiState> = _uiState.flatMapLatest { state ->
        val (from, to) = if (state.filter == StatsFilter.CUSTOM) {
            state.customStartDate to state.customEndDate
        } else {
            state.filter.toRange()
        }

        repo.getExpensesWithFriendsInRange(from, to).map { expenses ->
            var mySpending = 0.0
            val debtMap = mutableMapOf<Long, Double>()
            val friendNames = mutableMapOf<Long, String>()
            val expenseByDay = mutableMapOf<Long, Double>()
            val incomeByDay = mutableMapOf<Long, Double>()
            val expenseItems = expenses.filter { it.expense.transactionKind == TRANSACTION_KIND_EXPENSE }
            val incomeItems = expenses.filter { it.expense.transactionKind == TRANSACTION_KIND_INCOME }

            expenseItems.forEach { item ->
                val e = item.expense
                val friendCrossRefs = item.friendCrossRefs
                val totalShares = e.myShareCount + friendCrossRefs.sumOf { it.shareCount }
                val dayKey = startOfDay(e.date)

                var currentItemMyShare = 0.0

                if (e.isSplit && totalShares > 0) {
                    val shareAmount = e.amount / totalShares
                    currentItemMyShare = shareAmount * e.myShareCount
                    mySpending += currentItemMyShare

                    if (e.payerId == null) {
                        item.friends.forEach { friend ->
                            val crossRef = friendCrossRefs.find { it.friendId == friend.id }
                            if (crossRef != null && !crossRef.isSettled) {
                                val amountOwed = shareAmount * crossRef.shareCount
                                debtMap[friend.id] = (debtMap[friend.id] ?: 0.0) + amountOwed
                                friendNames[friend.id] = friend.name
                            }
                        }
                    } else {
                        val crossRefForMe = friendCrossRefs.find { it.friendId == e.payerId }
                        val isPaidToFriend = crossRefForMe?.isSettled ?: false

                        if (!isPaidToFriend) {
                            debtMap[e.payerId] = (debtMap[e.payerId] ?: 0.0) - currentItemMyShare
                            val payer = item.friends.find { it.id == e.payerId }
                            if (payer != null) friendNames[payer.id] = payer.name
                        }
                    }
                } else if (!e.isSplit && e.payerId == null) {
                    currentItemMyShare = e.amount
                    mySpending += currentItemMyShare
                }

                if (currentItemMyShare > 0) {
                    expenseByDay[dayKey] = (expenseByDay[dayKey] ?: 0.0) + currentItemMyShare
                }
            }

            incomeItems.forEach { item ->
                val e = item.expense
                val dayKey = startOfDay(e.date)
                incomeByDay[dayKey] = (incomeByDay[dayKey] ?: 0.0) + e.amount
            }

            val netOwedToMe = debtMap.values.filter { it > 0 }.sum()
            val netIOweOthers = debtMap.values.filter { it < 0 }.sum().let { kotlin.math.abs(it) }

            val friendDebtList = debtMap.map { (id, amount) ->
                FriendDebt(friendNames[id] ?: "Unknown", amount)
            }.sortedByDescending { kotlin.math.abs(it.netAmount) }

            val dateFormat = java.text.SimpleDateFormat("dd/MM", Locale.getDefault())
            val sortedDaily = expenseByDay.toSortedMap().map { (day, amount) ->
                DailySpending(dateMillis = day, dateLabel = dateFormat.format(day), amount = amount)
            }
            val sortedIncomeDaily = incomeByDay.toSortedMap().map { (day, amount) ->
                DailySpending(dateMillis = day, dateLabel = dateFormat.format(day), amount = amount)
            }

            val expenseByType = expenseItems
                .groupBy { it.expense.type }
                .map { (type, items) -> TypeTotalProjection(type, items.sumOf { it.expense.amount }) }
                .sortedByDescending { it.total }
            val incomeByType = incomeItems
                .groupBy { it.expense.type }
                .map { (type, items) -> TypeTotalProjection(type, items.sumOf { it.expense.amount }) }
                .sortedByDescending { it.total }

            val days = (((to - from) / DAY_IN_MILLIS) + 1).coerceAtLeast(1)
            state.copy(
                expenses = expenses,
                totalByType = expenseByType,
                incomeByType = incomeByType,
                totalSpentByMe = mySpending,
                totalIncome = incomeItems.sumOf { it.expense.amount },
                totalOwedToMe = netOwedToMe,
                totalIOweOthers = netIOweOthers,
                friendDebts = friendDebtList,
                dailySpending = sortedDaily,
                dailyIncome = sortedIncomeDaily,
                maxExpense = expenseItems.maxByOrNull { it.expense.amount },
                avgPerDay = mySpending / days
            )
        }.flowOn(Dispatchers.Default)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = StatsUiState()
    )

    fun setFilter(filter: StatsFilter) {
        _uiState.update { it.copy(filter = filter) }
    }

    fun setCustomRange(start: Long, end: Long) {
        _uiState.update {
            it.copy(
                filter = StatsFilter.CUSTOM,
                customStartDate = start,
                customEndDate = end
            )
        }
    }

    private fun StatsFilter.toRange(): Pair<Long, Long> {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()
        cal.timeInMillis = now
        return when (this) {
            StatsFilter.DAY -> startOfDay(now) to endOfDay(now)
            StatsFilter.WEEK -> {
                val end = endOfDay(now)
                cal.timeInMillis = startOfDay(now)
                cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
                val start = startOfDay(cal.timeInMillis)
                start to end
            }
            StatsFilter.MONTH -> {
                val end = endOfDay(now)
                cal.timeInMillis = startOfDay(now)
                cal.set(Calendar.DAY_OF_MONTH, 1)
                val start = startOfDay(cal.timeInMillis)
                start to end
            }
            StatsFilter.YEAR -> {
                val end = endOfDay(now)
                cal.timeInMillis = startOfDay(now)
                cal.set(Calendar.DAY_OF_YEAR, 1)
                val start = startOfDay(cal.timeInMillis)
                start to end
            }
            StatsFilter.CUSTOM -> 0L to 0L
        }
    }

    private fun startOfDay(timeMillis: Long): Long =
        Calendar.getInstance().apply {
            timeInMillis = timeMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun endOfDay(timeMillis: Long): Long =
        Calendar.getInstance().apply {
            timeInMillis = timeMillis
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis

    companion object {
        private const val DAY_IN_MILLIS = 24L * 60L * 60L * 1000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val appContext =
                    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                        ?: throw IllegalArgumentException("Application context missing")
                val db = AppDatabase.get(appContext)
                val repo: ExpenseRepository = ExpenseRepositoryImpl(db.expenseDao())
                StatsViewModel(repo)
            }
        }
    }
}
