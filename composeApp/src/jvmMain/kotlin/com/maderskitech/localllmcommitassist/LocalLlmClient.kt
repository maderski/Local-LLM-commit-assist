package com.maderskitech.localllmcommitassist

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

object LocalLlmClient {
    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build()

    suspend fun testConnection(settings: AppSettings): Result<String> = runCatching {
        val modelsUrl = "${settings.baseUrl.trimEnd('/')}/models"
        val request = HttpRequest.newBuilder()
            .uri(URI.create(modelsUrl))
            .timeout(Duration.ofSeconds(10))
            .GET()
            .header("Content-Type", "application/json")
            .build()

        val response = withContext(Dispatchers.IO) {
            client.send(request, HttpResponse.BodyHandlers.ofString())
        }

        if (response.statusCode() in 200..299) {
            "Connected (${response.statusCode()})"
        } else {
            throw IllegalStateException("Connection failed with status ${response.statusCode()}: ${response.body()}")
        }
    }

    suspend fun generateCommitMessage(
        settings: AppSettings,
        project: Project,
        stagedDiff: String,
        additionalContext: String
    ): Result<GeneratedCommit> = runCatching {
        val completionsUrl = "${settings.baseUrl.trimEnd('/')}/chat/completions"

        val compatibleDiff = makeDiffCompatible(stagedDiff)
        val prompt = buildString {
            append("Project name: ${project.name}\\n")
            append("Project path: ${project.path}\\n\\n")
            append("Generate commit message output in this exact format:\\n")
            append("Summary: <single line summary <= 72 chars>\\n")
            append("Description:\\n")
            append("<bullet list describing key changes and rationale>\\n")
            append("Do NOT include raw diff lines, file hunks, or code blocks in the description.\\n\\n")
            append("Staged git diff:\\n")
            append(compatibleDiff)
            if (additionalContext.isNotBlank()) {
                append("\\n\\nAdditional user context:\\n")
                append(additionalContext.trim())
            }
        }

        val body = """
            {
              "model": "${escapeJson(settings.model)}",
              "temperature": 0.2,
              "max_tokens": 400,
              "messages": [
                {
                  "role": "system",
                  "content": "You write concise, high-quality git commit messages."
                },
                {
                  "role": "user",
                  "content": "${escapeJson(prompt)}"
                }
              ]
            }
        """.trimIndent()

        val request = HttpRequest.newBuilder()
            .uri(URI.create(completionsUrl))
            .timeout(Duration.ofSeconds(180))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()

        val response = withContext(Dispatchers.IO) {
            client.send(request, HttpResponse.BodyHandlers.ofString())
        }

        if (response.statusCode() !in 200..299) {
            throw IllegalStateException("Generation failed with status ${response.statusCode()}: ${response.body()}")
        }

        val content = extractFirstChoiceContent(response.body())
            ?: throw IllegalStateException("Could not parse LLM response content")

        parseCommit(content)
    }

    private fun parseCommit(content: String): GeneratedCommit {
        val lines = content.lines().map { it.trimEnd() }
        val summaryLine = lines.firstOrNull { it.startsWith("Summary:", ignoreCase = true) }
        val summary = summaryLine
            ?.substringAfter(":")
            ?.trim()
            ?.ifBlank { null }
            ?: lines.firstOrNull { it.isNotBlank() }
            ?: "Update project"

        val descriptionHeaderIndex = lines.indexOfFirst { it.startsWith("Description:", ignoreCase = true) }
        val rawDescription = if (descriptionHeaderIndex >= 0 && descriptionHeaderIndex < lines.lastIndex) {
            lines.drop(descriptionHeaderIndex + 1).joinToString("\n").trim().ifBlank { "- Update implementation details." }
        } else {
            lines.drop(1).joinToString("\n").trim().ifBlank { "- Update implementation details." }
        }
        val description = sanitizeDescription(rawDescription)

        return GeneratedCommit(summary = summary, description = description)
    }

    private fun sanitizeDescription(description: String): String {
        val filtered = description
            .lineSequence()
            .takeWhile { line ->
                !looksLikeDiffStart(line) && !isStagedDiffHeader(line)
            }
            .filterNot { line -> looksLikeDiffLine(line) }
            .joinToString("\n")
            .trim()

        return filtered.ifBlank { "- Update implementation details." }
    }

    private fun isStagedDiffHeader(line: String): Boolean {
        return line.trimStart().startsWith("Staged git diff:", ignoreCase = true)
    }

    private fun looksLikeDiffStart(line: String): Boolean {
        val trimmed = line.trimStart()
        return trimmed.startsWith("diff --git") ||
            trimmed.startsWith("@@ ") ||
            trimmed.startsWith("@@") ||
            trimmed.startsWith("--- ") ||
            trimmed.startsWith("+++ ")
    }

    private fun looksLikeDiffLine(line: String): Boolean {
        val trimmed = line.trimStart()
        return trimmed.startsWith("diff --git") ||
            trimmed.startsWith("index ") ||
            trimmed.startsWith("@@ ") ||
            trimmed.startsWith("@@") ||
            trimmed.startsWith("--- ") ||
            trimmed.startsWith("+++ ") ||
            Regex("^[+-][^\\-+].*").matches(trimmed)
    }

    private fun extractFirstChoiceContent(responseBody: String): String? {
        val regex = Regex("\\\"content\\\"\\s*:\\s*\\\"((?:[^\\\"\\\\]|\\\\.)*)\\\"")
        val match = regex.find(responseBody) ?: return null
        return unescapeJson(match.groupValues[1])
    }

    private fun unescapeJson(value: String): String {
        return buildString {
            var i = 0
            while (i < value.length) {
                val c = value[i]
                if (c == '\\' && i + 1 < value.length) {
                    val next = value[i + 1]
                    when (next) {
                        '\\' -> append('\\')
                        '"' -> append('"')
                        '/' -> append('/')
                        'b' -> append('\b')
                        'f' -> append('\u000C')
                        'n' -> append('\n')
                        'r' -> append('\r')
                        't' -> append('\t')
                        'u' -> {
                            if (i + 5 < value.length) {
                                val hex = value.substring(i + 2, i + 6)
                                append(hex.toInt(16).toChar())
                                i += 4
                            }
                        }
                        else -> append(next)
                    }
                    i += 2
                } else {
                    append(c)
                    i++
                }
            }
        }
    }

    private fun escapeJson(value: String): String {
        return buildString {
            value.forEach { c ->
                when (c) {
                    '"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    '\b' -> append("\\b")
                    '\u000C' -> append("\\f")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(c)
                }
            }
        }
    }

    private fun makeDiffCompatible(diff: String): String {
        val normalized = diff.replace("\u0000", "")
        val maxChars = 40_000
        if (normalized.length <= maxChars) return normalized

        val head = normalized.take(25_000)
        val tail = normalized.takeLast(15_000)
        return buildString {
            append(head)
            append("\n\n[Diff truncated for model input compatibility]\n\n")
            append(tail)
        }
    }
}
