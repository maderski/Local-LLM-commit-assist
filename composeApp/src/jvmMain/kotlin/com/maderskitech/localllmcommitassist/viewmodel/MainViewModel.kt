package com.maderskitech.localllmcommitassist.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maderskitech.localllmcommitassist.data.AttachmentConfig
import com.maderskitech.localllmcommitassist.data.GitService
import com.maderskitech.localllmcommitassist.data.LlmService
import com.maderskitech.localllmcommitassist.data.PrAttachment
import com.maderskitech.localllmcommitassist.data.PrService
import com.maderskitech.localllmcommitassist.data.SettingsRepository
import java.io.File
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
    val prTemplate: String = "",
    val isCurrentBranchPublished: Boolean = false,
    val showBranchSwitchDialog: Boolean = false,
    val pendingSwitchBranch: String = "",
    val isPendingBranchCreate: Boolean = false,
    val prAttachments: List<PrAttachment> = emptyList(),
    val showFileSizeErrorDialog: Boolean = false,
    val fileSizeErrorMessage: String = "",
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

    private val stashedBranches = mutableSetOf<String>()

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
            prAttachments = emptyList(),
        )
        loadCurrentBranch(path)
        loadBranches(path)
    }

    private fun loadCurrentBranch(path: String) {
        if (path.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val branch = gitService.getCurrentBranch(path).getOrDefault("")
            val published = if (branch.isNotBlank()) {
                gitService.hasUpstreamBranch(path, branch).getOrDefault(false)
            } else false
            _uiState.value = _uiState.value.copy(currentBranch = branch, isCurrentBranchPublished = published)
        }
    }

    fun refreshBranches() {
        loadBranches(_uiState.value.repoPath)
    }

    private fun loadBranches(path: String) {
        if (path.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val branches = gitService.getLocalBranches(path).getOrDefault(emptyList())
            _uiState.value = _uiState.value.copy(availableBranches = branches)
        }
    }

    fun switchBranch(branch: String) {
        val path = _uiState.value.repoPath.trim()
        if (path.isBlank() || branch == _uiState.value.currentBranch) return
        viewModelScope.launch(Dispatchers.IO) {
            val hasChanges = gitService.hasUncommittedChanges(path).getOrDefault(false)
            if (hasChanges) {
                _uiState.value = _uiState.value.copy(
                    showBranchSwitchDialog = true,
                    pendingSwitchBranch = branch,
                )
            } else {
                performCheckout(path, branch)
            }
        }
    }

    private fun clearBranchDialogState() = _uiState.value.copy(
        showBranchSwitchDialog = false,
        pendingSwitchBranch = "",
        isPendingBranchCreate = false,
    )

    private suspend fun performCheckout(path: String, branch: String) {
        gitService.checkoutBranch(path, branch)
            .onSuccess {
                val published = gitService.hasUpstreamBranch(path, branch).getOrDefault(false)
                var statusMessage = "Switched to branch '$branch'"

                if (branch in stashedBranches) {
                    gitService.stashPop(path)
                        .onSuccess {
                            stashedBranches.remove(branch)
                            statusMessage = "Switched to branch '$branch' — stashed changes restored"
                        }
                        .onFailure {
                            statusMessage = "Switched to branch '$branch' — warning: failed to restore stashed changes: ${it.message}"
                        }
                }

                _uiState.value = clearBranchDialogState().copy(
                    currentBranch = branch,
                    isCurrentBranchPublished = published,
                    fileSummary = "",
                    fullDiff = "",
                    commitSummary = "",
                    commitDescription = "",
                    statusMessage = statusMessage,
                    isError = false,
                )
            }
            .onFailure { e ->
                _uiState.value = clearBranchDialogState().copy(
                    statusMessage = "Branch switch failed: ${e.message}",
                    isError = true,
                )
            }
    }

    private suspend fun performCreateBranch(path: String, branchName: String) {
        gitService.createBranch(path, branchName)
            .onSuccess {
                _uiState.value = clearBranchDialogState().copy(
                    currentBranch = branchName,
                    isCurrentBranchPublished = false,
                    statusMessage = "Created and switched to branch '$branchName'",
                    isError = false,
                )
                loadBranches(path)
            }
            .onFailure { e ->
                _uiState.value = clearBranchDialogState().copy(
                    statusMessage = "Failed to create branch: ${e.message}",
                    isError = true,
                )
            }
    }

    fun onBranchSwitchBringChanges() {
        val state = _uiState.value
        val path = state.repoPath.trim()
        val branch = state.pendingSwitchBranch
        if (path.isBlank() || branch.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            if (state.isPendingBranchCreate) {
                performCreateBranch(path, branch)
            } else {
                performCheckout(path, branch)
            }
        }
    }

    fun onBranchSwitchLeaveChanges() {
        val state = _uiState.value
        val path = state.repoPath.trim()
        val branch = state.pendingSwitchBranch
        if (path.isBlank() || branch.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            gitService.stashChanges(path)
                .onSuccess {
                    val currentBranch = _uiState.value.currentBranch
                    if (currentBranch.isNotBlank()) {
                        stashedBranches.add(currentBranch)
                    }
                    if (state.isPendingBranchCreate) {
                        performCreateBranch(path, branch)
                    } else {
                        performCheckout(path, branch)
                    }
                }
                .onFailure { e ->
                    _uiState.value = clearBranchDialogState().copy(
                        statusMessage = "Stash failed: ${e.message}",
                        isError = true,
                    )
                }
        }
    }

    fun dismissBranchSwitchDialog() {
        _uiState.value = clearBranchDialogState()
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
            prAttachments = emptyList(),
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
            prAttachments = emptyList(),
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

            val platform = settingsRepository.getPrPlatform()
            val template = gitService.readPrTemplate(path, platform).getOrNull()
            _uiState.value = _uiState.value.copy(prTemplate = template.orEmpty())

            val currentBranch = _uiState.value.currentBranch
            _uiState.value = _uiState.value.copy(statusMessage = "Generating PR description...", isError = false)

            llmService.generatePrDescription(address, model, commitLog, currentBranch, template)
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

    fun addAttachments(files: List<File>) {
        val platform = settingsRepository.getPrPlatform()
        val maxSize = AttachmentConfig.maxSizeForPlatform(platform)
        val maxLabel = AttachmentConfig.maxSizeLabelForPlatform(platform)
        val existingPaths = _uiState.value.prAttachments.map { it.file.absolutePath }.toSet()

        for (file in files) {
            if (file.extension.lowercase() !in AttachmentConfig.allowedExtensions) continue
            if (file.absolutePath in existingPaths) continue
            if (file.length() > maxSize) {
                val sizeMb = "%.1f".format(file.length().toDouble() / (1024 * 1024))
                _uiState.value = _uiState.value.copy(
                    showFileSizeErrorDialog = true,
                    fileSizeErrorMessage = "'${file.name}' ($sizeMb MB) exceeds the $maxLabel limit for ${if (platform == "azure_devops") "Azure DevOps" else "GitHub"}.",
                )
                return
            }
            val attachment = PrAttachment(file = file)
            _uiState.value = _uiState.value.copy(
                prAttachments = _uiState.value.prAttachments + attachment,
            )
        }
    }

    fun removeAttachment(id: String) {
        _uiState.value = _uiState.value.copy(
            prAttachments = _uiState.value.prAttachments.filter { it.id != id },
        )
    }

    fun dismissFileSizeErrorDialog() {
        _uiState.value = _uiState.value.copy(showFileSizeErrorDialog = false, fileSizeErrorMessage = "")
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

            // Upload attachments and build augmented PR body
            val attachments = _uiState.value.prAttachments
            val markdownReferences = mutableListOf<String>()
            if (attachments.isNotEmpty()) {
                for ((index, attachment) in attachments.withIndex()) {
                    _uiState.value = _uiState.value.copy(
                        statusMessage = "Uploading attachment ${index + 1} of ${attachments.size}...",
                    )
                    val uploadResult = when (platform) {
                        "github" -> {
                            val token = settingsRepository.getGitHubToken()
                            val parsed = prService.parseGitHubRemote(remoteUrl)
                            if (parsed == null) {
                                Result.failure(Exception("Could not parse GitHub remote URL"))
                            } else {
                                prService.uploadFileToGitHubRepo(
                                    token = token,
                                    owner = parsed.first,
                                    repo = parsed.second,
                                    branch = currentBranch,
                                    attachment = attachment,
                                )
                            }
                        }
                        "azure_devops" -> {
                            val token = settingsRepository.getAzureDevOpsToken()
                            val username = settingsRepository.getAzureDevOpsUsername()
                            val parsed = prService.parseAzureDevOpsRemote(remoteUrl)
                            if (parsed == null) {
                                Result.failure(Exception("Could not parse Azure DevOps remote URL"))
                            } else {
                                prService.uploadAzureDevOpsAttachment(
                                    token = token,
                                    username = username,
                                    orgUrl = parsed.first,
                                    project = parsed.second,
                                    attachment = attachment,
                                )
                            }
                        }
                        else -> Result.failure(Exception("Unknown platform: $platform"))
                    }
                    uploadResult.onFailure { e ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            statusMessage = "Failed to upload '${attachment.name}': ${e.message}",
                            isError = true,
                        )
                        return@launch
                    }
                    val downloadUrl = uploadResult.getOrThrow()
                    markdownReferences.add(prService.buildMarkdownReference(attachment, downloadUrl))
                }
            }

            val augmentedBody = if (markdownReferences.isNotEmpty()) {
                state.prBody + "\n\n" + markdownReferences.joinToString("\n")
            } else {
                state.prBody
            }

            _uiState.value = _uiState.value.copy(statusMessage = "Creating pull request...", isError = false)

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
                            body = augmentedBody,
                            head = currentBranch,
                            base = targetBranch,
                        )
                    }
                }
                "azure_devops" -> {
                    val token = settingsRepository.getAzureDevOpsToken()
                    val username = settingsRepository.getAzureDevOpsUsername()
                    val parsed = prService.parseAzureDevOpsRemote(remoteUrl)
                    if (parsed == null) {
                        Result.failure(Exception("Could not parse Azure DevOps remote URL: $remoteUrl"))
                    } else {
                        val reviewers = settingsRepository.getAzureReviewers()
                        val workItemIds = if (settingsRepository.getAzureLinkWorkItems()) {
                            prService.extractWorkItemIds(currentBranch)
                        } else emptyList()
                        val tags = if (settingsRepository.getAzureAutoTag()) {
                            prService.inferTags(parsed.third)
                        } else emptyList()
                        prService.createAzureDevOpsPr(
                            token = token,
                            username = username,
                            orgUrl = parsed.first,
                            project = parsed.second,
                            repo = parsed.third,
                            title = state.prTitle,
                            description = augmentedBody,
                            sourceBranch = currentBranch,
                            targetBranch = targetBranch,
                            reviewers = reviewers,
                            workItemIds = workItemIds,
                            tags = tags,
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
                        prAttachments = emptyList(),
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
        _uiState.value = _uiState.value.copy(statusMessage = "Staging all changes...", isError = false)
        gitService.stageAll(path).onFailure { e ->
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                statusMessage = "Failed to stage files: ${e.message}",
                isError = true,
            )
            return null
        }

        val diffResult = gitService.getStagedDiff(path)
        diffResult.onFailure { e ->
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                statusMessage = "Failed to load diff: ${e.message}",
                isError = true,
            )
            return null
        }

        val diff = diffResult.getOrThrow()
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

        return diff
    }

    fun createBranch(branchName: String) {
        val path = _uiState.value.repoPath.trim()
        if (path.isBlank() || branchName.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val hasChanges = gitService.hasUncommittedChanges(path).getOrDefault(false)
            if (hasChanges) {
                _uiState.value = _uiState.value.copy(
                    showBranchSwitchDialog = true,
                    pendingSwitchBranch = branchName,
                    isPendingBranchCreate = true,
                )
            } else {
                performCreateBranch(path, branchName)
            }
        }
    }

    fun deleteCurrentBranch(deleteRemote: Boolean) {
        val path = _uiState.value.repoPath.trim()
        val branchName = _uiState.value.currentBranch
        if (path.isBlank() || branchName.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val defaultBranch = gitService.getDefaultBranch(path).getOrElse { e ->
                _uiState.value = _uiState.value.copy(
                    statusMessage = "Failed to determine default branch: ${e.message}",
                    isError = true,
                )
                return@launch
            }
            if (defaultBranch == branchName) {
                _uiState.value = _uiState.value.copy(
                    statusMessage = "Cannot delete the default branch",
                    isError = true,
                )
                return@launch
            }
            gitService.checkoutBranch(path, defaultBranch).getOrElse { e ->
                _uiState.value = _uiState.value.copy(
                    statusMessage = "Failed to switch to '$defaultBranch': ${e.message}",
                    isError = true,
                )
                return@launch
            }
            loadCurrentBranch(path)
            gitService.deleteBranch(path, branchName)
                .onSuccess {
                    if (deleteRemote) {
                        gitService.deleteRemoteBranch(path, branchName)
                            .onFailure { e ->
                                _uiState.value = _uiState.value.copy(
                                    statusMessage = "Deleted local branch '$branchName' but failed to delete remote: ${e.message}",
                                    isError = true,
                                )
                                loadBranches(path)
                                return@launch
                            }
                    }
                    _uiState.value = _uiState.value.copy(
                        statusMessage = "Deleted branch '$branchName'${if (deleteRemote) " (local and remote)" else ""}",
                        isError = false,
                    )
                    loadBranches(path)
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        statusMessage = "Failed to delete branch: ${e.message}",
                        isError = true,
                    )
                }
        }
    }

    fun publishBranch() {
        val path = _uiState.value.repoPath.trim()
        val branch = _uiState.value.currentBranch
        if (path.isBlank() || branch.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(statusMessage = "Publishing branch '$branch'...", isError = false)
            gitService.publishBranch(path, branch)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        isCurrentBranchPublished = true,
                        statusMessage = "Branch '$branch' published to origin",
                        isError = false,
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        statusMessage = "Failed to publish branch: ${e.message}",
                        isError = true,
                    )
                }
        }
    }

    fun fetchBranch() {
        val path = _uiState.value.repoPath.trim()
        if (path.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(statusMessage = "Fetching from origin...", isError = false)
            gitService.fetchBranch(path)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        statusMessage = "Fetch complete",
                        isError = false,
                    )
                    loadBranches(path)
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        statusMessage = "Fetch failed: ${e.message}",
                        isError = true,
                    )
                }
        }
    }

    fun openTerminal() {
        val path = _uiState.value.repoPath.trim()
        if (path.isBlank()) return
        runCatching {
            ProcessBuilder("open", "-a", "Terminal", path).start()
        }
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
