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
data class ChatResponse(val choices: List<Choice>) {
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
        val chatResponse = json.decodeFromString<ChatResponse>(body)
        chatResponse.choices.first().message.content.trim()
    }

    suspend fun generateCommitMessage(
        address: String,
        modelName: String,
        diff: String,
    ): Result<CommitMessage> = runCatching {
        val model = modelName.ifBlank { "local-model" }

        val systemPrompt = buildString {
            append("You are a commit message generator. Analyze the provided git diff and write a commit message.\n\n")
            append("Reply with EXACTLY two lines and nothing else:\n")
            append("Line 1: A short imperative commit summary under 72 characters\n")
            append("Line 2: A detailed description with bullet points (use - for bullets) explaining the key changes\n\n")
            append("Do NOT wrap your response in JSON, code fences, or quotes. Just plain text, two lines.")
        }

        val userPrompt = "Generate a commit message for this diff:\n\n$diff"

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
        val chatResponse = json.decodeFromString<ChatResponse>(body)
        val content = chatResponse.choices.first().message.content.trim()

        val parsed = parseResponse(content)
        val summary = parsed.summary.ifBlank { "Update project files" }
        if (parsed.description.isNotBlank()) {
            return@runCatching CommitMessage(summary, parsed.description)
        }

        val fallbackDescription = generateCommitDescriptionFallback(address, model, summary, diff)
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

        val systemPrompt = buildString {
            append("You are a pull request description generator. Analyze the provided git commit log and write a PR description.\n\n")
            if (!prTemplate.isNullOrBlank()) {
                append("IMPORTANT: The repository has a PR template. You MUST follow this template structure for the PR description body. ")
                append("Fill in each section of the template based on the commit log.\n\n")
                append("PR Template:\n$prTemplate\n\n")
            }
            append("Reply in plain text using EXACTLY this format and nothing else:\n\n")
            append("Line 1: A concise PR title under 72 characters describing the overall change\n")
            append("(blank line)\n")
            if (!prTemplate.isNullOrBlank()) {
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
            append("Generate a PR title and description for these commits:\n\n$commitLog")
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
        val chatResponse = json.decodeFromString<ChatResponse>(body)
        val content = chatResponse.choices.first().message.content.trim()

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
}
