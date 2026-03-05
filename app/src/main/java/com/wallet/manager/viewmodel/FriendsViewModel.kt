package com.wallet.manager.viewmodel

import android.app.Application
import android.provider.ContactsContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.wallet.manager.data.local.db.AppDatabase
import com.wallet.manager.data.local.db.FriendWithSpending
import com.wallet.manager.data.local.db.ExpenseWithFriends
import com.wallet.manager.data.local.entity.Friend
import com.wallet.manager.data.repository.FriendRepository
import com.wallet.manager.data.repository.FriendRepositoryImpl
import com.wallet.manager.data.repository.ExpenseRepository
import com.wallet.manager.data.repository.ExpenseRepositoryImpl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FriendsUiState(
    val friends: List<FriendWithSpending> = emptyList(),
    val isAddSheetOpen: Boolean = false,
    val nameInput: String = "",
    val phoneInput: String = "",
    val imageUriInput: String? = null,
    val editingFriendId: Long? = null,
    val isSyncing: Boolean = false
)

class FriendsViewModel(
    application: Application,
    private val repo: FriendRepository,
    private val expenseRepo: ExpenseRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(FriendsUiState())
    val uiState: StateFlow<FriendsUiState> = combine(
        repo.getFriendsWithSpending(),
        _uiState
    ) { friendsWithSpending, currentState ->
        currentState.copy(friends = friendsWithSpending)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = FriendsUiState()
    )

    fun onNameChange(name: String) {
        _uiState.update { it.copy(nameInput = name) }
    }

    fun onPhoneChange(phone: String) {
        _uiState.update { it.copy(phoneInput = phone) }
    }

    fun onImageChange(uri: String?) {
        _uiState.update { it.copy(imageUriInput = uri) }
    }

    fun openAddSheet() {
        _uiState.update { it.copy(isAddSheetOpen = true, editingFriendId = null, nameInput = "", phoneInput = "", imageUriInput = null) }
    }
    
    fun openEditSheet(friend: Friend) {
        _uiState.update { 
            it.copy(
                isAddSheetOpen = true, 
                editingFriendId = friend.id, 
                nameInput = friend.name, 
                phoneInput = friend.phoneNumber ?: "", 
                imageUriInput = friend.imageUri
            ) 
        }
    }

    fun closeAddSheet() {
        _uiState.update { it.copy(isAddSheetOpen = false) }
    }

    fun saveFriend() {
        val state = _uiState.value
        if (state.nameInput.isBlank()) return

        viewModelScope.launch {
            val friend = Friend(
                id = state.editingFriendId ?: 0L,
                name = state.nameInput,
                phoneNumber = state.phoneInput,
                imageUri = state.imageUriInput
            )
            if (state.editingFriendId == null) {
                repo.addFriend(friend)
            } else {
                repo.updateFriend(friend)
            }
            closeAddSheet()
        }
    }

    fun deleteFriend(friend: Friend) {
        viewModelScope.launch {
            repo.deleteFriend(friend)
        }
    }

    fun syncContacts() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true) }
            val resolver = getApplication<Application>().contentResolver
            val cursor = resolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                null, null, null
            )

            cursor?.use {
                val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val phoneIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                while (it.moveToNext()) {
                    val name = it.getString(nameIdx)
                    val phone = it.getString(phoneIdx).replace("\\s".toRegex(), "")
                    
                    val existing = repo.getFriendByPhone(phone)
                    if (existing == null) {
                        repo.addFriend(Friend(name = name, phoneNumber = phone))
                    }
                }
            }
            _uiState.update { it.copy(isSyncing = false) }
        }
    }

    fun getExpensesByFriend(friendId: Long): Flow<List<ExpenseWithFriends>> {
        return repo.getExpensesByFriend(friendId)
    }

    fun settleExpense(expenseId: Long, friendId: Long, isSettled: Boolean) {
        viewModelScope.launch {
            expenseRepo.settleExpenseForFriend(expenseId, friendId, isSettled)
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as? Application
                    ?: throw IllegalArgumentException("Application context missing")
                val db = AppDatabase.get(application)
                val repo = FriendRepositoryImpl(db.friendDao(), db.expenseDao())
                val expenseRepo = ExpenseRepositoryImpl(db.expenseDao())
                FriendsViewModel(application, repo, expenseRepo)
            }
        }
    }
}
