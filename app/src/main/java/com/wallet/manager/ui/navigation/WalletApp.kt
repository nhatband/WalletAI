package com.wallet.manager.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.wallet.manager.R
import com.wallet.manager.ui.screen.chat.ChatScreen
import com.wallet.manager.ui.screen.creditcard.CreditCardsScreen
import com.wallet.manager.ui.screen.home.HomeScreen
import com.wallet.manager.ui.screen.settings.SettingsScreen
import com.wallet.manager.ui.screen.stats.StatsScreen
import com.wallet.manager.ui.screen.friends.FriendsScreen

@Composable
fun WalletApp(
    navController: NavHostController,
    drawerState: DrawerState,
    onNavigate: (AppDestination) -> Unit,
    onOpenDrawer: () -> Unit
) {
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(Modifier.height(12.dp))
                AppDestination.values().forEach { destination ->
                    NavigationDrawerItem(
                        icon = {
                            Icon(
                                imageVector = when (destination) {
                                    AppDestination.HOME -> Icons.Default.Home
                                    AppDestination.STATS -> Icons.Default.BarChart
                                    AppDestination.CREDIT_CARDS -> Icons.Default.CreditCard
                                    AppDestination.FRIENDS -> Icons.Default.People
                                    AppDestination.CHAT -> Icons.Default.Chat
                                    AppDestination.SETTINGS -> Icons.Default.Settings
                                },
                                contentDescription = null
                            )
                        },
                        label = { Text(stringResource(destination.labelRes)) },
                        selected = false,
                        onClick = { onNavigate(destination) },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                }
            }
        }
    ) {
        NavHost(
            navController = navController,
            startDestination = AppDestination.HOME.route
        ) {
            composable(AppDestination.HOME.route) {
                HomeScreen(onOpenDrawer = onOpenDrawer)
            }
            composable(AppDestination.STATS.route) {
                StatsScreen(onOpenDrawer = onOpenDrawer)
            }
            composable(AppDestination.CREDIT_CARDS.route) {
                CreditCardsScreen(onOpenDrawer = onOpenDrawer)
            }
            composable(AppDestination.FRIENDS.route) {
                FriendsScreen(onOpenDrawer = onOpenDrawer)
            }
            composable(AppDestination.CHAT.route) {
                ChatScreen(onOpenDrawer = onOpenDrawer)
            }
            composable(AppDestination.SETTINGS.route) {
                SettingsScreen(onOpenDrawer = onOpenDrawer)
            }
        }
    }
}
