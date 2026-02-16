package com.maderskitech.localllmcommitassist

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.maderskitech.localllmcommitassist.ui.App

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Local LLM Commit Assist",
    ) {
        App()
    }
}
