package com.wallet.manager.ui.screen.friends

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import com.wallet.manager.R
import com.wallet.manager.data.local.db.FriendWithSpending
import com.wallet.manager.viewmodel.FriendsViewModel

private fun formatFriendCurrency(amount: Double): String = "%,.0f VND".format(amount)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(
    onOpenDrawer: () -> Unit,
    vm: FriendsViewModel = viewModel(factory = FriendsViewModel.Factory)
) {
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) vm.syncContacts()
    }

    var detailFriendId by remember { mutableStateOf<Long?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.friends_title)) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.READ_CONTACTS
                                ) == PackageManager.PERMISSION_GRANTED
                            ) {
                                vm.syncContacts()
                            } else {
                                permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                            }
                        }
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = stringResource(R.string.sync_contacts))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = vm::openAddSheet,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = null)
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            if (state.friends.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    ElevatedCard(
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.friend_list_empty),
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.friends, key = { it.friend.id }) { friendWithSpending ->
                        FriendItem(
                            friendWithSpending = friendWithSpending,
                            onDelete = { vm.deleteFriend(friendWithSpending.friend) },
                            onEdit = { vm.openEditSheet(friendWithSpending.friend) },
                            onViewDetail = { detailFriendId = friendWithSpending.friend.id }
                        )
                    }
                }
            }

            if (state.isAddSheetOpen) {
                ModalBottomSheet(
                    onDismissRequest = vm::closeAddSheet,
                    sheetState = sheetState
                ) {
                    AddFriendForm(
                        name = state.nameInput,
                        phone = state.phoneInput,
                        imageUri = state.imageUriInput,
                        onNameChange = vm::onNameChange,
                        onPhoneChange = vm::onPhoneChange,
                        onImageChange = vm::onImageChange,
                        onSave = vm::saveFriend,
                        onCancel = vm::closeAddSheet
                    )
                }
            }

            detailFriendId?.let { id ->
                FriendDebtDetailDialog(
                    friendId = id,
                    vm = vm,
                    onDismiss = { detailFriendId = null }
                )
            }
        }
    }
}

@Composable
fun FriendDebtDetailDialog(
    friendId: Long,
    vm: FriendsViewModel,
    onDismiss: () -> Unit
) {
    val expenses by vm.getExpensesByFriend(friendId).collectAsState(initial = emptyList())
    val friendWithSpending = vm.uiState.collectAsState().value.friends.find { it.friend.id == friendId }
    val friendName = friendWithSpending?.friend?.name ?: stringResource(R.string.friends_title)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.friend_detail_title, friendName)) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        },
        text = {
            if (expenses.isEmpty()) {
                Text(stringResource(R.string.no_related_expenses))
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    items(expenses) { item ->
                        val expense = item.expense
                        val crossRef = item.friendCrossRefs.find { it.friendId == friendId }
                        val isSettled = crossRef?.isSettled ?: false
                        val totalShares = expense.myShareCount + item.friendCrossRefs.sumOf { it.shareCount }
                        val shareAmount = if (totalShares > 0) expense.amount / totalShares else 0.0
                        val friendShare = shareAmount * (crossRef?.shareCount ?: 0)
                        val iAmPayer = expense.payerId == null
                        val friendIsPayer = expense.payerId == friendId

                        if (iAmPayer || friendIsPayer) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSettled) {
                                        MaterialTheme.colorScheme.surfaceContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                    }
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(expense.title, style = MaterialTheme.typography.titleSmall)
                                        val statusText = if (iAmPayer) {
                                            stringResource(R.string.friend_owes_me)
                                        } else {
                                            stringResource(R.string.i_owe_them)
                                        }
                                        val amountText = if (iAmPayer) friendShare else shareAmount * expense.myShareCount
                                        val amountColor = if (iAmPayer) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
                                        Text(
                                            text = "${formatFriendCurrency(amountText)} ($statusText)",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isSettled) MaterialTheme.colorScheme.outline else amountColor
                                        )
                                    }

                                    Checkbox(
                                        checked = isSettled,
                                        onCheckedChange = { checked ->
                                            vm.settleExpense(expense.id, friendId, checked)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}

@Composable
fun FriendItem(
    friendWithSpending: FriendWithSpending,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onViewDetail: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onViewDetail),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FriendAvatar(
                uri = friendWithSpending.friend.imageUri,
                name = friendWithSpending.friend.name,
                size = 52.dp
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(friendWithSpending.friend.name, style = MaterialTheme.typography.titleMedium)
                friendWithSpending.friend.phoneNumber?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(
                        R.string.total_spent_with,
                        formatFriendCurrency(friendWithSpending.totalSpent ?: 0.0)
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Row {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.delete)) },
            text = { Text(stringResource(R.string.confirm_delete_friend)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteConfirm = false
                    }
                ) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
fun FriendAvatar(uri: String?, name: String, size: Dp) {
    if (uri != null) {
        Image(
            painter = rememberAsyncImagePainter(uri),
            contentDescription = null,
            modifier = Modifier
                .size(size)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = name.take(1).uppercase(),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
fun AddFriendForm(
    name: String,
    phone: String,
    imageUri: String?,
    onNameChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onImageChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            uri?.let { onImageChange(it.toString()) }
        }
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.add_friend),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            if (imageUri != null) {
                Image(
                    painter = rememberAsyncImagePainter(imageUri),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    Icons.Default.AddAPhoto,
                    contentDescription = stringResource(R.string.tap_to_add_photo),
                    modifier = Modifier.size(48.dp)
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text(stringResource(R.string.friend_name)) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = phone,
            onValueChange = onPhoneChange,
            label = { Text(stringResource(R.string.friend_phone)) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.cancel))
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onSave,
                enabled = name.isNotBlank()
            ) {
                Text(stringResource(R.string.save))
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}
