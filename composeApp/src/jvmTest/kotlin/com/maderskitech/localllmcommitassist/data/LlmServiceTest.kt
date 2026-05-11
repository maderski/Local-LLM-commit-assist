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
    fun compactText_returnsOriginal_whenExactlyAtBudget() {
        val text = "x".repeat(64)

        val result = PromptCompactor.compactText(text, label = "commit log", maxChars = text.length)

        assertEquals(text, result)
    }

    @Test
    fun compactText_returnsTrimmedNotice_whenBudgetIsTiny() {
        val text = "a".repeat(300)

        val result = PromptCompactor.compactText(text, label = "commit log", maxChars = 10)

        assertEquals("[commit lo", result)
        assertEquals(10, result.length)
    }

    @Test
    fun compactText_preservesHeadAndTail_whenTruncated() {
        val text = "HEAD-" + "x".repeat(400) + "-TAIL"

        val result = PromptCompactor.compactText(text, label = "commit log", maxChars = 120)

        assertContains(result, "HEAD-")
        assertContains(result, "-TAIL")
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
    fun compactDiff_returnsOriginal_whenExactlyAtBudget() {
        val diff = """
            diff --git a/file.txt b/file.txt
            @@ -1 +1 @@
            -old
            +new
        """.trimIndent()

        val result = PromptCompactor.compactDiff(
            diff,
            maxChars = diff.length,
            maxCharsPerSection = diff.length,
        )

        assertEquals(diff, result)
    }

    @Test
    fun compactDiff_truncatesOversizedSingleFileDiff_withDiffNoticeFallback() {
        val body = buildString {
            repeat(120) { index ->
                append("+single-file line $index\n")
            }
        }
        val diff = """
            diff --git a/src/Only.kt b/src/Only.kt
            --- a/src/Only.kt
            +++ b/src/Only.kt
            @@ -1,1 +1,120 @@
            $body
        """.trimIndent()

        val result = PromptCompactor.compactDiff(diff, maxChars = 220, maxCharsPerSection = 120)

        assertContains(result, "[diff truncated to fit model context:")
        assertContains(result, "diff --git a/src/Only.kt b/src/Only.kt")
        assertContains(result, "... [truncated] ...")
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

    @Test
    fun compactDiff_preservesNoHunkSections_throughPublicCompactionBehavior() {
        val noHunkMetadata = buildString {
            repeat(30) { index ->
                append("rename metadata line $index\n")
            }
        }
        val diff = """
            diff --git a/src/Legacy.kt b/src/Renamed.kt
            similarity index 85%
            rename from src/Legacy.kt
            rename to src/Renamed.kt
            $noHunkMetadata
            diff --git a/src/Changed.kt b/src/Changed.kt
            --- a/src/Changed.kt
            +++ b/src/Changed.kt
            @@ -1 +1 @@
            -old
            +new
        """.trimIndent()

        val result = PromptCompactor.compactDiff(diff, maxChars = 900, maxCharsPerSection = 320)

        assertContains(result, "[diff truncated to fit model context:")
        assertContains(result, "diff --git a/src/Legacy.kt b/src/Renamed.kt")
        assertContains(result, "diff --git a/src/Changed.kt b/src/Changed.kt")
        assertContains(result, "across 2 patch(es)]")
    }

    @Test
    fun compactDiff_fallsBackWhenNoSectionFitsBuilderBudget() {
        val oversizedHeader = buildString {
            repeat(40) { index ->
                append("rename metadata line $index that keeps the header very large\n")
            }
        }
        val diff = """
            diff --git a/src/Huge.kt b/src/HugeRenamed.kt
            similarity index 90%
            rename from src/Huge.kt
            rename to src/HugeRenamed.kt
            $oversizedHeader
            diff --git a/src/Small.kt b/src/Small.kt
            --- a/src/Small.kt
            +++ b/src/Small.kt
            @@ -1 +1 @@
            -old
            +new
        """.trimIndent()

        val result = PromptCompactor.compactDiff(diff, maxChars = 220, maxCharsPerSection = 120)

        assertContains(result, "[diff truncated to fit model context:")
        assertContains(result, "diff --git a/src/Huge.kt b/src/HugeRenamed.kt")
        assertContains(result, "diff --git a/src/Small.kt b/src/Small.kt")
        assertContains(result, "across 2 patch(es)]")
    }

    @Test
    fun compactDiff_omitsTrailingSectionsThatDoNotFit_andReportsIncludedCount() {
        val diff = listOf(
            patchSection("One", 8),
            patchSection("Two", 8),
            patchSection("Three", 8),
        ).joinToString("\n")

        val result = PromptCompactor.compactDiff(diff, maxChars = 800, maxCharsPerSection = 180)

        assertContains(result, "across 3 file patch(es);")
        assertContains(result, "across 2 patch(es)]")
        assertContains(result, "diff --git a/src/One.kt b/src/One.kt")
        assertContains(result, "diff --git a/src/Two.kt b/src/Two.kt")
        assertTrue(!result.contains("diff --git a/src/Three.kt b/src/Three.kt"))
    }

    private fun patchSection(name: String, lineCount: Int): String {
        val body = buildString {
            repeat(lineCount) { index ->
                append("+$name line $index with enough content to consume budget\n")
            }
        }
        return """
            diff --git a/src/$name.kt b/src/$name.kt
            --- a/src/$name.kt
            +++ b/src/$name.kt
            @@ -1,1 +1,$lineCount @@
            $body
        """.trimIndent()
    }
}
