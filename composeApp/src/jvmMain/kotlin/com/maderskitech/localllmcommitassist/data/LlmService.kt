package com.maderskitech.localllmcommitassist.data

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
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

        parseResponse(content)
    }

    suspend fun generatePrDescription(
        address: String,
        modelName: String,
        commitLog: String,
        currentBranch: String,
    ): Result<PrDescription> = runCatching {
        val model = modelName.ifBlank { "local-model" }

        val systemPrompt = buildString {
            append("You are a pull request description generator. Analyze the provided git commit log and write a PR description.\n\n")
            append("Reply in plain text using EXACTLY this format and nothing else:\n\n")
            append("Line 1: A concise PR title under 72 characters describing the overall change\n")
            append("(blank line)\n")
            append("A brief 1-2 sentence summary of what was done and why.\n")
            append("(blank line)\n")
            append("Then a bullet-point list of the key changes. Use - for each bullet. One bullet per line. Aim for 3-6 bullets.\n\n")
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
                val summary = parsed["summary"]?.jsonPrimitive?.content.orEmpty()
                val descriptionElement = parsed["description"]
                val description = when (descriptionElement) {
                    is JsonArray -> descriptionElement.joinToString("\n") { "- ${(it as JsonPrimitive).content}" }
                    is JsonPrimitive -> descriptionElement.content
                    else -> ""
                }
                return CommitMessage(summary, description)
            } catch (_: Exception) {
                // Fall through to plain text parsing
            }
        }

        // Plain text: first line is summary, rest is description
        val allLines = cleaned.lines().map { it.trim() }
        val nonBlankLines = allLines.filter { it.isNotBlank() }
        val summary = nonBlankLines.firstOrNull()
            ?.removeSurrounding("\"")
            ?: cleaned.take(72)
        // Preserve blank lines in the body to separate summary paragraph from bullets
        val description = allLines
            .dropWhile { it.isNotBlank() }  // skip title
            .dropWhile { it.isBlank() }      // skip blank line(s) after title
            .joinToString("\n")
            .trim()
        return CommitMessage(summary, description)
    }
}
