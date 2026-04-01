package com.wallet.manager.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.wallet.manager.R
import com.wallet.manager.ai.GeminiBillParser
import com.wallet.manager.data.local.db.AppDatabase
import com.wallet.manager.data.local.db.ExpenseWithFriends
import com.wallet.manager.data.local.entity.Expense
import com.wallet.manager.data.local.entity.Friend
import com.wallet.manager.data.repository.ExpenseRepository
import com.wallet.manager.data.repository.ExpenseRepositoryImpl
import com.wallet.manager.data.repository.FriendRepository
import com.wallet.manager.data.repository.FriendRepositoryImpl
import com.wallet.manager.data.secure.SecurePrefsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val expenses: List<ExpenseWithFriends> = emptyList(),
    val filteredExpenses: List<ExpenseWithFriends> = emptyList(),
    val isBottomSheetOpen: Boolean = false,
    val isBillLoading: Boolean = false,
    val manualType: String = "Ăn uống",
    val manualTitle: String = "",
    val manualContent: String = "",
    val manualAmount: String = "",
    val manualDateMillis: Long = System.currentTimeMillis(),
    val billImageUri: String? = null,
    val detailDialogExpense: ExpenseWithFriends? = null,
    val editingExpenseId: Long? = null,
    val searchQuery: String = "",
    val selectedFilterTypeResId: Int? = null, // null means "All"
    val pendingDeleteExpense: Expense? = null,
    
    // Friend related fields in form
    val allFriends: List<Friend> = emptyList(),
    val selectedFriendShares: Map<Long, Int> = emptyMap(), // FriendId -> shareCount
    val myShareCount: Int = 1,
    val isSplit: Boolean = false,
    val payerId: Long? = null, // null is "Me"
    val isSettled: Boolean = false,
    val errorMessage: String? = null
)

