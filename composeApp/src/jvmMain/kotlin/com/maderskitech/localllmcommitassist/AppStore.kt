package com.maderskitech.localllmcommitassist

import java.util.prefs.Preferences

object AppStore {
    private val prefs = Preferences.userRoot().node("com.maderskitech.localllmcommitassist")

    fun loadSettings(): AppSettings {
        return AppSettings(
            baseUrl = prefs.get("baseUrl", "http://localhost:11434/v1"),
            model = prefs.get("model", "gpt-4o-mini")
        )
    }

    fun saveSettings(settings: AppSettings) {
        prefs.put("baseUrl", settings.baseUrl)
        prefs.put("model", settings.model)
    }

    fun loadProjects(): List<Project> {
        val raw = prefs.get("projects", "")
        if (raw.isBlank()) return emptyList()

        return raw
            .split("\n")
            .mapNotNull { line ->
                val split = line.split("|", limit = 2)
                if (split.size != 2) return@mapNotNull null
                Project(name = split[0], path = split[1])
            }
    }

    fun saveProjects(projects: List<Project>) {
        val serialized = projects.joinToString("\n") { "${it.name}|${it.path}" }
        prefs.put("projects", serialized)
    }
}
