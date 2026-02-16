package com.maderskitech.localllmcommitassist.util

import javax.swing.JFileChooser

interface ProjectPicker {
    fun pickDirectory(): String?
}

object SwingProjectPicker : ProjectPicker {
    override fun pickDirectory(): String? {
        val chooser = JFileChooser().apply {
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            isAcceptAllFileFilterUsed = false
            dialogTitle = "Select Project Folder"
        }
        val result = chooser.showOpenDialog(null)
        return if (result == JFileChooser.APPROVE_OPTION) chooser.selectedFile.absolutePath else null
    }
}
