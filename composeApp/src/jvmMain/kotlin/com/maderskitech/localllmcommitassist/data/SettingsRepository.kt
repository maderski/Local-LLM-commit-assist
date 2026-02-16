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

    companion object {
        private const val KEY_LLM_ADDRESS = "llm_address"
        private const val KEY_MODEL_NAME = "model_name"
        private const val KEY_PROJECTS = "saved_projects"
        private const val KEY_SELECTED_PROJECT = "selected_project"
        private const val DEFAULT_LLM_ADDRESS = "http://localhost:1234/v1"
        private const val DEFAULT_MODEL_NAME = ""
        private const val SEPARATOR = "\n"
    }
}
