package com.wallet.manager.ui.navigation

import com.wallet.manager.R

enum class AppDestination(val route: String, val labelRes: Int) {
    HOME("home", R.string.home_title),
    STATS("stats", R.string.history_title),
    FRIENDS("friends", R.string.friends_title),
    CHAT("chat", R.string.chat_title),
    SETTINGS("settings", R.string.settings_title)
}
