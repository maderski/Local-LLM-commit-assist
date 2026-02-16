package com.maderskitech.localllmcommitassist

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    val icon = createAppIcon()

    Window(
        onCloseRequest = ::exitApplication,
        title = "Local LLM Commit Assist",
        icon = icon,
    ) {
        App()
    }
}
