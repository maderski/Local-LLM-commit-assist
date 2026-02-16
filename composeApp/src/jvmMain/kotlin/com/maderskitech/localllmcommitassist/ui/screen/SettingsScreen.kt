package com.maderskitech.localllmcommitassist.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("LLM Settings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

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
            Button(onClick = onSaveSettings) {
                Text("Save Settings")
            }
            Button(onClick = onTestConnection) {
                Text("Test Connection")
            }
        }

        uiState.settingsStatus?.let {
            Text(it, color = MaterialTheme.colorScheme.primary)
        }
    }
}
