package com.maderskitech.localllmcommitassist.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maderskitech.localllmcommitassist.data.AttachmentConfig
import com.maderskitech.localllmcommitassist.data.GitService
import com.maderskitech.localllmcommitassist.data.LlmService
import com.maderskitech.localllmcommitassist.data.PrAttachment
import com.maderskitech.localllmcommitassist.data.GitHubPrResult
import com.maderskitech.localllmcommitassist.data.PrService
import com.maderskitech.localllmcommitassist.data.SettingsRepository
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class SelectableFile(
    val path: String,
    val status: String,
    val isSelected: Boolean = true,
)

data class MainUiState(
    val savedProjects: List<String> = emptyList(),
    val repoPath: String = "",
    val prPlatform: String = "github",
    val currentBranch: String = "",
    val availableBranches: List<String> = emptyList(),
    val prTargetBranch: String = "",
    val changedFiles: List<SelectableFile> = emptyList(),
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
    val showAttachmentValidationDialog: Boolean = false,
    val attachmentValidationTitle: String = "",
    val attachmentValidationMessage: String = "",
    val showPublishBranchDialog: Boolean = false,
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
            prPlatform = settingsRepository.getPrPlatform(),
            prTargetBranch = settingsRepository.getPrTargetBranch(),
        )
    )
    val uiState: StateFlow<MainUiState> = _uiState

    private val stashedBranches = mutableSetOf<String>()

    init {
        loadCurrentBranch(_uiState.value.repoPath)
        loadBranches(_uiState.value.repoPath)
        loadChangedFiles()
    }

    fun selectProject(path: String) {
        settingsRepository.setSelectedProject(path)
        deleteTempAttachmentFiles(_uiState.value.prAttachments)
        _uiState.value = _uiState.value.copy(
            repoPath = path,
            currentBranch = "",
            availableBranches = emptyList(),
            changedFiles = emptyList(),
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
        loadDefaultBranchAsPrTarget(path)
        loadChangedFiles()
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

    fun refreshAll() {
        val path = _uiState.value.repoPath
        loadCurrentBranch(path)
        loadBranches(path)
    }

    fun refreshSettings() {
        _uiState.value = _uiState.value.copy(
            prPlatform = settingsRepository.getPrPlatform(),
        )
    }

    private fun loadBranches(path: String) {
        if (path.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val branches = gitService.getLocalBranches(path).getOrDefault(emptyList())
            _uiState.value = _uiState.value.copy(availableBranches = branches)
        }
    }

    private fun loadDefaultBranchAsPrTarget(path: String) {
        if (path.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val defaultBranch = gitService.getDefaultBranch(path).getOrNull() ?: return@launch
            settingsRepository.setPrTargetBranch(defaultBranch)
            _uiState.value = _uiState.value.copy(prTargetBranch = defaultBranch)
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
                    changedFiles = emptyList(),
                    fileSummary = "",
                    fullDiff = "",
                    commitSummary = "",
                    commitDescription = "",
                    statusMessage = statusMessage,
                    isError = false,
                )
                loadChangedFiles()
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
        deleteTempAttachmentFiles(_uiState.value.prAttachments)
        _uiState.value = _uiState.value.copy(
            savedProjects = settingsRepository.getSavedProjects(),
            repoPath = path,
            currentBranch = "",
            availableBranches = emptyList(),
            changedFiles = emptyList(),
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
        loadDefaultBranchAsPrTarget(path)
        loadChangedFiles()
    }

    fun removeProject(path: String) {
        settingsRepository.removeProject(path)
        val projects = settingsRepository.getSavedProjects()
        val newSelected = if (_uiState.value.repoPath == path) projects.firstOrNull().orEmpty() else _uiState.value.repoPath
        settingsRepository.setSelectedProject(newSelected)
        deleteTempAttachmentFiles(_uiState.value.prAttachments)
        _uiState.value = _uiState.value.copy(
            savedProjects = projects,
            repoPath = newSelected,
            currentBranch = "",
            availableBranches = emptyList(),
            changedFiles = emptyList(),
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
        loadDefaultBranchAsPrTarget(newSelected)
        loadChangedFiles()
    }

    fun updateCommitSummary(summary: String) {
        _uiState.value = _uiState.value.copy(commitSummary = summary)
    }

    fun updateCommitDescription(description: String) {
        _uiState.value = _uiState.value.copy(commitDescription = description)
    }

    fun loadChangedFiles() {
        val path = _uiState.value.repoPath.trim()
        if (path.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            gitService.getChangedFiles(path)
                .onSuccess { files ->
                    val currentSelected = _uiState.value.changedFiles
                        .filter { it.isSelected }
                        .map { it.path }
                        .toSet()
                    val selectableFiles = files.map { (filePath, status) ->
                        SelectableFile(
                            path = filePath,
                            status = status,
                            isSelected = if (currentSelected.isEmpty()) true else filePath in currentSelected,
                        )
                    }
                    _uiState.value = _uiState.value.copy(changedFiles = selectableFiles)
                }
        }
    }

    fun toggleFileSelection(path: String) {
        _uiState.value = _uiState.value.copy(
            changedFiles = _uiState.value.changedFiles.map {
                if (it.path == path) it.copy(isSelected = !it.isSelected) else it
            },
        )
    }

    fun selectAllFiles() {
        _uiState.value = _uiState.value.copy(
            changedFiles = _uiState.value.changedFiles.map { it.copy(isSelected = true) },
        )
    }

    fun unselectAllFiles() {
        _uiState.value = _uiState.value.copy(
            changedFiles = _uiState.value.changedFiles.map { it.copy(isSelected = false) },
        )
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

        val selectedFiles = _uiState.value.changedFiles.filter { it.isSelected }
        if (selectedFiles.isEmpty()) {
            _uiState.value = _uiState.value.copy(statusMessage = "No files selected", isError = true)
            return
        }

        val address = settingsRepository.getLlmAddress()
        val model = settingsRepository.getModelName()

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true, statusMessage = "Loading diff...", isError = false)

            val selectedPaths = selectedFiles.map { it.path }
            val diff = loadDiffForFiles(path, selectedPaths)
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
            _uiState.value = _uiState.value.copy(isLoading = true, statusMessage = "Loading commit history...", isError = false, prUrl = "")

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

    fun addAttachments(files: List<File>, isTempFile: Boolean = false) {
        val platform = settingsRepository.getPrPlatform()
        val maxSize = AttachmentConfig.maxSizeForPlatform(platform)
        val maxLabel = AttachmentConfig.maxSizeLabelForPlatform(platform)
        val platformLabel = if (platform == "azure_devops") "Azure DevOps" else "GitHub"
        val existingPaths = _uiState.value.prAttachments.map { it.file.absolutePath }.toMutableSet()
        val addedAttachments = mutableListOf<PrAttachment>()
        val unsupportedFiles = mutableListOf<String>()
        val duplicateFiles = mutableListOf<String>()
        val oversizedFiles = mutableListOf<String>()

        for (file in files) {
            val extension = file.extension.lowercase()
            when {
                extension !in AttachmentConfig.allowedExtensions -> {
                    unsupportedFiles += file.name
                    if (isTempFile) file.delete()
                }
                file.absolutePath in existingPaths -> {
                    duplicateFiles += file.name
                    if (isTempFile) file.delete()
                }
                file.length() > maxSize -> {
                    val sizeMb = "%.1f".format(file.length().toDouble() / (1024 * 1024))
                    oversizedFiles += "${file.name} ($sizeMb MB)"
                    if (isTempFile) file.delete()
                }
                else -> {
                    addedAttachments += PrAttachment(file = file, isTempFile = isTempFile)
                    existingPaths += file.absolutePath
                }
            }
        }

        if (addedAttachments.isNotEmpty()) {
            val updatedAttachments = _uiState.value.prAttachments + addedAttachments
            val addedCount = addedAttachments.size
            val statusMessage = if (unsupportedFiles.isEmpty() && duplicateFiles.isEmpty() && oversizedFiles.isEmpty()) {
                if (addedCount == 1) {
                    "Added attachment '${addedAttachments.first().name}'."
                } else {
                    "Added $addedCount attachments."
                }
            } else {
                "Added $addedCount attachment${if (addedCount == 1) "" else "s"} with some files skipped."
            }
            _uiState.value = _uiState.value.copy(
                prAttachments = updatedAttachments,
                statusMessage = statusMessage,
                isError = false,
            )
        }

        if (unsupportedFiles.isNotEmpty() || duplicateFiles.isNotEmpty() || oversizedFiles.isNotEmpty()) {
            val messageParts = buildList {
                if (unsupportedFiles.isNotEmpty()) {
                    add("Unsupported files: ${unsupportedFiles.joinToString(", ")}. Supported types: ${AttachmentConfig.allowedExtensions.sorted().joinToString(", ")}.")
                }
                if (duplicateFiles.isNotEmpty()) {
                    add("Already attached: ${duplicateFiles.joinToString(", ")}.")
                }
                if (oversizedFiles.isNotEmpty()) {
                    add("Over $maxLabel for $platformLabel: ${oversizedFiles.joinToString(", ")}.")
                }
            }

            _uiState.value = _uiState.value.copy(
                showAttachmentValidationDialog = true,
                attachmentValidationTitle = if (oversizedFiles.isNotEmpty() && unsupportedFiles.isEmpty() && duplicateFiles.isEmpty()) {
                    "Attachment Too Large"
                } else {
                    "Attachment Issues"
                },
                attachmentValidationMessage = messageParts.joinToString("\n\n"),
                isError = addedAttachments.isEmpty(),
                statusMessage = if (addedAttachments.isEmpty()) {
                    "No attachments were added."
                } else {
                    _uiState.value.statusMessage
                },
            )
        }
    }

    fun removeAttachment(id: String) {
        val attachment = _uiState.value.prAttachments.find { it.id == id }
        if (attachment?.isTempFile == true) attachment.file.delete()
        _uiState.value = _uiState.value.copy(
            prAttachments = _uiState.value.prAttachments.filter { it.id != id },
        )
    }

    private fun deleteTempAttachmentFiles(attachments: List<PrAttachment>) {
        attachments.filter { it.isTempFile }.forEach { it.file.delete() }
    }

    fun showAttachmentStatus(message: String, isError: Boolean) {
        _uiState.value = _uiState.value.copy(
            statusMessage = message,
            isError = isError,
        )
    }

    fun dismissAttachmentValidationDialog() {
        _uiState.value = _uiState.value.copy(
            showAttachmentValidationDialog = false,
            attachmentValidationTitle = "",
            attachmentValidationMessage = "",
        )
    }

    fun dismissPublishBranchDialog() {
        _uiState.value = _uiState.value.copy(showPublishBranchDialog = false)
    }

    fun confirmPublishAndPush() {
        val state = _uiState.value
        _uiState.value = state.copy(showPublishBranchDialog = false, isLoading = true, statusMessage = "Publishing branch and pushing...")
        viewModelScope.launch(Dispatchers.IO) {
            gitService.publishBranch(state.repoPath.trim(), state.currentBranch)
                .onSuccess { output ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isCurrentBranchPublished = true,
                        statusMessage = "Branch published and pushed successfully!\n$output",
                        isError = false,
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        statusMessage = "Failed to publish branch: ${e.message}",
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

            val hasCommits = gitService.hasCommitsAheadOfBranch(path, targetBranch)
            if (hasCommits.isFailure || hasCommits.getOrDefault(false) == false) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    statusMessage = "No changes to create a PR: branch '$currentBranch' has no commits ahead of '$targetBranch'",
                    isError = true,
                )
                return@launch
            }

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
                val imagesMarkdown = markdownReferences.joinToString("\n")
                val evidenceRegex = Regex("(## Evidence[^\n]*\n)", RegexOption.IGNORE_CASE)
                val match = evidenceRegex.find(state.prBody)
                if (match != null) {
                    val insertPos = match.range.last + 1
                    state.prBody.substring(0, insertPos) + "\n" + imagesMarkdown + "\n\n" +
                        state.prBody.substring(insertPos)
                } else {
                    state.prBody + "\n\n" + imagesMarkdown
                }
            } else {
                state.prBody
            }

            _uiState.value = _uiState.value.copy(statusMessage = "Creating pull request...", isError = false)

            when (platform) {
                "github" -> {
                    val token = settingsRepository.getGitHubToken()
                    val parsed = prService.parseGitHubRemote(remoteUrl)
                    if (parsed == null) {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            statusMessage = "PR creation failed: Could not parse GitHub remote URL: $remoteUrl",
                            isError = true,
                        )
                        return@launch
                    }
                    val reviewers = settingsRepository.getGitHubReviewers().map { it.login }
                    prService.createGitHubPr(
                        token = token,
                        owner = parsed.first,
                        repo = parsed.second,
                        title = state.prTitle,
                        body = augmentedBody,
                        head = currentBranch,
                        base = targetBranch,
                        reviewers = reviewers,
                    ).onSuccess { result ->
                        val message = if (result.reviewerWarning != null) {
                            "Pull request created successfully! Warning: ${result.reviewerWarning}"
                        } else {
                            "Pull request created successfully!"
                        }
                        deleteTempAttachmentFiles(_uiState.value.prAttachments)
                        _uiState.value = _uiState.value.copy(
                            prUrl = result.url,
                            isLoading = false,
                            statusMessage = message,
                            isError = result.reviewerWarning != null,
                            prAttachments = emptyList(),
                        )
                    }.onFailure { e ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            statusMessage = "PR creation failed: ${e.message}",
                            isError = true,
                        )
                    }
                }
                "azure_devops" -> {
                    val token = settingsRepository.getAzureDevOpsToken()
                    val username = settingsRepository.getAzureDevOpsUsername()
                    val parsed = prService.parseAzureDevOpsRemote(remoteUrl)
                    if (parsed == null) {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            statusMessage = "PR creation failed: Could not parse Azure DevOps remote URL: $remoteUrl",
                            isError = true,
                        )
                        return@launch
                    }
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
                    ).onSuccess { url ->
                        deleteTempAttachmentFiles(_uiState.value.prAttachments)
                        _uiState.value = _uiState.value.copy(
                            prUrl = url,
                            isLoading = false,
                            statusMessage = "Pull request created successfully!",
                            isError = false,
                            prAttachments = emptyList(),
                        )
                        if (settingsRepository.getAzureUpdateWorkItemStatus() && workItemIds.isNotEmpty()) {
                            val failedWorkItems = mutableListOf<String>()
                            workItemIds.forEach { workItemId ->
                                prService.updateAzureWorkItemState(
                                    token = token,
                                    username = username,
                                    orgUrl = parsed.first,
                                    project = parsed.second,
                                    workItemId = workItemId,
                                    state = "Ready for QA",
                                ).onFailure { e ->
                                    failedWorkItems.add("$workItemId: ${e.message}")
                                }
                            }
                            if (failedWorkItems.isNotEmpty()) {
                                val message = "Pull request created successfully! Warning: failed to update ${failedWorkItems.size} work item${if (failedWorkItems.size > 1) "s" else ""} to Ready for QA: ${failedWorkItems.joinToString("; ")}"
                                _uiState.value = _uiState.value.copy(
                                    statusMessage = message,
                                    isError = true,
                                )
                            }
                        }
                    }.onFailure { e ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            statusMessage = "PR creation failed: ${e.message}",
                            isError = true,
                        )
                    }
                }
                else -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        statusMessage = "PR creation failed: Unknown platform: $platform",
                        isError = true,
                    )
                }
            }
        }
    }

    private fun loadDiffForFiles(path: String, files: List<String>): String? {
        _uiState.value = _uiState.value.copy(statusMessage = "Staging selected files...", isError = false)

        gitService.unstageAll(path)
        gitService.stageFiles(path, files).onFailure { e ->
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
                statusMessage = "No changes found for selected files.",
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

    fun openFinder() {
        val path = _uiState.value.repoPath.trim()
        if (path.isBlank()) return
        runCatching {
            ProcessBuilder("open", path).start()
        }
    }

    fun commit(andPush: Boolean = false) {
        val state = _uiState.value
        if (state.commitSummary.isBlank()) {
            _uiState.value = state.copy(statusMessage = "Commit summary is empty", isError = true)
            return
        }

        val selectedFiles = state.changedFiles.filter { it.isSelected }
        if (selectedFiles.isEmpty()) {
            _uiState.value = state.copy(statusMessage = "No files selected to commit", isError = true)
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true, statusMessage = "", isError = false)

            val path = state.repoPath.trim()
            gitService.unstageAll(path)
            gitService.stageFiles(path, selectedFiles.map { it.path }).onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    statusMessage = "Failed to stage files: ${e.message}",
                    isError = true,
                )
                return@launch
            }

            gitService.commit(path, state.commitSummary, state.commitDescription)
                .onSuccess { commitOutput ->
                    if (andPush) {
                        _uiState.value = _uiState.value.copy(statusMessage = "Pushing...", isError = false)
                        gitService.push(state.repoPath.trim())
                            .onSuccess { pushOutput ->
                                _uiState.value = _uiState.value.copy(
                                    isLoading = false,
                                    changedFiles = emptyList(),
                                    fileSummary = "",
                                    fullDiff = "",
                                    commitSummary = "",
                                    commitDescription = "",
                                    statusMessage = "Committed and pushed successfully!\n$commitOutput\n$pushOutput",
                                    isError = false,
                                )
                                loadChangedFiles()
                            }
                            .onFailure { e ->
                                val isNoUpstream = e.message?.contains("has no upstream branch") == true
                                val isNonFastForward = e.message?.contains("non-fast-forward") == true ||
                                    e.message?.contains("rejected") == true
                                if (isNonFastForward) {
                                    _uiState.value = _uiState.value.copy(statusMessage = "Push rejected, rebasing on remote changes...")
                                    gitService.pullRebase(state.repoPath.trim())
                                        .onSuccess {
                                            gitService.push(state.repoPath.trim())
                                                .onSuccess { pushOutput ->
                                                    _uiState.value = _uiState.value.copy(
                                                        isLoading = false,
                                                        changedFiles = emptyList(),
                                                        fileSummary = "",
                                                        fullDiff = "",
                                                        commitSummary = "",
                                                        commitDescription = "",
                                                        statusMessage = "Committed and pushed successfully (after rebase)!\n$commitOutput\n$pushOutput",
                                                        isError = false,
                                                    )
                                                    loadChangedFiles()
                                                }
                                                .onFailure { pushError ->
                                                    _uiState.value = _uiState.value.copy(
                                                        isLoading = false,
                                                        statusMessage = "Committed but push failed after rebase: ${pushError.message}",
                                                        isError = true,
                                                    )
                                                }
                                        }
                                        .onFailure { rebaseError ->
                                            _uiState.value = _uiState.value.copy(
                                                isLoading = false,
                                                statusMessage = "Committed but rebase failed: ${rebaseError.message}",
                                                isError = true,
                                            )
                                        }
                                } else {
                                    _uiState.value = _uiState.value.copy(
                                        isLoading = false,
                                        changedFiles = emptyList(),
                                        fileSummary = "",
                                        fullDiff = "",
                                        commitSummary = "",
                                        commitDescription = "",
                                        statusMessage = if (isNoUpstream) "Committed successfully. Branch has not been published yet."
                                            else "Committed but push failed: ${e.message}",
                                        isError = !isNoUpstream,
                                        showPublishBranchDialog = isNoUpstream,
                                    )
                                    loadChangedFiles()
                                }
                            }
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            changedFiles = emptyList(),
                            fileSummary = "",
                            fullDiff = "",
                            commitSummary = "",
                            commitDescription = "",
                            statusMessage = "Committed successfully!\n$commitOutput",
                            isError = false,
                        )
                        loadChangedFiles()
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