class HomeViewModel(
    application: Application,
    private val repo: ExpenseRepository,
    private val friendRepo: FriendRepository,
    private val billParser: GeminiBillParser?
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(HomeUiState())
    
    val uiState: StateFlow<HomeUiState> = combine(
        repo.getAllExpensesWithFriends(),
        friendRepo.getAllFriends(),
        _uiState
    ) { allExpenses, allFriends, currentState ->
        val filtered = allExpenses.filter { item ->
            val expense = item.expense
            val matchesSearch = expense.title.contains(currentState.searchQuery, ignoreCase = true) ||
                    expense.content.contains(currentState.searchQuery, ignoreCase = true)
            
            val matchesFilter = if (currentState.selectedFilterTypeResId == null) {
                true
            } else {
                val resId = currentState.selectedFilterTypeResId
                // Compare with current localized string AND legacy/alternative localized strings
                isCategoryMatch(expense.type, resId)
            }
            matchesSearch && matchesFilter
        }
        currentState.copy(
            expenses = allExpenses,
            filteredExpenses = filtered,
            allFriends = allFriends
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState()
    )

    fun isCategoryMatch(type: String?, resId: Int): Boolean {
        if (type == null) return false
        return type == getApplication<Application>().getString(resId) || isLegacyMatch(type, resId)
    }

    private fun isLegacyMatch(type: String, resId: Int): Boolean {
        return when (resId) {
            R.string.cat_food -> type == "Ăn uống" || type == "Food & Drinks"
            R.string.cat_transport -> type == "Di chuyển" || type == "Transport"
            R.string.cat_shopping -> type == "Mua sắm" || type == "Shopping"
            R.string.cat_entertainment -> type == "Giải trí" || type == "Entertainment"
            R.string.cat_study -> type == "Học tập" || type == "Study"
            R.string.cat_other -> type == "Khác" || type == "Other"
            else -> false
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun onFilterTypeChange(typeResId: Int?) {
        _uiState.update { it.copy(selectedFilterTypeResId = typeResId) }
    }

    fun onFabClicked() {
        _uiState.update { 
            it.copy(
                isBottomSheetOpen = true,
                editingExpenseId = null,
                manualType = getApplication<Application>().getString(R.string.cat_food),
                manualTitle = "",
                manualContent = "",
                manualAmount = "",
                manualDateMillis = System.currentTimeMillis(),
                billImageUri = null,
                selectedFriendShares = emptyMap(),
                myShareCount = 1,
                isSplit = false,
                payerId = null,
                isSettled = false,
                errorMessage = null
            ) 
        }
    }

    fun onEditClicked(item: ExpenseWithFriends) {
        val expense = item.expense
        val friendShares = item.friendCrossRefs.associate { it.friendId to it.shareCount }
        val allSettled = item.friendCrossRefs.all { it.isSettled }
        _uiState.update {
            it.copy(
                isBottomSheetOpen = true,
                editingExpenseId = expense.id,
                manualType = expense.type,
                manualTitle = expense.title,
                manualContent = expense.content,
                manualAmount = expense.amount.toLong().toString(),
                manualDateMillis = expense.date,
                billImageUri = expense.imageUri,
                selectedFriendShares = friendShares,
                myShareCount = expense.myShareCount,
                isSplit = expense.isSplit,
                payerId = expense.payerId,
                isSettled = allSettled,
                errorMessage = null
            )
        }
    }

    fun closeBottomSheet() {
        _uiState.update { it.copy(isBottomSheetOpen = false) }
    }

    fun onManualFieldChange(
        type: String? = null,
        title: String? = null,
        content: String? = null,
        amount: String? = null,
        dateMillis: Long? = null,
        isSplit: Boolean? = null,
        payerId: Long? = null,
        isMePayer: Boolean? = null,
        isSettled: Boolean? = null,
        myShareCount: Int? = null,
        billImageUri: String? = null,
        clearBillImage: Boolean = false
    ) {
        _uiState.update {
            it.copy(
                manualType = type ?: it.manualType,
                manualTitle = title ?: it.manualTitle,
                manualContent = content ?: it.manualContent,
                manualAmount = amount ?: it.manualAmount,
                manualDateMillis = dateMillis ?: it.manualDateMillis,
                isSplit = isSplit ?: it.isSplit,
                payerId = if (isMePayer == true) null else (payerId ?: it.payerId),
                isSettled = isSettled ?: it.isSettled,
                myShareCount = myShareCount ?: it.myShareCount,
                billImageUri = if (clearBillImage) null else (billImageUri ?: it.billImageUri),
                errorMessage = null
            )
        }
    }

    fun toggleFriendSelection(friendId: Long) {
        _uiState.update { 
            val currentShares = it.selectedFriendShares.toMutableMap()
            if (currentShares.containsKey(friendId)) {
                currentShares.remove(friendId)
            } else {
                currentShares[friendId] = 0 // Khởi tạo bằng 0 thay vì 1
            }
            it.copy(selectedFriendShares = currentShares, errorMessage = null)
        }
    }

    fun updateFriendShare(friendId: Long, delta: Int) {
        _uiState.update {
            val currentShares = it.selectedFriendShares.toMutableMap()
            val current = currentShares[friendId] ?: 0
            val next = (current + delta).coerceAtLeast(0)
            currentShares[friendId] = next
            it.copy(selectedFriendShares = currentShares, errorMessage = null)
        }
    }

    fun saveExpense(imageUri: String?) {
        val state = _uiState.value
        val amount = state.manualAmount.toDoubleOrNull() ?: return
        
        viewModelScope.launch {
            val validShares = state.selectedFriendShares
            val isSplit = validShares.isNotEmpty()
            
            val expense = Expense(
                id = state.editingExpenseId ?: 0L,
                type = state.manualType,
                title = state.manualTitle,
                content = state.manualContent,
                amount = amount,
                date = state.manualDateMillis,
                imageUri = imageUri,
                createdAt = System.currentTimeMillis(),
                isSplit = isSplit,
                payerId = state.payerId,
                isSettled = state.isSettled,
                myShareCount = state.myShareCount
            )
            
            repo.addExpenseWithFriends(expense, validShares, state.isSettled)
            
            _uiState.update {
                it.copy(
                    isBottomSheetOpen = false,
                    editingExpenseId = null,
                    selectedFriendShares = emptyMap()
                )
            }
        }
    }

    fun confirmDelete(expense: Expense) {
        _uiState.update { it.copy(pendingDeleteExpense = expense) }
    }

    fun cancelDelete() {
        _uiState.update { it.copy(pendingDeleteExpense = null) }
    }

    fun executeDelete() {
        val expense = _uiState.value.pendingDeleteExpense ?: return
        viewModelScope.launch {
            repo.deleteExpense(expense)
            _uiState.update { it.copy(pendingDeleteExpense = null) }
        }
    }

    fun showDetail(item: ExpenseWithFriends) {
        _uiState.update { it.copy(detailDialogExpense = item) }
    }

    fun dismissDetail() {
        _uiState.update { it.copy(detailDialogExpense = null) }
    }

    fun onImagePicked(uri: Uri) {
        viewModelScope.launch {
            try {
                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(getApplication<Application>().contentResolver, uri))
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(getApplication<Application>().contentResolver, uri)
                }
                _uiState.update { it.copy(billImageUri = uri.toString()) }
                
                // Automatically trigger AI parsing
                parseBillFromBitmap(bitmap, uri.toString())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun parseBillFromBitmap(bitmap: Bitmap, imageUri: String?) {
        val parser = billParser ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isBillLoading = true, billImageUri = imageUri) }
            try {
                val parsed = parser.parseBill(bitmap)
                if (parsed != null) {
                    _uiState.update {
                        it.copy(
                            manualType = parsed.type,
                            manualTitle = parsed.title,
                            manualContent = parsed.content,
                            manualAmount = parsed.amount.toLong().toString(),
                            manualDateMillis = parsed.dateMillis,
                            isBillLoading = false
                        )
                    }
                } else {
                    _uiState.update { it.copy(isBillLoading = false) }
                }
            } catch (_: Throwable) {
                _uiState.update {
                    it.copy(
                        isBillLoading = false,
                        errorMessage = "Tinh nang AI hien khong kha dung do xung dot thu vien Ktor/Gemini."
                    )
                }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as? Application
                    ?: throw IllegalArgumentException("Application context missing")
                val db = AppDatabase.get(application)
                val repo = ExpenseRepositoryImpl(db.expenseDao())
                val friendRepo = FriendRepositoryImpl(db.friendDao(), db.expenseDao())
                val securePrefs = SecurePrefsManager.getInstance(application)
                val apiKey = securePrefs.getGeminiApiKey()
                val billParser = try {
                    apiKey?.let { GeminiBillParser(it) }
                } catch (_: Throwable) {
                    null
                }
                HomeViewModel(application, repo, friendRepo, billParser)
            }
        }
    }
}
