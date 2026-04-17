package com.maderskitech.localllmcommitassist.data

import java.util.prefs.Preferences

class SettingsRepository {
    private val prefs = Preferences.userNodeForPackage(SettingsRepository::class.java)

    private val encryptionKey: String by lazy {
        val existing = prefs.get(KEY_ENCRYPTION_KEY, "")
        if (existing.isNotEmpty()) {
            existing
        } else {
            val newKey = TokenEncryption.generateKey()
            prefs.put(KEY_ENCRYPTION_KEY, newKey)
            newKey
        }
    }

    private fun encryptToken(plaintext: String): String =
        TokenEncryption.encrypt(plaintext, encryptionKey)

    private fun decryptToken(prefsKey: String): String {
        val stored = prefs.get(prefsKey, "")
        if (stored.isEmpty()) return ""
        return try {
            TokenEncryption.decrypt(stored, encryptionKey)
        } catch (_: Exception) {
            // Stored value is plaintext (pre-encryption migration) — encrypt it in place
            prefs.put(prefsKey, TokenEncryption.encrypt(stored, encryptionKey))
            stored
        }
    }

    fun getLlmAddress(): String = prefs.get(KEY_LLM_ADDRESS, DEFAULT_LLM_ADDRESS)

    fun setLlmAddress(address: String) {
        prefs.put(KEY_LLM_ADDRESS, address)
    }

    fun getModelName(): String = prefs.get(KEY_MODEL_NAME, DEFAULT_MODEL_NAME)

    fun setModelName(name: String) {
        prefs.put(KEY_MODEL_NAME, name)
    }

    fun getSavedProjects(): List<String> {
        val raw = prefs.get(KEY_PROJECTS, "")
        if (raw.isBlank()) return emptyList()
        return raw.split(SEPARATOR)
    }

    fun addProject(path: String) {
        val projects = getSavedProjects().toMutableList()
        if (path !in projects) {
            projects.add(path)
            prefs.put(KEY_PROJECTS, projects.joinToString(SEPARATOR))
        }
    }

    fun removeProject(path: String) {
        val projects = getSavedProjects().toMutableList()
        projects.remove(path)
        prefs.put(KEY_PROJECTS, projects.joinToString(SEPARATOR))
    }

    fun getSelectedProject(): String = prefs.get(KEY_SELECTED_PROJECT, "")

    fun setSelectedProject(path: String) {
        prefs.put(KEY_SELECTED_PROJECT, path)
    }

    fun getPrPlatform(): String = prefs.get(KEY_PR_PLATFORM, DEFAULT_PR_PLATFORM)

    fun setPrPlatform(platform: String) {
        prefs.put(KEY_PR_PLATFORM, platform)
    }

    fun getGitHubToken(): String = decryptToken(KEY_GITHUB_TOKEN)

    fun setGitHubToken(token: String) {
        prefs.put(KEY_GITHUB_TOKEN, encryptToken(token))
    }

    fun getAzureDevOpsUsername(): String = prefs.get(KEY_AZURE_DEVOPS_USERNAME, "")

    fun setAzureDevOpsUsername(username: String) {
        prefs.put(KEY_AZURE_DEVOPS_USERNAME, username)
    }

    fun getAzureDevOpsToken(): String = decryptToken(KEY_AZURE_DEVOPS_TOKEN)

    fun setAzureDevOpsToken(token: String) {
        prefs.put(KEY_AZURE_DEVOPS_TOKEN, encryptToken(token))
    }

    fun getPrTargetBranch(): String = prefs.get(KEY_PR_TARGET_BRANCH, DEFAULT_PR_TARGET_BRANCH)

    fun setPrTargetBranch(branch: String) {
        prefs.put(KEY_PR_TARGET_BRANCH, branch)
    }

