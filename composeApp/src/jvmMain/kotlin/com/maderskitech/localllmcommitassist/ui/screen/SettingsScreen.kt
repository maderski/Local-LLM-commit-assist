package com.maderskitech.localllmcommitassist.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.maderskitech.localllmcommitassist.ui.state.AppUiState

@Composable
fun SettingsScreen(
    uiState: AppUiState,
    onBaseUrlChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onSaveSettings: () -> Unit,
    onTestConnection: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("LLM Settings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    text = "Connect to your local OpenAI-compatible endpoint.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                OutlinedTextField(
                    value = uiState.settingsBaseUrlInput,
                    onValueChange = onBaseUrlChange,
                    label = { Text("Local LLM OpenAI Base URL") },
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = uiState.settingsModelInput,
                    onValueChange = onModelChange,
                    label = { Text("Model") },
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onSaveSettings,
                        modifier = Modifier.defaultMinSize(minHeight = 46.dp),
                    ) {
                        Text("Save Settings")
                    }
                    FilledTonalButton(
                        onClick = onTestConnection,
                        modifier = Modifier.defaultMinSize(minHeight = 46.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    ) {
                        Text("Test Connection")
                    }
                }
            }
        }

        uiState.settingsStatus?.let {
            val isError = it.contains("failed", ignoreCase = true)
            val background = if (isError) {
                MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
            } else {
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
            }
            val contentColor = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSecondaryContainer
            Surface(
                color = background,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = it,
                    color = contentColor,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
