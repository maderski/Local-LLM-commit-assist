package com.maderskitech.localllmcommitassist.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.maderskitech.localllmcommitassist.model.AppScreen
import com.maderskitech.localllmcommitassist.ui.screen.MainScreen
import com.maderskitech.localllmcommitassist.ui.screen.SettingsScreen
import com.maderskitech.localllmcommitassist.ui.theme.LocalCommitAssistTheme
import com.maderskitech.localllmcommitassist.ui.viewmodel.AppViewModel

@Composable
fun App() {
    LocalCommitAssistTheme {
        val viewModel = remember { AppViewModel() }
        val uiState = viewModel.uiState

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                            MaterialTheme.colorScheme.background,
                        )
                    )
                )
                .safeContentPadding()
                .padding(16.dp),
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            text = "Local LLM Commit Assist",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Generate, review, and commit with confidence",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(
                            onClick = { viewModel.navigateTo(AppScreen.Main) },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (uiState.currentScreen == AppScreen.Main) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            )
                        ) { Text("Main") }

                        FilledTonalButton(
                            onClick = { viewModel.navigateTo(AppScreen.Settings) },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (uiState.currentScreen == AppScreen.Settings) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            )
                        ) { Text("Settings") }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(14.dp))

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
