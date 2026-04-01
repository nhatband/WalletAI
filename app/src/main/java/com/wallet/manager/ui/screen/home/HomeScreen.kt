package com.wallet.manager.ui.screen.home

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.wallet.manager.R
import com.wallet.manager.data.local.db.ExpenseWithFriends
import com.wallet.manager.data.local.entity.Expense
import com.wallet.manager.data.local.entity.Friend
import com.wallet.manager.viewmodel.HomeUiState
import com.wallet.manager.viewmodel.HomeViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

private val vnLocale = Locale("vi", "VN")

class ThousandSeparatorVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val originalText = text.text
        if (originalText.isEmpty()) return TransformedText(text, OffsetMapping.Identity)

        val formattedText = StringBuilder()
        for (i in originalText.indices) {
            formattedText.append(originalText[i])
            if ((originalText.length - 1 - i) % 3 == 0 && i != originalText.length - 1) {
                formattedText.append('.')
            }
        }

        val out = formattedText.toString()

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                if (offset <= 0) return 0
                var dots = 0
                for (i in 0 until offset) {
                    if (i < originalText.length - 1 && (originalText.length - 1 - i) % 3 == 0) {
                        dots++
                    }
                }
                return offset + dots
            }

            override fun transformedToOriginal(offset: Int): Int {
                var dots = 0
                for (i in 0 until offset) {
                    if (i < out.length && out[i] == '.') {
                        dots++
                    }
                }
                return (offset - dots).coerceAtLeast(0)
            }
        }

        return TransformedText(AnnotatedString(out), offsetMapping)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenDrawer: () -> Unit,
    vm: HomeViewModel = viewModel(factory = HomeViewModel.Factory)
) {
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.home_title)) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = vm::onFabClicked,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.add_expense))
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            SearchAndFilterHeader(
                searchQuery = state.searchQuery,
                onSearchChange = vm::onSearchQueryChange,
                selectedTypeResId = state.selectedFilterTypeResId,
                onTypeChange = vm::onFilterTypeChange
            )

            if (state.filteredExpenses.isEmpty()) {
                EmptyState()
            } else {
                ExpenseList(
                    items = state.filteredExpenses,
                    onItemClick = vm::showDetail,
                    onEditClick = vm::onEditClicked,
                    onDeleteClick = vm::confirmDelete
                )
            }
        }
    }

    if (state.isBottomSheetOpen) {
        ExpenseBottomSheet(state, vm)
    }

    state.detailDialogExpense?.let { item ->
        ExpenseDetailDialog(item, vm::dismissDetail)
    }

    state.pendingDeleteExpense?.let { expense ->
        AlertDialog(
            onDismissRequest = vm::cancelDelete,
            title = { Text(stringResource(R.string.confirm_delete_title)) },
            text = { Text(stringResource(R.string.confirm_delete_msg)) },
            confirmButton = {
                TextButton(onClick = vm::executeDelete) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = vm::cancelDelete) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
private fun SearchAndFilterHeader(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    selectedTypeResId: Int?,
    onTypeChange: (Int?) -> Unit
) {
    val filterOptions = listOf(
        null to stringResource(R.string.cat_all),
        R.string.cat_food to stringResource(R.string.cat_food),
        R.string.cat_transport to stringResource(R.string.cat_transport),
        R.string.cat_shopping to stringResource(R.string.cat_shopping),
        R.string.cat_entertainment to stringResource(R.string.cat_entertainment),
        R.string.cat_study to stringResource(R.string.cat_study),
        R.string.cat_other to stringResource(R.string.cat_other)
    )
    
    Surface(
        modifier = Modifier.padding(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                shape = MaterialTheme.shapes.medium,
                singleLine = true
            )
            Spacer(Modifier.height(12.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filterOptions) { (resId, label) ->
                    FilterChip(
                        selected = selectedTypeResId == resId,
                        onClick = { onTypeChange(resId) },
                        label = { Text(label) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ExpenseList(
    items: List<ExpenseWithFriends>,
    onItemClick: (ExpenseWithFriends) -> Unit,
    onEditClick: (ExpenseWithFriends) -> Unit,
    onDeleteClick: (Expense) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(bottom = 88.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(items, key = { it.expense.id }) { item ->
            ExpenseItem(
                item = item,
                onClick = { onItemClick(item) },
                onEdit = { onEditClick(item) },
                onDelete = { onDeleteClick(item.expense) }
            )
        }
    }
}

@Composable
private fun ExpenseItem(
    item: ExpenseWithFriends,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val expense = item.expense
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }
    val allSettled = item.friendCrossRefs.isNotEmpty() && item.friendCrossRefs.all { it.isSettled }

    Card(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val icon = when (expense.type) {
                "Ăn uống", stringResource(R.string.cat_food), "Food & Drinks" -> Icons.Default.Restaurant
                "Di chuyển", stringResource(R.string.cat_transport), "Transport" -> Icons.Default.DirectionsCar
                "Mua sắm", stringResource(R.string.cat_shopping), "Shopping" -> Icons.Default.ShoppingBag
                "Giải trí", stringResource(R.string.cat_entertainment), "Entertainment" -> Icons.Default.SportsEsports
                "Học tập", stringResource(R.string.cat_study), "Study" -> Icons.Default.School
                else -> Icons.Default.Category
            }

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        if (allSettled) Color(0xFF4CAF50).copy(alpha = 0.1f) else MaterialTheme.colorScheme.secondaryContainer,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon, 
                    contentDescription = null, 
                    tint = if (allSettled) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            Spacer(Modifier.width(16.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = expense.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (allSettled) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Done",
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Text(
                    text = dateFormat.format(Date(expense.date)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Column(
                modifier = Modifier.widthIn(min = 88.dp, max = 128.dp),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = "${String.format(vnLocale, "%,.0f", expense.amount)} đ",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (expense.isSplit) {
                    Text(
                        text = "Shared",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
            
            Spacer(Modifier.width(8.dp))
            
            Row {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpenseBottomSheet(
    state: HomeUiState,
    vm: HomeViewModel
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { vm.onImagePicked(it) }
    }

    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            cameraUri?.let { vm.onImagePicked(it) }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(context, "Permissions granted. Click again to take photo.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Camera permission required.", Toast.LENGTH_SHORT).show()
        }
    }

    ModalBottomSheet(
        onDismissRequest = vm::closeBottomSheet,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
        ) {
            var tabIndex by remember { mutableStateOf(0) }
            val tabs = listOf(stringResource(R.string.manual_input), stringResource(R.string.ai_parser))

            TabRow(selectedTabIndex = tabIndex) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = tabIndex == index,
                        onClick = { tabIndex = index },
                        text = { Text(title) }
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (tabIndex == 0) {
                    item {
                        ExpenseForm(
                            state = state,
                            vm = vm,
                            onFieldChange = vm::onManualFieldChange,
                            onPhotoClick = { photoPickerLauncher.launch("image/*") },
                            onClearPhoto = { vm.onManualFieldChange(clearBillImage = true) }
                        )
                    }
                    
                    item {
                        FriendSelectionSection(
                            allFriends = state.allFriends,
                            selectedFriendIds = state.selectedFriendShares.keys,
                            onFriendToggle = vm::toggleFriendSelection
                        )
                    }

                    if (state.selectedFriendShares.isNotEmpty()) {
                        item {
                            Text(stringResource(R.string.shares_counter), style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(8.dp))
                            
                            ShareCounter(
                                name = stringResource(R.string.me),
                                count = state.myShareCount,
                                onIncrement = { vm.onManualFieldChange(myShareCount = state.myShareCount + 1) },
                                onDecrement = { vm.onManualFieldChange(myShareCount = (state.myShareCount - 1).coerceAtLeast(0)) }
                            )
                            
                            state.selectedFriendShares.forEach { (id, count) ->
                                val friendName = state.allFriends.find { it.id == id }?.name ?: "Unknown"
                                ShareCounter(
                                    name = friendName,
                                    count = count,
                                    onIncrement = { vm.updateFriendShare(id, 1) },
                                    onDecrement = { vm.updateFriendShare(id, -1) }
                                )
                            }
                        }

                        item {
                            Text(stringResource(R.string.payer_label), style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = state.payerId == null,
                                    onClick = { vm.onManualFieldChange(isMePayer = true) },
                                    label = { Text(stringResource(R.string.me)) }
                                )
                                state.selectedFriendShares.keys.forEach { fid ->
                                    val f = state.allFriends.find { it.id == fid }
                                    if (f != null) {
                                        FilterChip(
                                            selected = state.payerId == fid,
                                            onClick = { vm.onManualFieldChange(payerId = fid, isMePayer = false) },
                                            label = { Text(f.name) }
                                        )
                                    }
                                }
                            }
                        }

                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = state.isSettled, onCheckedChange = { vm.onManualFieldChange(isSettled = it) })
                                Text(stringResource(R.string.paid_debt))
                            }
                        }
                    }
                } else {
                    item {
                        AIParserSection(
                            state = state, 
                            vm = vm, 
                            onPickImage = { photoPickerLauncher.launch("image/*") },
                            onTakePhoto = {
                                when (PackageManager.PERMISSION_GRANTED) {
                                    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) -> {
                                        val file = File(context.cacheDir, "images/temp_bill_${System.currentTimeMillis()}.jpg")
                                        file.parentFile?.mkdirs()
                                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                        cameraUri = uri
                                        cameraLauncher.launch(uri)
                                    }
                                    else -> {
                                        permissionLauncher.launch(Manifest.permission.CAMERA)
                                    }
                                }
                            }
                        )
                    }
                }

                item {
                    state.errorMessage?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    Button(
                        onClick = { vm.saveExpense(state.billImageUri) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = state.manualAmount.isNotEmpty() && state.manualTitle.isNotEmpty()
                    ) {
                        Text(stringResource(R.string.save))
                    }
                }
                
                item { Spacer(Modifier.height(32.dp)) }
            }
        }
    }
}

@Composable
private fun ShareCounter(
    name: String,
    count: Int,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(name, style = MaterialTheme.typography.bodyLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDecrement) { Icon(Icons.Default.Remove, contentDescription = null) }
            Text(count.toString(), modifier = Modifier.width(24.dp), textAlign = TextAlign.Center)
            IconButton(onClick = onIncrement) { Icon(Icons.Default.Add, contentDescription = null) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ExpenseForm(
    state: HomeUiState,
    vm: HomeViewModel,
    onFieldChange: (
        type: String? ,
        title: String? ,
        content: String? ,
        amount: String? ,
        dateMillis: Long?
    ) -> Unit,
    onPhotoClick: () -> Unit,
    onClearPhoto: () -> Unit
) {
    val categoryOptions = listOf(
        R.string.cat_food,
        R.string.cat_transport,
        R.string.cat_shopping,
        R.string.cat_entertainment,
        R.string.cat_study,
        R.string.cat_other
    )
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }
    var showDatePicker by remember { mutableStateOf(false) }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = state.manualDateMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    onFieldChange(null, null, null, null, datePickerState.selectedDateMillis)
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.cancel)) }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            categoryOptions.forEach { resId ->
                val label = stringResource(resId)
                FilterChip(
                    selected = vm.isCategoryMatch(state.manualType, resId),
                    onClick = { onFieldChange(label, null, null, null, null) },
                    label = { Text(label) }
                )
            }
        }
        
        OutlinedTextField(
            value = state.manualTitle,
            onValueChange = { onFieldChange(null, it, null, null, null) },
            label = { Text(stringResource(R.string.amount_name).replace("chi tiêu", "chi tiêu")) }, // Reusing or just hardcode if needed
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = state.manualAmount,
            onValueChange = { if (it.all { c -> c.isDigit() }) onFieldChange(null, null, null, it, null) },
            label = { Text(stringResource(R.string.amount)) },
            suffix = { Text("VNĐ") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            visualTransformation = ThousandSeparatorVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showDatePicker = true }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.DateRange, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Text(dateFormat.format(Date(state.manualDateMillis)))
        }

        Text(stringResource(R.string.bill_image), style = MaterialTheme.typography.titleSmall)
        if (state.billImageUri != null) {
            Box(Modifier.size(120.dp)) {
                AsyncImage(
                    model = state.billImageUri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().clip(MaterialTheme.shapes.medium),
                    contentScale = ContentScale.Crop
                )
                IconButton(
                    onClick = onClearPhoto,
                    modifier = Modifier.align(Alignment.TopEnd).size(24.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        } else {
            OutlinedButton(onClick = onPhotoClick) {
                Icon(Icons.Default.Image, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.pick_image))
            }
        }
    }
}

@Composable
private fun FriendSelectionSection(
    allFriends: List<Friend>,
    selectedFriendIds: Set<Long>,
    onFriendToggle: (Long) -> Unit
) {
    Column {
        Text(stringResource(R.string.with_friends), style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(allFriends) { friend ->
                FilterChip(
                    selected = selectedFriendIds.contains(friend.id),
                    onClick = { onFriendToggle(friend.id) },
                    label = { Text(friend.name) }
                )
            }
        }
    }
}

@Composable
private fun AIParserSection(
    state: HomeUiState,
    vm: HomeViewModel,
    onPickImage: () -> Unit,
    onTakePhoto: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (state.isBillLoading) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.ai_analyzing))
        } else {
            Icon(
                imageVector = Icons.Outlined.PhotoCamera,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.ai_parser_desc),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedButton(onClick = onTakePhoto) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.take_photo))
                }
                Button(onClick = onPickImage) {
                    Icon(Icons.Default.Image, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.pick_image))
                }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.no_data), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun ExpenseDetailDialog(item: ExpenseWithFriends, onDismiss: () -> Unit) {
    val expense = item.expense
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(expense.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailRow(stringResource(R.string.amount), "${String.format(vnLocale, "%,.0f", expense.amount)} đ")
                DetailRow(stringResource(R.string.expense_type), expense.type)
                DetailRow(stringResource(R.string.date), dateFormat.format(Date(expense.date)))
                if (expense.content.isNotEmpty()) DetailRow("Ghi chú", expense.content)
                
                if (expense.isSplit) {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Text(stringResource(R.string.split_detail), fontWeight = FontWeight.Bold)
                    val payerName = if (expense.payerId == null) stringResource(R.string.me) else item.friends.find { it.id == expense.payerId }?.name ?: stringResource(R.string.stranger)
                    DetailRow(stringResource(R.string.payer_name), payerName)
                    
                    val totalShares = expense.myShareCount + item.friendCrossRefs.sumOf { it.shareCount }
                    val amountPerShare = if (totalShares > 0) expense.amount / totalShares else 0.0
                    
                    DetailRow(stringResource(R.string.me), "${expense.myShareCount} suất (${String.format(vnLocale, "%,.0f", amountPerShare * expense.myShareCount)} đ)")
                    item.friends.forEach { friend ->
                        val crossRef = item.friendCrossRefs.find { it.friendId == friend.id }
                        val shares = crossRef?.shareCount ?: 0
                        val settled = crossRef?.isSettled ?: false
                        
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(friend.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (settled) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                }
                                Text(
                                    text = "$shares suất (${String.format(vnLocale, "%,.0f", amountPerShare * shares)} đ)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (settled) Color(0xFF4CAF50) else Color.Unspecified
                                )
                            }
                        }
                    }
                }
                
                if (expense.imageUri != null) {
                    Spacer(Modifier.height(8.dp))
                    AsyncImage(
                        model = expense.imageUri,
                        contentDescription = "Bill image",
                        modifier = Modifier.fillMaxWidth().height(200.dp).clip(MaterialTheme.shapes.medium),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } }
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}
