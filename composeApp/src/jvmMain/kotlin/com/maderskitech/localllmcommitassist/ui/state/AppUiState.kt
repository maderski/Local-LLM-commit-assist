package com.maderskitech.localllmcommitassist.ui.state

import com.maderskitech.localllmcommitassist.model.AppScreen
import com.maderskitech.localllmcommitassist.model.AppSettings
import com.maderskitech.localllmcommitassist.model.Project

data class AppUiState(
    val currentScreen: AppScreen = AppScreen.Main,
    val settings: AppSettings = AppSettings(),
    val settingsBaseUrlInput: String = settings.baseUrl,
    val settingsModelInput: String = settings.model,
    val settingsStatus: String? = null,
    val projects: List<Project> = emptyList(),
    val selectedProjectPath: String = "",
    val dropdownExpanded: Boolean = false,
    val additionalContextInput: String = "",
    val generatedSummary: String = "",
    val generatedDescription: String = "",
    val generationStatus: String? = null,
) {
    val selectedProject: Project?
        get() = projects.firstOrNull { it.path == selectedProjectPath }
}
