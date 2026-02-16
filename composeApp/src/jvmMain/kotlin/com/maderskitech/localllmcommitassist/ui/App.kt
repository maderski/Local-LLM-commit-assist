package com.maderskitech.localllmcommitassist.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.maderskitech.localllmcommitassist.model.AppScreen
import com.maderskitech.localllmcommitassist.ui.screen.MainScreen
import com.maderskitech.localllmcommitassist.ui.screen.SettingsScreen
import com.maderskitech.localllmcommitassist.ui.viewmodel.AppViewModel

@Composable
fun App() {
    MaterialTheme {
        val viewModel = remember { AppViewModel() }
        val uiState = viewModel.uiState

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .safeContentPadding()
                .padding(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.navigateTo(AppScreen.Main) }) { Text("Main") }
                TextButton(onClick = { viewModel.navigateTo(AppScreen.Settings) }) { Text("Settings") }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))

            when (uiState.currentScreen) {
                AppScreen.Main -> {
                    MainScreen(
                        uiState = uiState,
                        onToggleDropdown = viewModel::toggleDropdown,
                        onDismissDropdown = viewModel::dismissDropdown,
                        onSelectProjectAtPath = viewModel::selectProjectByPath,
                        onAddProject = viewModel::addProjectFromPicker,
                        onAdditionalContextChange = viewModel::onAdditionalContextChange,
                        onGenerate = viewModel::generateCommitMessage,
                        onCopyToClipboard = viewModel::copyToClipboard,
                        onCommitChanges = viewModel::commitChanges,
                        onClearCommitText = viewModel::clearCommitText,
                        onSummaryChange = viewModel::onSummaryChange,
                        onDescriptionChange = viewModel::onDescriptionChange,
                    )
                }

                AppScreen.Settings -> {
                    SettingsScreen(
                        uiState = uiState,
                        onBaseUrlChange = viewModel::onSettingsBaseUrlChange,
                        onModelChange = viewModel::onSettingsModelChange,
                        onSaveSettings = viewModel::saveSettings,
                        onTestConnection = viewModel::testConnection,
                    )
                }
            }
        }
    }
}
