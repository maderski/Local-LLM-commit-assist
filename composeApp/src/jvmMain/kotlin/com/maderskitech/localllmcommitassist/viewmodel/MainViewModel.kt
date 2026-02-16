package com.maderskitech.localllmcommitassist.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maderskitech.localllmcommitassist.data.GitService
import com.maderskitech.localllmcommitassist.data.LlmService
import com.maderskitech.localllmcommitassist.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class MainUiState(
    val savedProjects: List<String> = emptyList(),
    val repoPath: String = "",
    val diffText: String = "",
    val commitSummary: String = "",
    val commitDescription: String = "",
    val isLoading: Boolean = false,
    val statusMessage: String = "",
    val isError: Boolean = false,
)

class MainViewModel(
    private val settingsRepository: SettingsRepository = SettingsRepository(),
    private val gitService: GitService = GitService(),
    private val llmService: LlmService = LlmService(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        MainUiState(
            savedProjects = settingsRepository.getSavedProjects(),
            repoPath = settingsRepository.getSelectedProject(),
        )
    )
    val uiState: StateFlow<MainUiState> = _uiState

    fun selectProject(path: String) {
        settingsRepository.setSelectedProject(path)
        _uiState.value = _uiState.value.copy(
            repoPath = path,
            diffText = "",
            commitSummary = "",
            commitDescription = "",
            statusMessage = "",
            isError = false,
        )
    }

    fun addProject(path: String) {
        if (!gitService.isGitRepository(path)) {
            _uiState.value = _uiState.value.copy(statusMessage = "Not a valid git repository", isError = true)
            return
        }
        settingsRepository.addProject(path)
        settingsRepository.setSelectedProject(path)
        _uiState.value = _uiState.value.copy(
            savedProjects = settingsRepository.getSavedProjects(),
            repoPath = path,
            diffText = "",
            commitSummary = "",
            commitDescription = "",
            statusMessage = "",
            isError = false,
        )
    }

    fun removeProject(path: String) {
        settingsRepository.removeProject(path)
        val projects = settingsRepository.getSavedProjects()
        val newSelected = if (_uiState.value.repoPath == path) projects.firstOrNull().orEmpty() else _uiState.value.repoPath
        settingsRepository.setSelectedProject(newSelected)
        _uiState.value = _uiState.value.copy(
            savedProjects = projects,
            repoPath = newSelected,
            diffText = "",
            commitSummary = "",
            commitDescription = "",
            statusMessage = "",
            isError = false,
        )
    }

    fun updateCommitSummary(summary: String) {
        _uiState.value = _uiState.value.copy(commitSummary = summary)
    }

    fun updateCommitDescription(description: String) {
        _uiState.value = _uiState.value.copy(commitDescription = description)
    }

    fun loadDiff() {
        val path = _uiState.value.repoPath.trim()
        if (path.isBlank()) {
            _uiState.value = _uiState.value.copy(statusMessage = "Please select a project first", isError = true)
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true, statusMessage = "", isError = false)
            gitService.getStagedDiff(path)
                .onSuccess { diff ->
                    if (diff.isBlank()) {
                        // No staged changes — run git add -A and retry once
                        _uiState.value = _uiState.value.copy(statusMessage = "No staged changes, running git add...", isError = false)
                        gitService.stageAll(path)
                            .onSuccess {
                                gitService.getStagedDiff(path)
                                    .onSuccess { retryDiff ->
                                        if (retryDiff.isBlank()) {
                                            _uiState.value = _uiState.value.copy(
                                                diffText = "",
                                                isLoading = false,
                                                statusMessage = "No changes found in the repository.",
                                                isError = true,
                                            )
                                        } else {
                                            _uiState.value = _uiState.value.copy(
                                                diffText = retryDiff,
                                                isLoading = false,
                                                statusMessage = "Staged all changes via git add",
                                                isError = false,
                                            )
                                        }
                                    }
                                    .onFailure { e ->
                                        _uiState.value = _uiState.value.copy(
                                            isLoading = false,
                                            statusMessage = "Failed to load diff after staging: ${e.message}",
                                            isError = true,
                                        )
                                    }
                            }
                            .onFailure { e ->
                                _uiState.value = _uiState.value.copy(
                                    isLoading = false,
                                    statusMessage = "Failed to stage files: ${e.message}",
                                    isError = true,
                                )
                            }
                    } else {
                        _uiState.value = _uiState.value.copy(diffText = diff, isLoading = false)
                    }
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        statusMessage = "Failed to load diff: ${e.message}",
                        isError = true,
                    )
                }
        }
    }

    fun generateCommitMessage() {
        val diff = _uiState.value.diffText
        if (diff.isBlank()) {
            _uiState.value = _uiState.value.copy(statusMessage = "Load a diff first", isError = true)
            return
        }

        val address = settingsRepository.getLlmAddress()
        val model = settingsRepository.getModelName()

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true, statusMessage = "Generating commit message...", isError = false)
            llmService.generateCommitMessage(address, model, diff)
                .onSuccess { commitMessage ->
                    _uiState.value = _uiState.value.copy(
                        commitSummary = commitMessage.summary,
                        commitDescription = commitMessage.description,
                        isLoading = false,
                        statusMessage = "Commit message generated",
                        isError = false,
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        statusMessage = "LLM error: ${e.message}",
                        isError = true,
                    )
                }
        }
    }

    fun commit() {
        val state = _uiState.value
        if (state.commitSummary.isBlank()) {
            _uiState.value = state.copy(statusMessage = "Commit summary is empty", isError = true)
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true, statusMessage = "", isError = false)
            gitService.commit(state.repoPath.trim(), state.commitSummary, state.commitDescription)
                .onSuccess { output ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        diffText = "",
                        commitSummary = "",
                        commitDescription = "",
                        statusMessage = "Committed successfully!\n$output",
                        isError = false,
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        statusMessage = "Commit failed: ${e.message}",
                        isError = true,
                    )
                }
        }
    }
}
