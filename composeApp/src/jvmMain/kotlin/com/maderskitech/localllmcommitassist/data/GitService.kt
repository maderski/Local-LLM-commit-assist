package com.maderskitech.localllmcommitassist.data

import java.io.File

class GitService {
    fun isGitRepository(repoPath: String): Boolean {
        val gitDir = File(repoPath, ".git")
        return gitDir.exists() && gitDir.isDirectory
    }

    fun getStagedDiff(repoPath: String): Result<String> = runCatching {
        val process = ProcessBuilder("git", "diff", "--cached")
            .directory(File(repoPath))
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        if (exitCode != 0) {
            error("git diff --cached failed (exit $exitCode): $output")
        }
        output
    }

    fun stageAll(repoPath: String): Result<Unit> = runCatching {
        val process = ProcessBuilder("git", "add", "-A")
            .directory(File(repoPath))
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        if (exitCode != 0) {
            error("git add -A failed (exit $exitCode): $output")
        }
    }

    fun commit(repoPath: String, summary: String, description: String): Result<String> = runCatching {
        val message = if (description.isBlank()) summary else "$summary\n\n$description"

        val process = ProcessBuilder("git", "commit", "-m", message)
            .directory(File(repoPath))
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        if (exitCode != 0) {
            error("git commit failed (exit $exitCode): $output")
        }
        output
    }
}
