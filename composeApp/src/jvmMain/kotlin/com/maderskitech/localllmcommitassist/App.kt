package com.maderskitech.localllmcommitassist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.maderskitech.localllmcommitassist.data.SettingsRepository
import com.maderskitech.localllmcommitassist.ui.AppTheme
import com.maderskitech.localllmcommitassist.ui.MainScreen
import com.maderskitech.localllmcommitassist.ui.SettingsScreen
import com.maderskitech.localllmcommitassist.viewmodel.MainViewModel

enum class Screen { Main, Settings }

@Composable
fun App() {
    val settingsRepository = remember { SettingsRepository() }
    var currentScreen by remember { mutableStateOf(Screen.Main) }
    val mainViewModel: MainViewModel = viewModel { MainViewModel(settingsRepository) }

    AppTheme {
        Surface(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            color = MaterialTheme.colorScheme.background,
        ) {
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
}
