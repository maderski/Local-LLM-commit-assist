package com.maderskitech.localllmcommitassist.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maderskitech.localllmcommitassist.data.GitService
import com.maderskitech.localllmcommitassist.data.LlmService
import com.maderskitech.localllmcommitassist.data.PrService
import com.maderskitech.localllmcommitassist.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class MainUiState(
    val savedProjects: List<String> = emptyList(),
    val repoPath: String = "",
    val currentBranch: String = "",
    val availableBranches: List<String> = emptyList(),
    val prTargetBranch: String = "",
    val fileSummary: String = "",
    val fullDiff: String = "",
    val commitSummary: String = "",
    val commitDescription: String = "",
    val isLoading: Boolean = false,
    val statusMessage: String = "",
    val isError: Boolean = false,
    val prTitle: String = "",
    val prBody: String = "",
    val prUrl: String = "",
)

class MainViewModel(
    private val settingsRepository: SettingsRepository = SettingsRepository(),
    private val gitService: GitService = GitService(),
    private val llmService: LlmService = LlmService(),
    private val prService: PrService = PrService(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        MainUiState(
            savedProjects = settingsRepository.getSavedProjects(),
            repoPath = settingsRepository.getSelectedProject(),
            prTargetBranch = settingsRepository.getPrTargetBranch(),
        )
    )
    val uiState: StateFlow<MainUiState> = _uiState

    init {
        loadCurrentBranch(_uiState.value.repoPath)
        loadBranches(_uiState.value.repoPath)
    }

    fun selectProject(path: String) {
        settingsRepository.setSelectedProject(path)
        _uiState.value = _uiState.value.copy(
            repoPath = path,
            currentBranch = "",
            availableBranches = emptyList(),
            fileSummary = "",
            fullDiff = "",
            commitSummary = "",
            commitDescription = "",
            statusMessage = "",
            isError = false,
            prTitle = "",
            prBody = "",
            prUrl = "",
        )
        loadCurrentBranch(path)
        loadBranches(path)
    }

    private fun loadCurrentBranch(path: String) {
        if (path.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val branch = gitService.getCurrentBranch(path).getOrDefault("")
            _uiState.value = _uiState.value.copy(currentBranch = branch)
        }
    }

    private fun loadBranches(path: String) {
        if (path.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val branches = gitService.getLocalBranches(path).getOrDefault(emptyList())
            _uiState.value = _uiState.value.copy(availableBranches = branches)
        }
    }

    fun updatePrTargetBranch(branch: String) {
        settingsRepository.setPrTargetBranch(branch)
        _uiState.value = _uiState.value.copy(prTargetBranch = branch)
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
            currentBranch = "",
            availableBranches = emptyList(),
            fileSummary = "",
            fullDiff = "",
            commitSummary = "",
            commitDescription = "",
            statusMessage = "",
            isError = false,
            prTitle = "",
            prBody = "",
            prUrl = "",
        )
        loadCurrentBranch(path)
        loadBranches(path)
    }

    fun removeProject(path: String) {
        settingsRepository.removeProject(path)
        val projects = settingsRepository.getSavedProjects()
        val newSelected = if (_uiState.value.repoPath == path) projects.firstOrNull().orEmpty() else _uiState.value.repoPath
        settingsRepository.setSelectedProject(newSelected)
        _uiState.value = _uiState.value.copy(
            savedProjects = projects,
            repoPath = newSelected,
            currentBranch = "",
            availableBranches = emptyList(),
            fileSummary = "",
            fullDiff = "",
            commitSummary = "",
            commitDescription = "",
            statusMessage = "",
            isError = false,
            prTitle = "",
            prBody = "",
            prUrl = "",
        )
        loadCurrentBranch(newSelected)
        loadBranches(newSelected)
    }

    fun updateCommitSummary(summary: String) {
        _uiState.value = _uiState.value.copy(commitSummary = summary)
    }

    fun updateCommitDescription(description: String) {
        _uiState.value = _uiState.value.copy(commitDescription = description)
    }

    fun updatePrTitle(title: String) {
        _uiState.value = _uiState.value.copy(prTitle = title)
    }

    fun updatePrBody(body: String) {
        _uiState.value = _uiState.value.copy(prBody = body)
    }

    fun generateCommitMessage() {
        val path = _uiState.value.repoPath.trim()
        if (path.isBlank()) {
            _uiState.value = _uiState.value.copy(statusMessage = "Please select a project first", isError = true)
            return
        }

        val address = settingsRepository.getLlmAddress()
        val model = settingsRepository.getModelName()

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true, statusMessage = "Loading diff...", isError = false)

            val diff = loadDiff(path)
            if (diff == null) return@launch

            val summary = gitService.getNewFiles(path).getOrDefault(emptyList())
            val statSummary = gitService.getStagedStatSummary(path).getOrDefault("")
            val displayText = if (summary.isNotEmpty()) {
                summary.joinToString("\n") + "\n\n" + statSummary.lines().lastOrNull { it.isNotBlank() }.orEmpty()
            } else {
                statSummary
            }

            _uiState.value = _uiState.value.copy(
                fileSummary = displayText.trim(),
                fullDiff = diff,
                statusMessage = "Generating commit message...",
                isError = false,
            )

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

    fun generatePrDescription() {
        val path = _uiState.value.repoPath.trim()
        if (path.isBlank()) {
            _uiState.value = _uiState.value.copy(statusMessage = "Please select a project first", isError = true)
            return
        }

        val address = settingsRepository.getLlmAddress()
        val model = settingsRepository.getModelName()
        val targetBranch = _uiState.value.prTargetBranch

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true, statusMessage = "Loading commit history...", isError = false)

            val commitLogResult = gitService.getCommitLog(path, targetBranch)
            commitLogResult.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    statusMessage = "Failed to read commit log: ${e.message}",
                    isError = true,
                )
                return@launch
            }

            val commitLog = commitLogResult.getOrThrow()
            if (commitLog.isBlank()) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    statusMessage = "No commits found on this branch",
                    isError = true,
                )
                return@launch
            }

            val currentBranch = _uiState.value.currentBranch
            _uiState.value = _uiState.value.copy(statusMessage = "Generating PR description...", isError = false)

            llmService.generatePrDescription(address, model, commitLog, currentBranch)
                .onSuccess { prDescription ->
                    _uiState.value = _uiState.value.copy(
                        prTitle = prDescription.title,
                        prBody = prDescription.body,
                        isLoading = false,
                        statusMessage = "PR description generated",
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

    fun createPullRequest() {
        val state = _uiState.value
        val path = state.repoPath.trim()
        if (path.isBlank()) {
            _uiState.value = state.copy(statusMessage = "Please select a project first", isError = true)
            return
        }
        if (state.prTitle.isBlank()) {
            _uiState.value = state.copy(statusMessage = "PR title is empty", isError = true)
            return
        }

        val platform = settingsRepository.getPrPlatform()
        val targetBranch = _uiState.value.prTargetBranch
        val currentBranch = state.currentBranch

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true, statusMessage = "Creating pull request...", isError = false)

            val remoteUrlResult = gitService.getRemoteUrl(path)
            remoteUrlResult.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    statusMessage = "Failed to get remote URL: ${e.message}",
                    isError = true,
                )
                return@launch
            }

            val remoteUrl = remoteUrlResult.getOrThrow()

            val prResult: Result<String> = when (platform) {
                "github" -> {
                    val token = settingsRepository.getGitHubToken()
                    val parsed = prService.parseGitHubRemote(remoteUrl)
                    if (parsed == null) {
                        Result.failure(Exception("Could not parse GitHub remote URL: $remoteUrl"))
                    } else {
                        prService.createGitHubPr(
                            token = token,
                            owner = parsed.first,
                            repo = parsed.second,
                            title = state.prTitle,
                            body = state.prBody,
                            head = currentBranch,
                            base = targetBranch,
                        )
                    }
                }
                "azure_devops" -> {
                    val token = settingsRepository.getAzureDevOpsToken()
                    val parsed = prService.parseAzureDevOpsRemote(remoteUrl)
                    if (parsed == null) {
                        Result.failure(Exception("Could not parse Azure DevOps remote URL: $remoteUrl"))
                    } else {
                        prService.createAzureDevOpsPr(
                            token = token,
                            orgUrl = parsed.first,
                            project = parsed.second,
                            repo = parsed.third,
                            title = state.prTitle,
                            description = state.prBody,
                            sourceBranch = currentBranch,
                            targetBranch = targetBranch,
                        )
                    }
                }
                else -> Result.failure(Exception("Unknown platform: $platform"))
            }

            prResult
                .onSuccess { url ->
                    _uiState.value = _uiState.value.copy(
                        prUrl = url,
                        isLoading = false,
                        statusMessage = "Pull request created successfully!",
                        isError = false,
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        statusMessage = "PR creation failed: ${e.message}",
                        isError = true,
                    )
                }
        }
    }

    private fun loadDiff(path: String): String? {
        val diffResult = gitService.getStagedDiff(path)
        diffResult.onFailure { e ->
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                statusMessage = "Failed to load diff: ${e.message}",
                isError = true,
            )
            return null
        }

        var diff = diffResult.getOrThrow()

        if (diff.isBlank()) {
            _uiState.value = _uiState.value.copy(statusMessage = "No staged changes, running git add...", isError = false)
            gitService.stageAll(path).onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    statusMessage = "Failed to stage files: ${e.message}",
                    isError = true,
                )
                return null
            }

            val retryResult = gitService.getStagedDiff(path)
            retryResult.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    statusMessage = "Failed to load diff after staging: ${e.message}",
                    isError = true,
                )
                return null
            }

            diff = retryResult.getOrThrow()
            if (diff.isBlank()) {
                _uiState.value = _uiState.value.copy(
                    fileSummary = "",
            fullDiff = "",
                    isLoading = false,
                    statusMessage = "No changes found in the repository.",
                    isError = true,
                )
                return null
            }
        }

        return diff
    }

    fun commit(andPush: Boolean = false) {
        val state = _uiState.value
        if (state.commitSummary.isBlank()) {
            _uiState.value = state.copy(statusMessage = "Commit summary is empty", isError = true)
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true, statusMessage = "", isError = false)
            gitService.commit(state.repoPath.trim(), state.commitSummary, state.commitDescription)
                .onSuccess { commitOutput ->
                    if (andPush) {
                        _uiState.value = _uiState.value.copy(statusMessage = "Pushing...", isError = false)
                        gitService.push(state.repoPath.trim())
                            .onSuccess { pushOutput ->
                                _uiState.value = _uiState.value.copy(
                                    isLoading = false,
                                    fileSummary = "",
                                    fullDiff = "",
                                    commitSummary = "",
                                    commitDescription = "",
                                    statusMessage = "Committed and pushed successfully!\n$commitOutput\n$pushOutput",
                                    isError = false,
                                )
                            }
                            .onFailure { e ->
                                _uiState.value = _uiState.value.copy(
                                    isLoading = false,
                                    fileSummary = "",
                                    fullDiff = "",
                                    commitSummary = "",
                                    commitDescription = "",
                                    statusMessage = "Committed but push failed: ${e.message}",
                                    isError = true,
                                )
                            }
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            fileSummary = "",
                            fullDiff = "",
                            commitSummary = "",
                            commitDescription = "",
                            statusMessage = "Committed successfully!\n$commitOutput",
                            isError = false,
                        )
                    }
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
