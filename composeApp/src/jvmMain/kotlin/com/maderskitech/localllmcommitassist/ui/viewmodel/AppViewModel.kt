package com.maderskitech.localllmcommitassist.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.maderskitech.localllmcommitassist.data.repository.AppStoreRepository
import com.maderskitech.localllmcommitassist.data.service.GitProjectService
import com.maderskitech.localllmcommitassist.data.service.LocalLlmClient
import com.maderskitech.localllmcommitassist.model.AppScreen
import com.maderskitech.localllmcommitassist.model.AppSettings
import com.maderskitech.localllmcommitassist.model.Project
import com.maderskitech.localllmcommitassist.ui.state.AppUiState
import com.maderskitech.localllmcommitassist.util.ClipboardService
import com.maderskitech.localllmcommitassist.util.ProjectPicker
import com.maderskitech.localllmcommitassist.util.SwingProjectPicker
import com.maderskitech.localllmcommitassist.util.SystemClipboardService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

class AppViewModel(
    private val storeRepository: AppStoreRepository = AppStoreRepository,
    private val gitProjectService: GitProjectService = GitProjectService,
    private val localLlmClient: LocalLlmClient = LocalLlmClient,
    private val projectPicker: ProjectPicker = SwingProjectPicker,
    private val clipboardService: ClipboardService = SystemClipboardService,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val initialSettings = storeRepository.loadSettings()
    private val initialProjects = storeRepository.loadProjects()

    var uiState by mutableStateOf(
        AppUiState(
            settings = initialSettings,
            settingsBaseUrlInput = initialSettings.baseUrl,
            settingsModelInput = initialSettings.model,
            projects = initialProjects,
            selectedProjectPath = initialProjects.firstOrNull()?.path.orEmpty(),
        )
    )
        private set

    fun navigateTo(screen: AppScreen) {
        uiState = uiState.copy(currentScreen = screen)
    }

    fun toggleDropdown() {
        uiState = uiState.copy(dropdownExpanded = !uiState.dropdownExpanded)
    }

    fun dismissDropdown() {
        uiState = uiState.copy(dropdownExpanded = false)
    }

    fun selectProject(project: Project) {
        uiState = uiState.copy(
            selectedProjectPath = project.path,
            dropdownExpanded = false,
        )
    }

    fun selectProjectByPath(path: String) {
        uiState.projects.firstOrNull { it.path == path }?.let(::selectProject)
    }

    fun addProjectFromPicker() {
        val path = projectPicker.pickDirectory() ?: return
        val projectName = File(path).name.ifBlank { path }
        val alreadyExists = uiState.projects.any { it.path.equals(path, ignoreCase = true) }
        if (alreadyExists) {
            uiState = uiState.copy(generationStatus = "That project is already added.")
            return
        }

        val newProjects = uiState.projects + Project(name = projectName, path = path)
        storeRepository.saveProjects(newProjects)

        uiState = uiState.copy(
            projects = newProjects,
            selectedProjectPath = path,
            generationStatus = null,
        )
    }

    fun onAdditionalContextChange(value: String) {
        uiState = uiState.copy(additionalContextInput = value)
    }

    fun onSummaryChange(value: String) {
        uiState = uiState.copy(generatedSummary = value)
    }

    fun onDescriptionChange(value: String) {
        uiState = uiState.copy(generatedDescription = value)
    }

    fun generateCommitMessage() {
        val selectedProject = uiState.selectedProject
        if (selectedProject == null) {
            uiState = uiState.copy(generationStatus = "Select a project first.")
            return
        }

        uiState = uiState.copy(generationStatus = "Staging changes (git add -A) and preparing diff...")

        val settings = uiState.settings
        val additionalContext = uiState.additionalContextInput
        scope.launch {
            val stagedDiffResult = gitProjectService.stageAndReadCachedDiff(selectedProject.path)
            stagedDiffResult.onSuccess { stagedDiff ->
                uiState = uiState.copy(generationStatus = "Generating commit message from staged diff...")
                val result = localLlmClient.generateCommitMessage(
                    settings = settings,
                    project = selectedProject,
                    stagedDiff = stagedDiff,
                    additionalContext = additionalContext,
                )
                result.onSuccess { generated ->
                    uiState = uiState.copy(
                        generatedSummary = generated.summary,
                        generatedDescription = generated.description,
                        generationStatus = "Commit message generated from staged changes.",
                    )
                }.onFailure { error ->
                    uiState = uiState.copy(generationStatus = "Generation failed: ${error.message}")
                }
            }.onFailure { error ->
                uiState = uiState.copy(generationStatus = "Git staging failed: ${error.message}")
            }
        }
    }

    fun copyToClipboard() {
        val commitText = buildString {
            append(uiState.generatedSummary.trim())
            val description = uiState.generatedDescription.trim()
            if (description.isNotBlank()) {
                append("\n\n")
                append(description)
            }
        }.trim()

        if (commitText.isBlank()) {
            uiState = uiState.copy(generationStatus = "Nothing to copy. Generate or enter a commit message first.")
            return
        }

        runCatching { clipboardService.copy(commitText) }
            .onSuccess {
                uiState = uiState.copy(generationStatus = "Commit message copied to clipboard.")
            }
            .onFailure { error ->
                uiState = uiState.copy(generationStatus = "Clipboard copy failed: ${error.message}")
            }
    }

    fun commitChanges() {
        val selectedProject = uiState.selectedProject
        if (selectedProject == null) {
            uiState = uiState.copy(generationStatus = "Select a project first.")
            return
        }

        if (uiState.generatedSummary.trim().isBlank()) {
            uiState = uiState.copy(generationStatus = "Summary is required before committing.")
            return
        }

        uiState = uiState.copy(generationStatus = "Committing changes...")

        val summary = uiState.generatedSummary
        val description = uiState.generatedDescription
        scope.launch {
            val result = gitProjectService.commitAllChanges(
                projectPath = selectedProject.path,
                summary = summary,
                description = description,
            )
            result.onSuccess { output ->
                uiState = uiState.copy(
                    generatedSummary = "",
                    generatedDescription = "",
                    generationStatus = "Commit created. ${output.lineSequence().firstOrNull().orEmpty()}",
                )
            }.onFailure { error ->
                uiState = uiState.copy(generationStatus = "Commit failed: ${error.message}")
            }
        }
    }

    fun clearCommitText() {
        uiState = uiState.copy(
            generatedSummary = "",
            generatedDescription = "",
            generationStatus = "Cleared commit summary and description.",
        )
    }

    fun onSettingsBaseUrlChange(value: String) {
        uiState = uiState.copy(settingsBaseUrlInput = value)
    }

    fun onSettingsModelChange(value: String) {
        uiState = uiState.copy(settingsModelInput = value)
    }

    fun saveSettings() {
        val settings = AppSettings(
            baseUrl = uiState.settingsBaseUrlInput.trim(),
            model = uiState.settingsModelInput.trim(),
        )
        storeRepository.saveSettings(settings)
        uiState = uiState.copy(settings = settings, settingsStatus = "Settings saved.")
    }

    fun testConnection() {
        val candidate = AppSettings(
            baseUrl = uiState.settingsBaseUrlInput.trim(),
            model = uiState.settingsModelInput.trim(),
        )

        uiState = uiState.copy(settingsStatus = "Testing connection...")
        scope.launch {
            val result = localLlmClient.testConnection(candidate)
            result.onSuccess { status ->
                uiState = uiState.copy(settingsStatus = status)
            }.onFailure { error ->
                uiState = uiState.copy(settingsStatus = "Connection failed: ${error.message}")
            }
        }
    }
}
