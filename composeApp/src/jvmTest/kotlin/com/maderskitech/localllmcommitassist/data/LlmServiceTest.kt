package com.maderskitech.localllmcommitassist.data

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LlmServiceTest {

    @Test
    fun compactText_returnsOriginal_whenWithinBudget() {
        val text = "short commit log"

        val result = PromptCompactor.compactText(text, label = "commit log", maxChars = 100)

        assertEquals(text, result)
    }

    @Test
    fun compactText_addsNoticeAndTruncates_whenOverBudget() {
        val text = "a".repeat(300)

        val result = PromptCompactor.compactText(text, label = "commit log", maxChars = 120)

        assertContains(result, "[commit log truncated to fit model context:")
        assertTrue(result.length <= 140)
        assertContains(result, "... [truncated] ...")
    }

    @Test
    fun compactDiff_returnsOriginal_whenWithinBudget() {
        val diff = """
            diff --git a/file.txt b/file.txt
            @@ -1 +1 @@
            -old
            +new
        """.trimIndent()

        val result = PromptCompactor.compactDiff(diff, maxChars = 500, maxCharsPerSection = 200)

        assertEquals(diff, result)
    }

    @Test
    fun compactDiff_truncatesLargeMultiFileDiff_andKeepsPatchHeaders() {
        val fileOneBody = buildString {
            repeat(200) { index ->
                append("+added line $index in file one\n")
            }
        }
        val fileTwoBody = buildString {
            repeat(200) { index ->
                append("-removed line $index in file two\n")
            }
        }
        val diff = """
            diff --git a/src/One.kt b/src/One.kt
            --- a/src/One.kt
            +++ b/src/One.kt
            @@ -1,1 +1,200 @@
            $fileOneBody
            diff --git a/src/Two.kt b/src/Two.kt
            --- a/src/Two.kt
            +++ b/src/Two.kt
            @@ -1,200 +1,1 @@
            $fileTwoBody
        """.trimIndent()

        val result = PromptCompactor.compactDiff(diff, maxChars = 1_100, maxCharsPerSection = 500)

        assertContains(result, "[diff truncated to fit model context:")
        assertContains(result, "diff --git a/src/One.kt b/src/One.kt")
        assertContains(result, "diff --git a/src/Two.kt b/src/Two.kt")
        assertContains(result, "... [truncated] ...")
    }
}
