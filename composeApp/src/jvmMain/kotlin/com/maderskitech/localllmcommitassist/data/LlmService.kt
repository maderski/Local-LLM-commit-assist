package com.maderskitech.localllmcommitassist.data

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class ChatMessage(val role: String, val content: String)

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double = 0.3,
)

@Serializable
data class ChatResponse(val choices: List<Choice> = emptyList()) {
    @Serializable
    data class Choice(val message: ChatMessage)
}

@Serializable
data class CommitMessage(val summary: String, val description: String)

@Serializable
data class PrDescription(val title: String, val body: String)

class LlmService {
    private val json = Json { ignoreUnknownKeys = true }

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(json)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 5 * 60 * 1000 // 5 minutes
            connectTimeoutMillis = 30 * 1000      // 30 seconds
            socketTimeoutMillis = 5 * 60 * 1000   // 5 minutes
        }
    }

    suspend fun testConnection(address: String, modelName: String): Result<String> = runCatching {
        val model = modelName.ifBlank { "local-model" }
        val response = client.post("${address.trimEnd('/')}/chat/completions") {
            contentType(ContentType.Application.Json)
            setBody(
                ChatRequest(
                    model = model,
                    messages = listOf(ChatMessage("user", "Say hello in one word.")),
                    temperature = 0.0,
                )
            )
        }
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            error("LLM API error ${response.status.value}: ${response.status.description}")
        }
        val chatResponse = json.decodeFromString<ChatResponse>(body)
        chatResponse.choices.firstOrNull()?.message?.content?.trim()
            ?: error("LLM returned an empty response: $body")
    }

    suspend fun generateCommitMessage(
        address: String,
        modelName: String,
        diff: String,
    ): Result<CommitMessage> = runCatching {
        val model = modelName.ifBlank { "local-model" }
        val promptDiff = PromptCompactor.compactDiff(diff)

        val systemPrompt = buildString {
            append("You are a commit message generator. Analyze the provided git diff and write a commit message.\n\n")
            append("Reply with EXACTLY two lines and nothing else:\n")
            append("Line 1: A short imperative commit summary under 72 characters\n")
            append("Line 2: A detailed description with bullet points (use - for bullets) explaining the key changes\n\n")
            append("Do NOT wrap your response in JSON, code fences, or quotes. Just plain text, two lines.")
        }

        val userPrompt = "Generate a commit message for this diff:\n\n$promptDiff"

        val response = client.post("${address.trimEnd('/')}/chat/completions") {
            contentType(ContentType.Application.Json)
            setBody(
                ChatRequest(
                    model = model,
                    messages = listOf(
                        ChatMessage("system", systemPrompt),
                        ChatMessage("user", userPrompt),
                    ),
                )
            )
        }

        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            error(buildApiError(response.status.value, response.status.description, body))
        }
        val chatResponse = json.decodeFromString<ChatResponse>(body)
        val content = chatResponse.choices.firstOrNull()?.message?.content?.trim()
            ?: error("LLM returned an empty response: $body")

        val parsed = parseResponse(content)
        val summary = parsed.summary.ifBlank { "Update project files" }
        if (parsed.description.isNotBlank()) {
            return@runCatching CommitMessage(summary, parsed.description)
        }

        val fallbackDescription = generateCommitDescriptionFallback(address, model, summary, promptDiff)
            .ifBlank { buildDefaultDescription(diff, summary) }
        CommitMessage(summary, fallbackDescription)
    }

    suspend fun generatePrDescription(
        address: String,
        modelName: String,
        commitLog: String,
        currentBranch: String,
        prTemplate: String? = null,
    ): Result<PrDescription> = runCatching {
        val model = modelName.ifBlank { "local-model" }
        val promptTemplate = prTemplate?.let { PromptCompactor.compactText(it, "PR template", 18_000) }
        val promptCommitLog = PromptCompactor.compactText(commitLog, "commit log", 32_000)

        val systemPrompt = buildString {
            append("You are a pull request description generator. Analyze the provided git commit log and write a PR description.\n\n")
            if (!promptTemplate.isNullOrBlank()) {
                append("IMPORTANT: The repository has a PR template. You MUST follow this template structure for the PR description body. ")
                append("Fill in each section of the template based on the commit log.\n\n")
                append("PR Template:\n$promptTemplate\n\n")
            }
            append("Reply in plain text using EXACTLY this format and nothing else:\n\n")
            append("Line 1: A concise PR title under 72 characters describing the overall change\n")
            append("(blank line)\n")
            if (!promptTemplate.isNullOrBlank()) {
                append("The PR description body following the provided template structure, with each section filled in based on the commits.\n\n")
            } else {
                append("A brief 1-2 sentence summary of what was done and why.\n")
                append("(blank line)\n")
                append("Then a bullet-point list of the key changes. Use - for each bullet. One bullet per line. Aim for 3-6 bullets.\n\n")
            }
            append("Do NOT wrap your response in JSON, code fences, or markdown headers. Just plain text.")
        }

        val userPrompt = buildString {
            append("Current branch: $currentBranch\n\n")
            append("Generate a PR title and description for these commits:\n\n$promptCommitLog")
        }

        val response = client.post("${address.trimEnd('/')}/chat/completions") {
            contentType(ContentType.Application.Json)
            setBody(
                ChatRequest(
                    model = model,
                    messages = listOf(
                        ChatMessage("system", systemPrompt),
                        ChatMessage("user", userPrompt),
                    ),
                )
            )
        }

        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            error(buildApiError(response.status.value, response.status.description, body))
        }
        val chatResponse = json.decodeFromString<ChatResponse>(body)
        val content = chatResponse.choices.firstOrNull()?.message?.content?.trim()
            ?: error("LLM returned an empty response: $body")

        val parsed = parseResponse(content)
        PrDescription(title = parsed.summary, body = parsed.description)
    }

    private fun parseResponse(content: String): CommitMessage {
        // Strip markdown code fences if present
        val cleaned = content
            .removePrefix("```json").removePrefix("```")
            .removeSuffix("```")
            .trim()

        // Try JSON parsing first
        if (cleaned.startsWith("{")) {
            try {
                val parsed = json.parseToJsonElement(cleaned).jsonObject
                val summary = listOf("summary", "title", "subject")
                    .asSequence()
                    .mapNotNull { key -> parsed[key]?.asPlainText()?.takeIf { it.isNotBlank() } }
                    .firstOrNull()
                    .orEmpty()

                val description = listOf("description", "body", "details", "changes", "bullets")
                    .asSequence()
                    .mapNotNull { key -> parsed[key]?.asPlainText()?.takeIf { it.isNotBlank() } }
                    .firstOrNull()
                    .orEmpty()

                return CommitMessage(summary, description)
            } catch (_: Exception) {
                // Fall through to plain text parsing
            }
        }

        val labeledSummary = Regex("(?im)^\\s*summary\\s*:\\s*(.+)$")
            .find(cleaned)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            .orEmpty()
        val labeledDescription = Regex("(?is)\\bdescription\\s*:\\s*(.+)$")
            .find(cleaned)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            .orEmpty()
        if (labeledSummary.isNotBlank() && labeledDescription.isNotBlank()) {
            return CommitMessage(labeledSummary, labeledDescription)
        }

        // Plain text: first non-blank line is summary, everything after it is description.
        val allLines = cleaned.lines()
        val summaryLineIndex = allLines.indexOfFirst { it.isNotBlank() }
        val summary = if (summaryLineIndex >= 0) {
            allLines[summaryLineIndex].trim().removeSurrounding("\"")
        } else {
            cleaned.take(72)
        }

        val description = if (summaryLineIndex >= 0) {
            allLines
                .drop(summaryLineIndex + 1)
                .dropWhile { it.isBlank() }
                .joinToString("\n")
                .trim()
        } else {
            ""
        }

        var normalizedDescription = if (description.isBlank() && allLines.size > summaryLineIndex + 1) {
            // Fallback for terse model output where formatting is unusual but content exists.
            allLines.drop(summaryLineIndex + 1).joinToString(" ").trim()
        } else {
            description
        }

        // Handle single-line outputs like: "Add X - explain Y".
        if (normalizedDescription.isBlank()) {
            val compact = summary.trim()
            val separators = listOf(" - ", " — ", ": ")
            for (separator in separators) {
                val parts = compact.split(separator, limit = 2)
                if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                    return CommitMessage(parts[0].trim(), parts[1].trim())
                }
            }
        }

        return CommitMessage(summary, normalizedDescription)
    }

    private suspend fun generateCommitDescriptionFallback(
        address: String,
        model: String,
        summary: String,
        diff: String,
    ): String {
        val systemPrompt = buildString {
            append("You generate only commit message descriptions.\n")
            append("Return plain text body only, no title, no JSON, no code fences.\n")
            append("Write 2-4 bullet points using '- ' explaining key changes and impact.")
        }
        val userPrompt = buildString {
            append("Commit summary: $summary\n\n")
            append("Diff:\n$diff")
        }

        val response = client.post("${address.trimEnd('/')}/chat/completions") {
            contentType(ContentType.Application.Json)
            setBody(
                ChatRequest(
                    model = model,
                    messages = listOf(
                        ChatMessage("system", systemPrompt),
                        ChatMessage("user", userPrompt),
                    ),
                    temperature = 0.2,
                )
            )
        }

        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            error(buildApiError(response.status.value, response.status.description, body))
        }
        val chatResponse = json.decodeFromString<ChatResponse>(body)
        val raw = chatResponse.choices.firstOrNull()?.message?.content.orEmpty()
        val cleaned = raw
            .removePrefix("```text").removePrefix("```markdown").removePrefix("```")
            .removeSuffix("```")
            .trim()
            .lines()
            .dropWhile { it.isBlank() }
            .filterNot { line ->
                val normalized = line.trim().lowercase()
                normalized.startsWith("summary:") || normalized.startsWith("title:")
            }
            .joinToString("\n")
            .trim()
        return cleaned
    }

    private fun buildDefaultDescription(diff: String, summary: String): String {
        val files = diff.lineSequence()
            .filter { it.startsWith("diff --git ") }
            .mapNotNull { line ->
                val parts = line.split(" ")
                parts.getOrNull(2)?.removePrefix("a/")
            }
            .distinct()
            .toList()

        val additions = diff.lineSequence()
            .count { it.startsWith("+") && !it.startsWith("+++") }
        val deletions = diff.lineSequence()
            .count { it.startsWith("-") && !it.startsWith("---") }

        val firstFile = files.firstOrNull() ?: "project files"
        return buildString {
            append("- ")
            append(summary)
            append(".\n")
            append("- Updated ${files.size.coerceAtLeast(1)} file(s), including $firstFile.\n")
            append("- Net diff: +$additions / -$deletions lines.")
        }
    }

    private fun JsonElement.asPlainText(): String = when (this) {
        is JsonPrimitive -> content.trim()
        is JsonArray -> this
            .mapNotNull { element ->
                val text = element.asPlainText().trim()
                text.takeIf { it.isNotBlank() }
            }
            .joinToString("\n") { line -> if (line.startsWith("- ")) line else "- $line" }
        is JsonObject -> this.entries
            .sortedBy { it.key }
            .mapNotNull { (_, value) ->
                val text = value.asPlainText().trim()
                text.takeIf { it.isNotBlank() }
            }
            .joinToString("\n")
    }

    private fun buildApiError(statusCode: Int, statusDescription: String, body: String): String {
        val compactBody = body
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(500)
        return if (compactBody.isBlank()) {
            "LLM API error $statusCode: $statusDescription"
        } else {
            "LLM API error $statusCode: $statusDescription - $compactBody"
        }
    }
}

