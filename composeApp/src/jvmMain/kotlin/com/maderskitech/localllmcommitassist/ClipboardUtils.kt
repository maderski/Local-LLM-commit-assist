package com.maderskitech.localllmcommitassist

import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

fun copyToClipboard(text: String) {
    val selection = StringSelection(text)
    Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, null)
}
