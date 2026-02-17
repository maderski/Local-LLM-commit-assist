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

    fun getStagedStatSummary(repoPath: String): Result<String> = runCatching {
        val process = ProcessBuilder("git", "diff", "--cached", "--stat")
            .directory(File(repoPath))
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        if (exitCode != 0) {
            error("git diff --cached --stat failed (exit $exitCode): $output")
        }
        output
    }

    fun getNewFiles(repoPath: String): Result<List<String>> = runCatching {
        val process = ProcessBuilder("git", "diff", "--cached", "--name-status")
            .directory(File(repoPath))
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        if (exitCode != 0) {
            error("git diff --cached --name-status failed (exit $exitCode): $output")
        }

        output.lines()
            .filter { it.isNotBlank() }
            .map { line ->
                val parts = line.split("\t", limit = 2)
                val status = when (parts[0].first()) {
                    'A' -> "added"
                    'D' -> "deleted"
                    'M' -> "modified"
                    'R' -> "renamed"
                    'C' -> "copied"
                    else -> parts[0]
                }
                val file = parts.getOrElse(1) { "" }
                "$file ($status)"
            }
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

    fun push(repoPath: String): Result<String> = runCatching {
        val process = ProcessBuilder("git", "push")
            .directory(File(repoPath))
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        if (exitCode != 0) {
            error("git push failed (exit $exitCode): $output")
        }
        output
    }
}
