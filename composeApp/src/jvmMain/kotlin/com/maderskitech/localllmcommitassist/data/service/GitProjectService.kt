package com.maderskitech.localllmcommitassist.data.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object GitProjectService {
    suspend fun stageAndReadCachedDiff(projectPath: String): Result<String> = runCatching {
        runGit(projectPath, "add", "-A")
        val diff = runGit(projectPath, "diff", "--cached", "--no-color")
        if (diff.isBlank()) {
            throw IllegalStateException("No staged changes found after git add -A.")
        }
        diff
    }

    suspend fun commitAllChanges(projectPath: String, summary: String, description: String): Result<String> = runCatching {
        val trimmedSummary = summary.trim()
        if (trimmedSummary.isBlank()) {
            throw IllegalStateException("Commit summary is required.")
        }

        runGit(projectPath, "add", "-A")

        val command = mutableListOf("commit", "-m", trimmedSummary)
        val trimmedDescription = description.trim()
        if (trimmedDescription.isNotBlank()) {
            command += listOf("-m", trimmedDescription)
        }

        runGit(projectPath, *command.toTypedArray())
    }

    private suspend fun runGit(projectPath: String, vararg args: String): String {
        return withContext(Dispatchers.IO) {
            val command = listOf("git") + args
            val process = ProcessBuilder(command)
                .directory(File(projectPath))
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                val rendered = command.joinToString(" ")
                throw IllegalStateException("Command failed: $rendered\n$output")
            }
            output
        }
    }
}
