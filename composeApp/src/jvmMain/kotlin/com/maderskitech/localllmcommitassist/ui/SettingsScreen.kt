package com.maderskitech.localllmcommitassist.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.maderskitech.localllmcommitassist.data.LlmService
import com.maderskitech.localllmcommitassist.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    settingsRepository: SettingsRepository,
    onBack: () -> Unit,
) {
    var llmAddress by remember { mutableStateOf(settingsRepository.getLlmAddress()) }
    var modelName by remember { mutableStateOf(settingsRepository.getModelName()) }
    var saved by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var testIsError by remember { mutableStateOf(false) }
    val llmService = remember { LlmService() }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)

        OutlinedTextField(
            value = llmAddress,
            onValueChange = { llmAddress = it; saved = false; testResult = null },
            label = { Text("LLM Server Address") },
            placeholder = { Text("http://localhost:1234/v1") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = modelName,
            onValueChange = { modelName = it; saved = false; testResult = null },
            label = { Text("Model Name (optional)") },
            placeholder = { Text("Leave blank for default") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(onClick = {
                settingsRepository.setLlmAddress(llmAddress)
                settingsRepository.setModelName(modelName)
                saved = true
            }) {
                Text("Save")
            }

            OutlinedButton(
                onClick = {
                    testing = true
                    testResult = null
                    scope.launch(Dispatchers.IO) {
                        llmService.testConnection(llmAddress, modelName)
                            .onSuccess { reply ->
                                testResult = "Connection successful! LLM replied: \"$reply\""
                                testIsError = false
                            }
                            .onFailure { e ->
                                testResult = "Connection failed: ${e.message}"
                                testIsError = true
                            }
                        testing = false
                    }
                },
                enabled = !testing && llmAddress.isNotBlank(),
            ) {
                Text("Test Connection")
            }

            OutlinedButton(onClick = onBack) {
                Text("Back")
            }

            if (saved) {
                Text(
                    "Settings saved",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }

        if (testing) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        testResult?.let { result ->
            Text(
                text = result,
                color = if (testIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