internal object PromptCompactor {
    private const val DEFAULT_TEXT_LIMIT = 32_000
    private const val DEFAULT_DIFF_LIMIT = 90_000
    private const val DEFAULT_SECTION_LIMIT = 8_000
    private const val MIN_TRAILING_SECTION_CHARS = 600

    fun compactText(text: String, label: String, maxChars: Int = DEFAULT_TEXT_LIMIT): String {
        if (text.length <= maxChars) return text
        return truncateWithNotice(text, label, maxChars)
    }

    fun compactDiff(
        diff: String,
        maxChars: Int = DEFAULT_DIFF_LIMIT,
        maxCharsPerSection: Int = DEFAULT_SECTION_LIMIT,
    ): String {
        if (diff.length <= maxChars) return diff

        val sections = splitDiffSections(diff)
        if (sections.size <= 1) {
            return truncateWithNotice(diff, "diff", maxChars)
        }

        val truncatedSections = sections.map { section ->
            if (section.length <= maxCharsPerSection) {
                section
            } else {
                truncateSection(section, maxCharsPerSection)
            }
        }

        val builder = StringBuilder()
        for (section in truncatedSections) {
            val separatorLength = if (builder.isEmpty()) 0 else 1
            if (builder.length + separatorLength + section.length > maxChars) {
                break
            }
            if (builder.isNotEmpty()) builder.append('\n')
            builder.append(section.trimEnd())
        }

        val compacted = if (builder.isNotEmpty()) builder.toString() else truncateSection(diff, maxChars)
        val includedSections = countIncludedSections(compacted)
        return buildString {
            append("[diff truncated to fit model context: ")
            append(diff.length)
            append(" chars across ")
            append(sections.size)
            append(" file patch(es); sending ")
            append(compacted.length)
            append(" chars across ")
            append(includedSections)
            append(" patch(es)]\n\n")
            append(compacted)
        }
    }

