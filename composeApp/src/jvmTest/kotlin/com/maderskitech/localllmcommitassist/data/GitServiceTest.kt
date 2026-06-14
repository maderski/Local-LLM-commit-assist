package com.maderskitech.localllmcommitassist.data

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GitServiceTest {

    private val service = GitService()

    // region helpers

    private fun tempDir(): File = Files.createTempDirectory("git-service-test").toFile()

    private fun exec(dir: File, vararg cmd: String): Int {
        val proc = ProcessBuilder(*cmd)
            .directory(dir)
            .redirectErrorStream(true)
            .start()
        proc.inputStream.bufferedReader().readText() // drain stdout/stderr
        return proc.waitFor()
    }

    private fun initRepo(defaultBranch: String = "main"): File {
        val dir = tempDir()
        // git init -b requires git 2.28+; fall back to symbolic-ref rename for older installs
        val initExit = ProcessBuilder("git", "init", "-b", defaultBranch)
            .directory(dir)
            .redirectErrorStream(true)
            .start()
            .also { it.inputStream.bufferedReader().readText() }
            .waitFor()
        if (initExit != 0) {
            exec(dir, "git", "init")
            exec(dir, "git", "symbolic-ref", "HEAD", "refs/heads/$defaultBranch")
        }
        exec(dir, "git", "config", "user.email", "test@test.com")
        exec(dir, "git", "config", "user.name", "Test User")
        return dir
    }

    private fun addCommit(
        dir: File,
        filename: String = "file.txt",
        message: String = "initial commit",
    ): File {
        val file = File(dir, filename).also { it.writeText("content") }
        exec(dir, "git", "add", filename)
        exec(dir, "git", "commit", "-m", message)
        return file
    }

    private fun readLog(dir: File): String =
        ProcessBuilder("git", "log", "--format=%B", "-1")
            .directory(dir)
            .start()
            .inputStream.bufferedReader().readText()
            .trim()

    // endregion

    // region isGitRepository

    @Test
    fun isGitRepository_returnsTrueForGitRepo() {
        val dir = initRepo()
        assertTrue(service.isGitRepository(dir.absolutePath))
        dir.deleteRecursively()
    }

    @Test
    fun isGitRepository_returnsFalseForPlainDirectory() {
        val dir = tempDir()
        assertFalse(service.isGitRepository(dir.absolutePath))
        dir.deleteRecursively()
    }

    // endregion

    // region readPrTemplate

    @Test
    fun readPrTemplate_returnsNullWhenNoTemplateExists() {
        val dir = initRepo()
        assertNull(service.readPrTemplate(dir.absolutePath, "github").getOrThrow())
        dir.deleteRecursively()
    }

    @Test
    fun readPrTemplate_returnsContentFromGitHubTemplatePath() {
        val dir = initRepo()
        File(dir, ".github").mkdirs()
        File(dir, ".github/pull_request_template.md").writeText("## Summary")
        assertEquals("## Summary", service.readPrTemplate(dir.absolutePath, "github").getOrThrow())
        dir.deleteRecursively()
    }

    @Test
    fun readPrTemplate_returnsContentFromAzureDevOpsTemplatePath() {
        val dir = initRepo()
        File(dir, ".azuredevops").mkdirs()
        File(dir, ".azuredevops/pull_request_template.md").writeText("## Changes")
        assertEquals("## Changes", service.readPrTemplate(dir.absolutePath, "azure_devops").getOrThrow())
        dir.deleteRecursively()
    }

    @Test
    fun readPrTemplate_prefersFirstMatchOverLaterPaths() {
        // Use paths that differ by directory, not just case, to stay safe on case-insensitive
        // filesystems (macOS). Path 1 is ".github/pull_request_template.md"; path 6 is
        // "docs/pull_request_template.md". Both exist — service must return path 1.
        val dir = initRepo()
        File(dir, ".github").mkdirs()
        File(dir, "docs").mkdirs()
        File(dir, ".github/pull_request_template.md").writeText("first")
        File(dir, "docs/pull_request_template.md").writeText("last")
        assertEquals("first", service.readPrTemplate(dir.absolutePath, "github").getOrThrow())
        dir.deleteRecursively()
    }

    @Test
    fun readPrTemplate_returnsNullForUnknownPlatform() {
        val dir = initRepo()
        File(dir, "pull_request_template.md").writeText("some template")
        assertNull(service.readPrTemplate(dir.absolutePath, "bitbucket").getOrThrow())
        dir.deleteRecursively()
    }

    // endregion

    // region getChangedFiles

    @Test
    fun getChangedFiles_returnsEmptyListForCleanRepo() {
        val dir = initRepo()
        addCommit(dir)
        assertTrue(service.getChangedFiles(dir.absolutePath).getOrThrow().isEmpty())
        dir.deleteRecursively()
    }

    @Test
    fun getChangedFiles_detectsUntrackedFiles() {
        val dir = initRepo()
        addCommit(dir)
        File(dir, "new.txt").writeText("untracked")
        val files = service.getChangedFiles(dir.absolutePath).getOrThrow()
        val entry = files.firstOrNull { it.first.contains("new.txt") }
        assertNotNull(entry)
        assertEquals("untracked", entry.second)
        dir.deleteRecursively()
    }

    @Test
    fun getChangedFiles_detectsModifiedFiles() {
        val dir = initRepo()
        val file = addCommit(dir, "existing.txt")
        file.writeText("changed content")
        val files = service.getChangedFiles(dir.absolutePath).getOrThrow()
        val entry = files.firstOrNull { it.first.contains("existing.txt") }
        assertNotNull(entry)
        assertEquals("modified", entry.second)
        dir.deleteRecursively()
    }

    @Test
    fun getChangedFiles_detectsDeletedFiles() {
        val dir = initRepo()
        val file = addCommit(dir, "todelete.txt")
        file.delete()
        val files = service.getChangedFiles(dir.absolutePath).getOrThrow()
        val entry = files.firstOrNull { it.first.contains("todelete.txt") }
        assertNotNull(entry)
        assertEquals("deleted", entry.second)
        dir.deleteRecursively()
    }

    // endregion

    // region getNewFiles (staged diff --name-status)

    @Test
    fun getNewFiles_returnsEmptyListWhenNothingIsStaged() {
        val dir = initRepo()
        addCommit(dir)
        assertTrue(service.getNewFiles(dir.absolutePath).getOrThrow().isEmpty())
        dir.deleteRecursively()
    }

    @Test
    fun getNewFiles_reportsAddedStatusForNewStagedFile() {
        val dir = initRepo()
        addCommit(dir)
        File(dir, "staged.txt").writeText("new")
        exec(dir, "git", "add", "staged.txt")
        val entry = service.getNewFiles(dir.absolutePath).getOrThrow()
            .firstOrNull { it.contains("staged.txt") }
        assertNotNull(entry)
        assertTrue(entry.contains("added"), "expected 'added' in '$entry'")
        dir.deleteRecursively()
    }

    @Test
    fun getNewFiles_reportsModifiedStatusForStagedEdit() {
        val dir = initRepo()
        val file = addCommit(dir, "mod.txt")
        file.writeText("updated")
        exec(dir, "git", "add", "mod.txt")
        val entry = service.getNewFiles(dir.absolutePath).getOrThrow()
            .firstOrNull { it.contains("mod.txt") }
        assertNotNull(entry)
        assertTrue(entry.contains("modified"), "expected 'modified' in '$entry'")
        dir.deleteRecursively()
    }

    @Test
    fun getNewFiles_reportsDeletedStatusForStagedDeletion() {
        val dir = initRepo()
        val file = addCommit(dir, "del.txt")
        file.delete()
        exec(dir, "git", "add", "-A")
        val entry = service.getNewFiles(dir.absolutePath).getOrThrow()
            .firstOrNull { it.contains("del.txt") }
        assertNotNull(entry)
        assertTrue(entry.contains("deleted"), "expected 'deleted' in '$entry'")
        dir.deleteRecursively()
    }

    @Test
    fun getNewFiles_reportsRenamedStatusForStagedRename() {
        val dir = initRepo()
        addCommit(dir, "original.txt")
        exec(dir, "git", "mv", "original.txt", "renamed.txt")
        val files = service.getNewFiles(dir.absolutePath).getOrThrow()
        // git mv produces an R entry; the output line includes the new filename
        val entry = files.firstOrNull { it.contains("renamed.txt") }
        assertNotNull(entry)
        assertTrue(entry.contains("renamed"), "expected 'renamed' in '$entry'")
        dir.deleteRecursively()
    }

    // endregion

    // region commit

    @Test
    fun commit_usesSummaryAloneWhenDescriptionIsBlank() {
        val dir = initRepo()
        addCommit(dir)
        File(dir, "change.txt").writeText("change")
        exec(dir, "git", "add", "change.txt")
        service.commit(dir.absolutePath, "Fix the bug", "").getOrThrow()
        assertEquals("Fix the bug", readLog(dir))
        dir.deleteRecursively()
    }

    @Test
    fun commit_separatesSummaryAndDescriptionWithBlankLine() {
        val dir = initRepo()
        addCommit(dir)
        File(dir, "feat.txt").writeText("feature")
        exec(dir, "git", "add", "feat.txt")
        service.commit(dir.absolutePath, "Add feature", "- Added foo\n- Removed bar").getOrThrow()
        assertEquals("Add feature\n\n- Added foo\n- Removed bar", readLog(dir))
        dir.deleteRecursively()
    }

    @Test
    fun commit_failsWhenNothingIsStaged() {
        val dir = initRepo()
        addCommit(dir) // nothing left to stage
        assertTrue(service.commit(dir.absolutePath, "Empty commit", "").isFailure)
        dir.deleteRecursively()
    }

    // endregion

    // region getDefaultBranch

    @Test
    fun getDefaultBranch_returnsMainWhenSymbolicRefFailsAndMainExists() {
        // No remote → symbolic-ref refs/remotes/origin/HEAD fails; main branch exists
        val dir = initRepo(defaultBranch = "main")
        addCommit(dir)
        assertEquals("main", service.getDefaultBranch(dir.absolutePath).getOrThrow())
        dir.deleteRecursively()
    }

    @Test
    fun getDefaultBranch_returnsMasterWhenOnlyMasterExists() {
        val dir = initRepo(defaultBranch = "master")
        addCommit(dir)
        assertEquals("master", service.getDefaultBranch(dir.absolutePath).getOrThrow())
        dir.deleteRecursively()
    }

    @Test
    fun getDefaultBranch_failsWhenNeitherMainNorMasterExists() {
        val dir = initRepo(defaultBranch = "develop")
        addCommit(dir)
        assertTrue(service.getDefaultBranch(dir.absolutePath).isFailure)
        dir.deleteRecursively()
    }

    // endregion

    // region getCommitLog

    @Test
    fun getCommitLog_returnsOnlyCommitsAheadOfBase() {
        val dir = initRepo()
        addCommit(dir, "a.txt", "base commit")
        exec(dir, "git", "checkout", "-b", "feature")
        addCommit(dir, "b.txt", "feature commit")
        val log = service.getCommitLog(dir.absolutePath, "main").getOrThrow()
        assertTrue(log.contains("feature commit"))
        assertFalse(log.contains("base commit"))
        dir.deleteRecursively()
    }

    @Test
    fun getCommitLog_fallsBackToRecentLogsWhenBaseBranchDoesNotExist() {
        val dir = initRepo()
        addCommit(dir, "a.txt", "only commit")
        val log = service.getCommitLog(dir.absolutePath, "nonexistent-branch").getOrThrow()
        assertTrue(log.contains("only commit"))
        dir.deleteRecursively()
    }

    @Test
    fun getCommitLog_fallsBackWhenNoCommitsAheadOfBase() {
        // HEAD == main, so git log main..HEAD is blank → fallback returns recent commits
        val dir = initRepo()
        addCommit(dir, "a.txt", "base commit")
        val log = service.getCommitLog(dir.absolutePath, "main").getOrThrow()
        assertTrue(log.contains("base commit"))
        dir.deleteRecursively()
    }

    // endregion
}
