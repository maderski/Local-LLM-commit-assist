package com.maderskitech.localllmcommitassist.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.maderskitech.localllmcommitassist.data.AzureReviewer
import com.maderskitech.localllmcommitassist.data.GitHubReviewer
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
    var azureUsername by remember { mutableStateOf(settingsRepository.getAzureDevOpsUsername()) }
    var azureToken by remember { mutableStateOf(settingsRepository.getAzureDevOpsToken()) }
    var prSaved by remember { mutableStateOf(false) }
    var prDropdownExpanded by remember { mutableStateOf(false) }
    var azureLinkWorkItems by remember { mutableStateOf(settingsRepository.getAzureLinkWorkItems()) }
    var azureAutoTag by remember { mutableStateOf(settingsRepository.getAzureAutoTag()) }
    var azureUpdateWorkItemStatus by remember { mutableStateOf(settingsRepository.getAzureUpdateWorkItemStatus()) }
    var azureReviewers by remember { mutableStateOf(settingsRepository.getAzureReviewers()) }
    var newReviewerName by remember { mutableStateOf("") }
    var newReviewerUuid by remember { mutableStateOf("") }
    var newReviewerRequired by remember { mutableStateOf(false) }
    var githubReviewers by remember { mutableStateOf(settingsRepository.getGitHubReviewers()) }
    var newGitHubReviewerLogin by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Header
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.CenterStart),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "Settings",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
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
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary,
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

                if (prPlatform == "azure_devops") {
                    OutlinedTextField(
                        value = azureUsername,
                        onValueChange = { azureUsername = it; prSaved = false },
                        label = { Text("Azure DevOps Username") },
                        placeholder = { Text("Enter your username") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
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

                if (prPlatform == "github") {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    Text(
                        "Reviewers",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    githubReviewers.forEach { reviewer ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                reviewer.login,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = {
                                    githubReviewers = githubReviewers.filter { it.login != reviewer.login }
                                    settingsRepository.setGitHubReviewers(githubReviewers)
                                },
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Remove reviewer",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = newGitHubReviewerLogin,
                            onValueChange = { newGitHubReviewerLogin = it },
                            label = { Text("GitHub Username") },
                            placeholder = { Text("e.g. octocat") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f),
                        )

                        FilledTonalButton(
                            onClick = {
                                val login = newGitHubReviewerLogin.trim().trimStart('@')
                                if (login.isNotBlank() && githubReviewers.none { it.login == login }) {
                                    githubReviewers = githubReviewers + GitHubReviewer(login = login)
                                    settingsRepository.setGitHubReviewers(githubReviewers)
                                    newGitHubReviewerLogin = ""
                                }
                            },
                            enabled = newGitHubReviewerLogin.isNotBlank(),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add")
                        }
                    }
                }

                if (prPlatform == "azure_devops") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = azureLinkWorkItems,
                            onCheckedChange = {
                                azureLinkWorkItems = it
                                settingsRepository.setAzureLinkWorkItems(it)
                            },
                        )
                        Text(
                            "Link work items from branch name",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = azureAutoTag,
                            onCheckedChange = {
                                azureAutoTag = it
                                settingsRepository.setAzureAutoTag(it)
                            },
                        )
                        Text(
                            "Auto-tag PRs based on project name",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    if (azureLinkWorkItems) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = azureUpdateWorkItemStatus,
                                onCheckedChange = {
                                    azureUpdateWorkItemStatus = it
                                    settingsRepository.setAzureUpdateWorkItemStatus(it)
                                },
                            )
                            Text(
                                "Set linked work item status to 'Code Review' when PR is created",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    Text(
                        "Reviewers",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    azureReviewers.forEach { reviewer ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    reviewer.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    reviewer.id,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                if (reviewer.isRequired) "Required" else "Optional",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (reviewer.isRequired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                            )
                            IconButton(
                                onClick = {
                                    azureReviewers = azureReviewers.filter { it.id != reviewer.id }
                                    settingsRepository.setAzureReviewers(azureReviewers)
                                },
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Remove reviewer",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = newReviewerName,
                        onValueChange = { newReviewerName = it },
                        label = { Text("Display Name") },
                        placeholder = { Text("e.g. John Doe") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    OutlinedTextField(
                        value = newReviewerUuid,
                        onValueChange = { newReviewerUuid = it },
                        label = { Text("UUID") },
                        placeholder = { Text("e.g. 12345678-1234-1234-1234-123456789abc") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Checkbox(
                            checked = newReviewerRequired,
                            onCheckedChange = { newReviewerRequired = it },
                        )
                        Text("Required", style = MaterialTheme.typography.bodyMedium)

                        Spacer(modifier = Modifier.weight(1f))

                        FilledTonalButton(
                            onClick = {
                                if (newReviewerUuid.isNotBlank() && newReviewerName.isNotBlank()) {
                                    azureReviewers = azureReviewers + AzureReviewer(
                                        id = newReviewerUuid.trim(),
                                        name = newReviewerName.trim(),
                                        isRequired = newReviewerRequired,
                                    )
                                    settingsRepository.setAzureReviewers(azureReviewers)
                                    newReviewerName = ""
                                    newReviewerUuid = ""
                                    newReviewerRequired = false
                                }
                            },
                            enabled = newReviewerUuid.isNotBlank() && newReviewerName.isNotBlank(),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Reviewer")
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = {
                            settingsRepository.setPrPlatform(prPlatform)
                            settingsRepository.setGitHubToken(githubToken)
                            settingsRepository.setAzureDevOpsUsername(azureUsername)
                            settingsRepository.setAzureDevOpsToken(azureToken)
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
