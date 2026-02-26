package com.maderskitech.localllmcommitassist

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application

fun main() = application {
    val icon = createAppIcon()

    Window(
        onCloseRequest = ::exitApplication,
        title = "Local LLM Commit Assist",
        icon = icon,
        state = WindowState(size = DpSize(900.dp, 1000.dp)),
    ) {
        App()
    }
}
