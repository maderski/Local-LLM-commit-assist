package com.maderskitech.localllmcommitassist.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.maderskitech.localllmcommitassist.data.LlmService
import com.maderskitech.localllmcommitassist.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
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

    var prPlatform by remember { mutableStateOf(settingsRepository.getPrPlatform()) }
    var githubToken by remember { mutableStateOf(settingsRepository.getGitHubToken()) }
    var azureToken by remember { mutableStateOf(settingsRepository.getAzureDevOpsToken()) }
    var prTargetBranch by remember { mutableStateOf(settingsRepository.getPrTargetBranch()) }
    var prSaved by remember { mutableStateOf(false) }
    var prDropdownExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Settings",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            FilledTonalButton(
                onClick = onBack,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            ) {
                Text("Back")
            }
        }

        // LLM Configuration card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    "LLM Configuration",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )

                OutlinedTextField(
                    value = llmAddress,
                    onValueChange = { llmAddress = it; saved = false; testResult = null },
                    label = { Text("Server Address") },
                    placeholder = { Text("http://localhost:1234/v1") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = modelName,
                    onValueChange = { modelName = it; saved = false; testResult = null },
                    label = { Text("Model Name (optional)") },
                    placeholder = { Text("Leave blank for default") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = {
                            settingsRepository.setLlmAddress(llmAddress)
                            settingsRepository.setModelName(modelName)
                            saved = true
                        },
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text("Save")
                    }

                    FilledTonalButton(
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
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                    ) {
                        Text("Test Connection")
                    }

                    if (saved) {
                        Text(
                            "Settings saved",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }

                if (testing) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    )
                }

                testResult?.let { result ->
                    val bgColor = if (testIsError)
                        MaterialTheme.colorScheme.errorContainer
                    else
                        MaterialTheme.colorScheme.primaryContainer
                    val textColor = if (testIsError)
                        MaterialTheme.colorScheme.onErrorContainer
                    else
                        MaterialTheme.colorScheme.onPrimaryContainer

                    Text(
                        text = result,
                        color = textColor,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(bgColor)
                            .padding(12.dp),
                    )
                }
            }
        }

        // Pull Request configuration card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    "Pull Request",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )

                ExposedDropdownMenuBox(
                    expanded = prDropdownExpanded,
                    onExpandedChange = { prDropdownExpanded = it },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedTextField(
                        value = if (prPlatform == "github") "GitHub" else "Azure DevOps",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Platform") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = prDropdownExpanded) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = prDropdownExpanded,
                        onDismissRequest = { prDropdownExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("GitHub") },
                            onClick = { prPlatform = "github"; prDropdownExpanded = false; prSaved = false },
                        )
                        DropdownMenuItem(
                            text = { Text("Azure DevOps") },
                            onClick = { prPlatform = "azure_devops"; prDropdownExpanded = false; prSaved = false },
                        )
                    }
                }

                OutlinedTextField(
                    value = if (prPlatform == "github") githubToken else azureToken,
                    onValueChange = { newValue ->
                        if (prPlatform == "github") githubToken = newValue else azureToken = newValue
                        prSaved = false
                    },
                    label = { Text(if (prPlatform == "github") "GitHub Personal Access Token" else "Azure DevOps PAT") },
                    placeholder = { Text("Paste your token here") },
                    singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = prTargetBranch,
                    onValueChange = { prTargetBranch = it; prSaved = false },
                    label = { Text("Target Branch") },
                    placeholder = { Text("main") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = {
                            settingsRepository.setPrPlatform(prPlatform)
                            settingsRepository.setGitHubToken(githubToken)
                            settingsRepository.setAzureDevOpsToken(azureToken)
                            settingsRepository.setPrTargetBranch(prTargetBranch)
                            prSaved = true
                        },
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text("Save")
                    }
                    if (prSaved) {
                        Text(
                            "Settings saved",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
            }
        }
    }
}
