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

    fun getCurrentBranch(repoPath: String): Result<String> = runCatching {
        val process = ProcessBuilder("git", "branch", "--show-current")
            .directory(File(repoPath))
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText().trim()
        val exitCode = process.waitFor()

        if (exitCode != 0) {
            error("git branch --show-current failed (exit $exitCode): $output")
        }
        output
    }

    fun getLocalBranches(repoPath: String): Result<List<String>> = runCatching {
        val process = ProcessBuilder("git", "branch")
            .directory(File(repoPath))
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        if (exitCode != 0) error("git branch failed (exit $exitCode): $output")
        output.lines()
            .map { it.trim().removePrefix("* ").trim() }
            .filter { it.isNotBlank() }
    }

    fun checkoutBranch(repoPath: String, branch: String): Result<String> = runCatching {
        val process = ProcessBuilder("git", "checkout", branch)
            .directory(File(repoPath))
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        if (exitCode != 0) {
            error("git checkout failed (exit $exitCode): $output")
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

    fun getCommitLog(repoPath: String, baseBranch: String): Result<String> = runCatching {
        val process = ProcessBuilder("git", "log", "--oneline", "$baseBranch..HEAD")
            .directory(File(repoPath))
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText().trim()
        val exitCode = process.waitFor()

        if (exitCode != 0 || output.isBlank()) {
            val fallback = ProcessBuilder("git", "log", "--oneline", "-n", "10")
                .directory(File(repoPath))
                .redirectErrorStream(true)
                .start()
            val fallbackOutput = fallback.inputStream.bufferedReader().readText().trim()
            if (fallback.waitFor() != 0) error("git log fallback failed: $fallbackOutput")
            fallbackOutput
        } else {
            output
        }
    }

    fun getRemoteUrl(repoPath: String): Result<String> = runCatching {
        val process = ProcessBuilder("git", "remote", "get-url", "origin")
            .directory(File(repoPath))
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText().trim()
        val exitCode = process.waitFor()

        if (exitCode != 0) {
            error("git remote get-url origin failed (exit $exitCode): $output")
        }
        output
    }

    fun readPrTemplate(repoPath: String, platform: String): Result<String?> = runCatching {
        val templatePaths = when (platform) {
            "github" -> listOf(
                ".github/pull_request_template.md",
                ".github/PULL_REQUEST_TEMPLATE.md",
                ".github/PULL_REQUEST_TEMPLATE/pull_request_template.md",
                "pull_request_template.md",
                "PULL_REQUEST_TEMPLATE.md",
                "docs/pull_request_template.md",
            )
            "azure_devops" -> listOf(
                ".azuredevops/pull_request_template.md",
                ".azuredevops/PULL_REQUEST_TEMPLATE.md",
                "pull_request_template.md",
                "PULL_REQUEST_TEMPLATE.md",
                "docs/pull_request_template.md",
            )
            else -> emptyList()
        }

        for (path in templatePaths) {
            val file = File(repoPath, path)
            if (file.exists() && file.isFile) {
                return@runCatching file.readText()
            }
        }
        null
    }

    fun createBranch(repoPath: String, branchName: String): Result<String> = runCatching {
        val process = ProcessBuilder("git", "checkout", "-b", branchName)
            .directory(File(repoPath))
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        if (exitCode != 0) {
            error("git checkout -b failed (exit $exitCode): $output")
        }
        output
    }

    fun deleteBranch(repoPath: String, branchName: String): Result<String> = runCatching {
        val process = ProcessBuilder("git", "branch", "-d", branchName)
            .directory(File(repoPath))
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        if (exitCode != 0) {
            error("git branch -d failed (exit $exitCode): $output")
        }
        output
    }

    fun fetchBranch(repoPath: String): Result<String> = runCatching {
        val process = ProcessBuilder("git", "fetch")
            .directory(File(repoPath))
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        if (exitCode != 0) {
            error("git fetch failed (exit $exitCode): $output")
        }
        output
    }

    fun hasUpstreamBranch(repoPath: String, branchName: String): Result<Boolean> = runCatching {
        val process = ProcessBuilder("git", "config", "--get", "branch.$branchName.remote")
            .directory(File(repoPath))
            .redirectErrorStream(true)
            .start()

        process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        exitCode == 0
    }

    fun publishBranch(repoPath: String, branchName: String): Result<String> = runCatching {
        val process = ProcessBuilder("git", "push", "--set-upstream", "origin", branchName)
            .directory(File(repoPath))
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        if (exitCode != 0) {
            error("git push --set-upstream failed (exit $exitCode): $output")
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
