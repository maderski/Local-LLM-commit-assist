package com.maderskitech.localllmcommitassist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun App() {
    MaterialTheme {
        val scope = rememberCoroutineScope()

        var currentScreen by remember { mutableStateOf(AppScreen.Main) }

        var settings by remember { mutableStateOf(AppStore.loadSettings()) }
        var settingsBaseUrlInput by remember { mutableStateOf(settings.baseUrl) }
        var settingsModelInput by remember { mutableStateOf(settings.model) }
        var settingsStatus by remember { mutableStateOf<String?>(null) }

        val projects = remember { mutableStateListOf<Project>().apply { addAll(AppStore.loadProjects()) } }
        var selectedProjectPath by remember { mutableStateOf(projects.firstOrNull()?.path.orEmpty()) }
        var dropdownExpanded by remember { mutableStateOf(false) }

        var additionalContextInput by remember { mutableStateOf("") }
        var generatedSummary by remember { mutableStateOf("") }
        var generatedDescription by remember { mutableStateOf("") }
        var generationStatus by remember { mutableStateOf<String?>(null) }

        val selectedProject = projects.firstOrNull { it.path == selectedProjectPath }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .safeContentPadding()
                .padding(16.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { currentScreen = AppScreen.Main }) { Text("Main") }
                TextButton(onClick = { currentScreen = AppScreen.Settings }) { Text("Settings") }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))

            when (currentScreen) {
                AppScreen.Main -> {
                    MainScreen(
                        projects = projects,
                        selectedProjectName = selectedProject?.name.orEmpty(),
                        dropdownExpanded = dropdownExpanded,
                        additionalContextInput = additionalContextInput,
                        generatedSummary = generatedSummary,
                        generatedDescription = generatedDescription,
                        generationStatus = generationStatus,
                        onToggleDropdown = { dropdownExpanded = !dropdownExpanded },
                        onDismissDropdown = { dropdownExpanded = false },
                        onSelectProject = { project ->
                            selectedProjectPath = project.path
                            dropdownExpanded = false
                        },
                        onAddProject = {
                            val path = pickProjectDirectory() ?: return@MainScreen
                            val name = File(path).name.ifBlank { path }
                            val alreadyExists = projects.any { it.path.equals(path, ignoreCase = true) }
                            if (alreadyExists) {
                                generationStatus = "That project is already added."
                                return@MainScreen
                            }
                            val newProject = Project(name = name, path = path)
                            projects.add(newProject)
                            AppStore.saveProjects(projects)
                            selectedProjectPath = newProject.path
                            generationStatus = null
                        },
                        onAdditionalContextChange = { additionalContextInput = it },
                        onGenerate = {
                            if (selectedProject == null) {
                                generationStatus = "Select a project first."
                                return@MainScreen
                            }

                            generationStatus = "Staging changes (git add -A) and preparing diff..."
                            scope.launch {
                                val stagedDiffResult = GitProjectService.stageAndReadCachedDiff(selectedProject.path)
                                stagedDiffResult.onSuccess { stagedDiff ->
                                    generationStatus = "Generating commit message from staged diff..."
                                    val result = LocalLlmClient.generateCommitMessage(
                                        settings = settings,
                                        project = selectedProject,
                                        stagedDiff = stagedDiff,
                                        additionalContext = additionalContextInput
                                    )
                                    result.onSuccess {
                                        generatedSummary = it.summary
                                        generatedDescription = it.description
                                        generationStatus = "Commit message generated from staged changes."
                                    }.onFailure {
                                        generationStatus = "Generation failed: ${it.message}"
                                    }
                                }.onFailure {
                                    generationStatus = "Git staging failed: ${it.message}"
                                }
                            }
                        },
                        onCopyToClipboard = {
                            val commitText = buildString {
                                append(generatedSummary.trim())
                                val trimmedDescription = generatedDescription.trim()
                                if (trimmedDescription.isNotBlank()) {
                                    append("\n\n")
                                    append(trimmedDescription)
                                }
                            }.trim()

                            if (commitText.isBlank()) {
                                generationStatus = "Nothing to copy. Generate or enter a commit message first."
                                return@MainScreen
                            }

                            runCatching {
                                copyToClipboard(commitText)
                            }.onSuccess {
                                generationStatus = "Commit message copied to clipboard."
                            }.onFailure {
                                generationStatus = "Clipboard copy failed: ${it.message}"
                            }
                        },
                        onCommitChanges = {
                            if (selectedProject == null) {
                                generationStatus = "Select a project first."
                                return@MainScreen
                            }
                            if (generatedSummary.trim().isBlank()) {
                                generationStatus = "Summary is required before committing."
                                return@MainScreen
                            }

                            generationStatus = "Committing changes..."
                            scope.launch {
                                val result = GitProjectService.commitAllChanges(
                                    projectPath = selectedProject.path,
                                    summary = generatedSummary,
                                    description = generatedDescription
                                )
                                result.onSuccess { output ->
                                    generationStatus = "Commit created. ${output.lineSequence().firstOrNull().orEmpty()}"
                                }.onFailure {
                                    generationStatus = "Commit failed: ${it.message}"
                                }
                            }
                        },
                        onSummaryChange = { generatedSummary = it },
                        onDescriptionChange = { generatedDescription = it }
                    )
                }

                AppScreen.Settings -> {
                    SettingsScreen(
                        settingsBaseUrlInput = settingsBaseUrlInput,
                        settingsModelInput = settingsModelInput,
                        settingsStatus = settingsStatus,
                        onBaseUrlChange = { settingsBaseUrlInput = it },
                        onModelChange = { settingsModelInput = it },
                        onSaveSettings = {
                            settings = AppSettings(
                                baseUrl = settingsBaseUrlInput.trim(),
                                model = settingsModelInput.trim()
                            )
                            AppStore.saveSettings(settings)
                            settingsStatus = "Settings saved."
                        },
                        onTestConnection = {
                            val candidate = AppSettings(
                                baseUrl = settingsBaseUrlInput.trim(),
                                model = settingsModelInput.trim()
                            )
                            settingsStatus = "Testing connection..."
                            scope.launch {
                                val result = LocalLlmClient.testConnection(candidate)
                                result.onSuccess {
                                    settingsStatus = it
                                }.onFailure {
                                    settingsStatus = "Connection failed: ${it.message}"
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}
