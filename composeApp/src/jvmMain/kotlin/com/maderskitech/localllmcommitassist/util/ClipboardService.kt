package com.maderskitech.localllmcommitassist.util

import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

interface ClipboardService {
    fun copy(text: String)
}

object SystemClipboardService : ClipboardService {
    override fun copy(text: String) {
        val selection = StringSelection(text)
        Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, null)
    }
}
