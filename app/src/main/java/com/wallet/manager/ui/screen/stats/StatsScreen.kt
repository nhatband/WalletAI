package com.wallet.manager.ui.screen.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.wallet.manager.R
import com.wallet.manager.viewmodel.FriendDebt
import com.wallet.manager.viewmodel.StatsFilter
import com.wallet.manager.viewmodel.StatsViewModel
import com.wallet.manager.viewmodel.DailySpending
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onOpenDrawer: () -> Unit,
    vm: StatsViewModel = viewModel(factory = StatsViewModel.Factory)
) {
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current
    val dateFormatter = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }

    var showRangePicker by remember { mutableStateOf(false) }

    if (showRangePicker) {
        val dateRangePickerState = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { showRangePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val start = dateRangePickerState.selectedStartDateMillis
                    val end = dateRangePickerState.selectedEndDateMillis
                    if (start != null && end != null) {
                        val endCal = Calendar.getInstance().apply {
                            timeInMillis = end
                            set(Calendar.HOUR_OF_DAY, 23)
                            set(Calendar.MINUTE, 59)
                            set(Calendar.SECOND, 59)
                        }
                        vm.setCustomRange(start, endCal.timeInMillis)
                        showRangePicker = false
                    }
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showRangePicker = false }) { Text(stringResource(R.string.cancel)) }
            }
        ) {
            DateRangePicker(
                state = dateRangePickerState,
                title = { Text(stringResource(R.string.filter_custom), modifier = Modifier.padding(16.dp)) },
                showModeToggle = false,
                modifier = Modifier.fillMaxWidth().height(450.dp)
            )
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.history_title)) },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Menu"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            FilterRow(
                current = state.filter, 
                onFilterChange = vm::setFilter,
                onCustomRange = { showRangePicker = true }
            )

            if (state.filter == StatsFilter.CUSTOM) {
                Text(
                    text = stringResource(R.string.from_to_date, dateFormatter.format(Date(state.customStartDate)), dateFormatter.format(Date(state.customEndDate))),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            Spacer(Modifier.height(12.dp))

            DebtOverview(
                owedToMe = state.totalOwedToMe,
                iOweOthers = state.totalIOweOthers
            )

            Spacer(Modifier.height(16.dp))

            if (state.friendDebts.isNotEmpty()) {
                Text(stringResource(R.string.debt_detail_by_person), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                FriendDebtList(state.friendDebts)
                Spacer(Modifier.height(24.dp))
            }

            StatsCards(
                totalSpent = state.totalSpentByMe,
                avgPerDay = state.avgPerDay,
                maxExpense = state.maxExpense?.expense?.amount ?: 0.0
            )

            Spacer(Modifier.height(24.dp))

            Text(stringResource(R.string.stat_ratio), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            PieChartSection(
                data = state.totalByType.map { it.type to it.total }
            )

            Spacer(Modifier.height(24.dp))

            Text(stringResource(R.string.stat_daily), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            BarChartSection(
                dailySpending = state.dailySpending
            )
            
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun FriendDebtList(debts: List<FriendDebt>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        debts.forEach { debt ->
            val isOwedToMe = debt.netAmount > 0
            val color = if (isOwedToMe) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
            val amountText = if (isOwedToMe) "+${"%,.0f".format(debt.netAmount)}" else "${"%,.0f".format(debt.netAmount)}"
            
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(debt.friendName, style = MaterialTheme.typography.bodyLarge)
                    }
                    Text(
                        text = "$amountText đ",
                        style = MaterialTheme.typography.titleMedium,
                        color = color,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun DebtOverview(owedToMe: Double, iOweOthers: Double) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.debt_situation), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth()) {
                DebtItem(
                    label = stringResource(R.string.friends_owe_me),
                    amount = owedToMe,
                    color = Color(0xFF4CAF50),
                    icon = Icons.Default.ArrowUpward,
                    modifier = Modifier.weight(1f)
                )
                VerticalDivider(modifier = Modifier.height(40.dp).align(Alignment.CenterVertically))
                DebtItem(
                    label = stringResource(R.string.i_owe_friends),
                    amount = iOweOthers,
                    color = MaterialTheme.colorScheme.error,
                    icon = Icons.Default.ArrowDownward,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun DebtItem(
    label: String,
    amount: Double,
    color: Color,
    icon: ImageVector,
    modifier: Modifier
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(label, style = MaterialTheme.typography.bodySmall)
        }
        Text(
            text = "${"%,.0f".format(amount)} đ",
            style = MaterialTheme.typography.titleMedium,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun FilterRow(
    current: StatsFilter, 
    onFilterChange: (StatsFilter) -> Unit,
    onCustomRange: () -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        item { FilterChip(stringResource(R.string.filter_day), current == StatsFilter.DAY) { onFilterChange(StatsFilter.DAY) } }
        item { FilterChip(stringResource(R.string.filter_week), current == StatsFilter.WEEK) { onFilterChange(StatsFilter.WEEK) } }
        item { FilterChip(stringResource(R.string.filter_month), current == StatsFilter.MONTH) { onFilterChange(StatsFilter.MONTH) } }
        item { FilterChip(stringResource(R.string.filter_year), current == StatsFilter.YEAR) { onFilterChange(StatsFilter.YEAR) } }
        item { 
            FilterChip(stringResource(R.string.filter_custom), current == StatsFilter.CUSTOM) { 
                onCustomRange()
            } 
        }
    }
}

@Composable
private fun FilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
            labelColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
        )
    )
}

@Composable
private fun StatsCards(
    totalSpent: Double,
    avgPerDay: Double,
    maxExpense: Double
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StatCardWide(
            title = stringResource(R.string.actual_spent),
            value = totalSpent,
            icon = Icons.Default.Payments,
            color = MaterialTheme.colorScheme.primary
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCardSmall(title = stringResource(R.string.stat_avg), value = avgPerDay, modifier = Modifier.weight(1f))
            StatCardSmall(title = stringResource(R.string.stat_max), value = maxExpense, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatCardWide(title: String, value: Double, icon: ImageVector, color: Color) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(color.copy(alpha = 0.1f), MaterialTheme.shapes.medium),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color)
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.labelMedium)
                Text(
                    text = "${"%,.0f".format(value)} đ",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            }
        }
    }
}

@Composable
private fun StatCardSmall(title: String, value: Double, modifier: Modifier = Modifier) {
    ElevatedCard(modifier = modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.labelSmall)
            Text(
                text = "${"%,.0f".format(value)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun PieChartSection(data: List<Pair<String, Double>>) {
    if (data.isEmpty()) {
        Text(stringResource(R.string.no_data), style = MaterialTheme.typography.bodyMedium)
        return
    }

    val total = data.sumOf { it.second }
    val colors = listOf(
        Color(0xFF2196F3), Color(0xFFE91E63), Color(0xFFFF9800),
        Color(0xFF4CAF50), Color(0xFF9C27B0), Color(0xFF00BCD4)
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Canvas(modifier = Modifier.size(140.dp)) {
            var startAngle = -90f
            data.forEachIndexed { index, (_, value) ->
                val sweep = if (total == 0.0) 0f else (value / total * 360f).toFloat()
                drawArc(
                    color = colors[index % colors.size],
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = true,
                    size = Size(size.width, size.height)
                )
                startAngle += sweep
            }
        }
        Spacer(Modifier.width(24.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            data.forEachIndexed { index, (label, value) ->
                val translatedLabel = when (label) {
                    "Ăn uống" -> stringResource(R.string.cat_food)
                    "Di chuyển" -> stringResource(R.string.cat_transport)
                    "Mua sắm" -> stringResource(R.string.cat_shopping)
                    "Giải trí" -> stringResource(R.string.cat_entertainment)
                    "Học tập" -> stringResource(R.string.cat_study)
                    "Khác" -> stringResource(R.string.cat_other)
                    else -> label
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(10.dp).background(colors[index % colors.size], MaterialTheme.shapes.extraSmall))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "$translatedLabel: ${"%,.0f".format(value)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun BarChartSection(dailySpending: List<DailySpending>) {
    if (dailySpending.isEmpty()) {
        Text(stringResource(R.string.no_data), style = MaterialTheme.typography.bodyMedium)
        return
    }
    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(dailySpending) {
        modelProducer.runTransaction {
            columnSeries { series(dailySpending.map { it.amount.toFloat() }) }
        }
    }

    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberColumnCartesianLayer(),
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis = HorizontalAxis.rememberBottom(
                valueFormatter = { _, value, _ ->
                    dailySpending.getOrNull(value.toInt())?.dateLabel ?: ""
                }
            )
        ),
        modelProducer = modelProducer,
        modifier = Modifier.fillMaxWidth().height(220.dp)
    )
}