    private fun splitDiffSections(diff: String): List<String> {
        val sections = mutableListOf<String>()
        val current = StringBuilder()
        diff.lineSequence().forEach { line ->
            if (line.trimStart().startsWith("diff --git ") && current.isNotEmpty()) {
                sections += current.toString().trimEnd()
                current.clear()
            }
            current.append(line).append('\n')
        }
        if (current.isNotEmpty()) {
            sections += current.toString().trimEnd()
        }
        return sections.filter { it.isNotBlank() }
    }

    private fun truncateWithNotice(text: String, label: String, maxChars: Int): String {
        if (text.length <= maxChars) return text
        val notice = "[${label.trim()} truncated to fit model context: ${text.length} chars -> ${maxChars} chars]\n\n"
        val usableChars = maxChars - notice.length
        if (usableChars <= 0) {
            return notice.take(maxChars)
        }
        return notice + truncateMiddle(text, usableChars)
    }

    private fun truncateSection(section: String, maxChars: Int): String {
        if (section.length <= maxChars) return section
        val header = section.lineSequence()
            .takeWhile { !it.trimStart().startsWith("@@") }
            .joinToString("\n")
            .trimEnd()
        val headerWithSpacing = if (header.isBlank()) "" else "$header\n"
        val remaining = (maxChars - headerWithSpacing.length).coerceAtLeast(256)
        val body = section.removePrefix(headerWithSpacing)
        return headerWithSpacing + truncateMiddle(body, remaining)
    }

    private fun truncateMiddle(text: String, maxChars: Int): String {
        if (text.length <= maxChars) return text
        val marker = "\n... [truncated] ...\n"
        val remaining = maxChars - marker.length
        if (remaining <= 0) {
            return text.take(maxChars)
        }
        val head = remaining * 3 / 4
        val tail = remaining - head
        val targetTail = MIN_TRAILING_SECTION_CHARS.coerceAtMost(remaining / 2)
        val safeTail = if (tail >= targetTail) tail else targetTail
        val safeHead = (remaining - safeTail).coerceAtLeast(0)
        return text.take(safeHead) + marker + text.takeLast(safeTail)
    }

    private fun countIncludedSections(text: String): Int =
        text.lineSequence().count { it.trimStart().startsWith("diff --git ") }.coerceAtLeast(1)
}
