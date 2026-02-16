package com.maderskitech.localllmcommitassist.model

data class AppSettings(
    val baseUrl: String = "http://localhost:11434/v1",
    val model: String = "gpt-4o-mini"
)
