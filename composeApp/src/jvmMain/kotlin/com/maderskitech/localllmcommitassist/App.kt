package com.maderskitech.localllmcommitassist

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.maderskitech.localllmcommitassist.data.SettingsRepository
import com.maderskitech.localllmcommitassist.ui.MainScreen
import com.maderskitech.localllmcommitassist.ui.SettingsScreen
import com.maderskitech.localllmcommitassist.viewmodel.MainViewModel

enum class Screen { Main, Settings }

@Composable
fun App() {
    val settingsRepository = remember { SettingsRepository() }
    var currentScreen by remember { mutableStateOf(Screen.Main) }
    val mainViewModel: MainViewModel = viewModel { MainViewModel(settingsRepository) }

    MaterialTheme {
        when (currentScreen) {
            Screen.Main -> MainScreen(
                viewModel = mainViewModel,
                onNavigateToSettings = { currentScreen = Screen.Settings },
            )
            Screen.Settings -> SettingsScreen(
                settingsRepository = settingsRepository,
                onBack = { currentScreen = Screen.Main },
            )
        }
    }
}
