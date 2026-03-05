package com.wallet.manager.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.wallet.manager.data.local.db.AppDatabase
import com.wallet.manager.data.local.db.ExpenseWithFriends
import com.wallet.manager.data.local.db.TypeTotalProjection
import com.wallet.manager.data.repository.ExpenseRepository
import com.wallet.manager.data.repository.ExpenseRepositoryImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class StatsFilter { DAY, WEEK, MONTH, YEAR, CUSTOM }

data class FriendDebt(
    val friendName: String,
    val netAmount: Double // Positive means they owe me, negative means I owe them
)

data class DailySpending(
    val dateLabel: String,
    val amount: Double
)

data class StatsUiState(
    val filter: StatsFilter = StatsFilter.MONTH,
    val customStartDate: Long = System.currentTimeMillis(),
    val customEndDate: Long = System.currentTimeMillis(),
    val expenses: List<ExpenseWithFriends> = emptyList(),
    val totalByType: List<TypeTotalProjection> = emptyList(),
    val totalSpentByMe: Double = 0.0,
    val totalOwedToMe: Double = 0.0,
    val totalIOweOthers: Double = 0.0,
    val friendDebts: List<FriendDebt> = emptyList(),
    val dailySpending: List<DailySpending> = emptyList(),
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
        
        combine(
            repo.getExpensesWithFriendsInRange(from, to),
            repo.getTotalByType(from, to)
        ) { expenses, byType ->
            var mySpending = 0.0
            val debtMap = mutableMapOf<Long, Double>() // FriendId -> netAmount
            val friendNames = mutableMapOf<Long, String>()
            val dailyMap = mutableMapOf<String, Double>()
            val dateFormat = SimpleDateFormat("dd/MM", Locale.getDefault())

            expenses.forEach { item ->
                val e = item.expense
                val friendCrossRefs = item.friendCrossRefs
                val totalShares = e.myShareCount + friendCrossRefs.sumOf { it.shareCount }
                val dateLabel = dateFormat.format(Date(e.date))
                
                var currentItemMyShare = 0.0

                if (e.isSplit && totalShares > 0) {
                    val shareAmount = e.amount / totalShares
                    currentItemMyShare = shareAmount * e.myShareCount
                    mySpending += currentItemMyShare

                    // logic nợ: tính dựa trên trạng thái isSettled của từng người bạn
                    if (e.payerId == null) { // Tôi trả
                        item.friends.forEach { friend ->
                            val crossRef = friendCrossRefs.find { it.friendId == friend.id }
                            if (crossRef != null && !crossRef.isSettled) {
                                val amountOwed = shareAmount * crossRef.shareCount
                                debtMap[friend.id] = (debtMap[friend.id] ?: 0.0) + amountOwed
                                friendNames[friend.id] = friend.name
                            }
                        }
                    } else { // Bạn trả
                        val crossRefForMe = friendCrossRefs.find { it.friendId == e.payerId }
                        // Ở đây logic nợ là "Tôi nợ người trả tiền". 
                        // Trạng thái settled này thường được đánh dấu từ phía tôi trả cho họ.
                        val isPaidToFriend = crossRefForMe?.isSettled ?: false
                        
                        if (!isPaidToFriend) {
                            debtMap[e.payerId] = (debtMap[e.payerId] ?: 0.0) - currentItemMyShare
                            val payer = item.friends.find { it.id == e.payerId }
                            if (payer != null) friendNames[payer.id] = payer.name
                        }
                    }
                } else if (!e.isSplit) {
                    if (e.payerId == null) {
                        currentItemMyShare = e.amount
                        mySpending += currentItemMyShare
                    }
                }
                
                if (currentItemMyShare > 0) {
                    dailyMap[dateLabel] = (dailyMap[dateLabel] ?: 0.0) + currentItemMyShare
                }
            }

            val netOwedToMe = debtMap.values.filter { it > 0 }.sum()
            val netIOweOthers = debtMap.values.filter { it < 0 }.sum().let { Math.abs(it) }
            
            val friendDebtList = debtMap.map { (id, amount) ->
                FriendDebt(friendNames[id] ?: "Unknown", amount)
            }.sortedByDescending { Math.abs(it.netAmount) }

            val sortedDaily = dailyMap.map { DailySpending(it.key, it.value) }.reversed()

            val days = ((to - from) / (1000 * 60 * 60 * 24)).coerceAtLeast(1)
            state.copy(
                expenses = expenses,
                totalByType = byType,
                totalSpentByMe = mySpending,
                totalOwedToMe = netOwedToMe,
                totalIOweOthers = netIOweOthers,
                friendDebts = friendDebtList,
                dailySpending = sortedDaily,
                maxExpense = expenses.maxByOrNull { it.expense.amount },
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
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        val end = cal.timeInMillis
        
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        
        val start = when (this) {
            StatsFilter.DAY -> cal.timeInMillis
            StatsFilter.WEEK -> {
                cal.add(Calendar.DAY_OF_YEAR, -7)
                cal.timeInMillis
            }
            StatsFilter.MONTH -> {
                cal.add(Calendar.DAY_OF_YEAR, -30)
                cal.timeInMillis
            }
            StatsFilter.YEAR -> {
                cal.add(Calendar.YEAR, -1)
                cal.timeInMillis
            }
            StatsFilter.CUSTOM -> 0L // Handled outside
        }
        return start to end
    }

    companion object {
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