    fun getAzureReviewers(): List<AzureReviewer> {
        val raw = prefs.get(KEY_AZURE_REVIEWERS, "")
        if (raw.isBlank()) return emptyList()
        return raw.split(SEPARATOR).mapNotNull { line ->
            val parts = line.split(REVIEWER_FIELD_SEPARATOR)
            if (parts.size == 3) {
                AzureReviewer(
                    id = parts[0],
                    name = parts[1],
                    isRequired = parts[2].toBoolean(),
                )
            } else null
        }
    }

    fun setAzureReviewers(reviewers: List<AzureReviewer>) {
        val raw = reviewers.joinToString(SEPARATOR) { reviewer ->
            "${reviewer.id}${REVIEWER_FIELD_SEPARATOR}${reviewer.name}${REVIEWER_FIELD_SEPARATOR}${reviewer.isRequired}"
        }
        prefs.put(KEY_AZURE_REVIEWERS, raw)
    }

    fun getAzureLinkWorkItems(): Boolean = prefs.getBoolean(KEY_AZURE_LINK_WORK_ITEMS, false)

    fun setAzureLinkWorkItems(enabled: Boolean) {
        prefs.putBoolean(KEY_AZURE_LINK_WORK_ITEMS, enabled)
    }

    fun getAzureAutoTag(): Boolean = prefs.getBoolean(KEY_AZURE_AUTO_TAG, false)

    fun setAzureAutoTag(enabled: Boolean) {
        prefs.putBoolean(KEY_AZURE_AUTO_TAG, enabled)
    }

    fun getAzureUpdateWorkItemStatus(): Boolean = prefs.getBoolean(KEY_AZURE_UPDATE_WORK_ITEM_STATUS, false)

    fun setAzureUpdateWorkItemStatus(enabled: Boolean) {
        prefs.putBoolean(KEY_AZURE_UPDATE_WORK_ITEM_STATUS, enabled)
    }

    fun getGitHubReviewers(): List<GitHubReviewer> {
        val raw = prefs.get(KEY_GITHUB_REVIEWERS, "")
        if (raw.isBlank()) return emptyList()
        return raw.split(SEPARATOR).filter { it.isNotBlank() }.map { GitHubReviewer(login = it) }
    }

    fun setGitHubReviewers(reviewers: List<GitHubReviewer>) {
        prefs.put(KEY_GITHUB_REVIEWERS, reviewers.joinToString(SEPARATOR) { it.login })
    }

    companion object {
        private const val KEY_LLM_ADDRESS = "llm_address"
        private const val KEY_MODEL_NAME = "model_name"
        private const val KEY_PROJECTS = "saved_projects"
        private const val KEY_SELECTED_PROJECT = "selected_project"
        private const val KEY_PR_PLATFORM = "pr_platform"
        private const val KEY_GITHUB_TOKEN = "github_token"
        private const val KEY_AZURE_DEVOPS_USERNAME = "azure_devops_username"
        private const val KEY_AZURE_DEVOPS_TOKEN = "azure_devops_token"
        private const val KEY_ENCRYPTION_KEY = "encryption_key"
        private const val KEY_PR_TARGET_BRANCH = "pr_target_branch"
        private const val KEY_AZURE_REVIEWERS = "azure_reviewers"
        private const val KEY_GITHUB_REVIEWERS = "github_reviewers"
        private const val KEY_AZURE_LINK_WORK_ITEMS = "azure_link_work_items"
        private const val KEY_AZURE_AUTO_TAG = "azure_auto_tag"
        private const val KEY_AZURE_UPDATE_WORK_ITEM_STATUS = "azure_update_work_item_status"
        private const val DEFAULT_LLM_ADDRESS = "http://localhost:1234/v1"
        private const val DEFAULT_MODEL_NAME = ""
        private const val DEFAULT_PR_PLATFORM = "github"
        private const val DEFAULT_PR_TARGET_BRANCH = "main"
        private const val SEPARATOR = "\n"
        private const val REVIEWER_FIELD_SEPARATOR = "|"
    }
}

data class AzureReviewer(
    val id: String,
    val name: String,
    val isRequired: Boolean,
)

data class GitHubReviewer(val login: String)
