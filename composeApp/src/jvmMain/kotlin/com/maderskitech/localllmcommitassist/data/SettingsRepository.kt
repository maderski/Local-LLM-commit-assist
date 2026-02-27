package com.maderskitech.localllmcommitassist.data

import java.util.prefs.Preferences

class SettingsRepository {
    private val prefs = Preferences.userNodeForPackage(SettingsRepository::class.java)

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

    fun getGitHubToken(): String = prefs.get(KEY_GITHUB_TOKEN, "")

    fun setGitHubToken(token: String) {
        prefs.put(KEY_GITHUB_TOKEN, token)
    }

    fun getAzureDevOpsUsername(): String = prefs.get(KEY_AZURE_DEVOPS_USERNAME, "")

    fun setAzureDevOpsUsername(username: String) {
        prefs.put(KEY_AZURE_DEVOPS_USERNAME, username)
    }

    fun getAzureDevOpsToken(): String = prefs.get(KEY_AZURE_DEVOPS_TOKEN, "")

    fun setAzureDevOpsToken(token: String) {
        prefs.put(KEY_AZURE_DEVOPS_TOKEN, token)
    }

    fun getPrTargetBranch(): String = prefs.get(KEY_PR_TARGET_BRANCH, DEFAULT_PR_TARGET_BRANCH)

    fun setPrTargetBranch(branch: String) {
        prefs.put(KEY_PR_TARGET_BRANCH, branch)
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
        private const val KEY_PR_TARGET_BRANCH = "pr_target_branch"
        private const val DEFAULT_LLM_ADDRESS = "http://localhost:1234/v1"
        private const val DEFAULT_MODEL_NAME = ""
        private const val DEFAULT_PR_PLATFORM = "github"
        private const val DEFAULT_PR_TARGET_BRANCH = "main"
        private const val SEPARATOR = "\n"
    }
}
