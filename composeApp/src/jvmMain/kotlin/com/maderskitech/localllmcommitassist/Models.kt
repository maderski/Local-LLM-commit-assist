package com.maderskitech.localllmcommitassist

data class AppSettings(
    val baseUrl: String = "http://localhost:11434/v1",
    val model: String = "gpt-4o-mini"
)

data class Project(
    val name: String,
    val path: String
)

data class GeneratedCommit(
    val summary: String,
    val description: String
)

enum class AppScreen {
    Main,
    Settings
}
