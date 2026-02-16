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

        val systemPrompt = """
            You are a commit message generator. Analyze the provided git diff and return ONLY a valid JSON object with exactly two string fields. No arrays, no bullet characters, no markdown.

            Example response:
            {"summary": "Add user authentication endpoint", "description": "- Added login route handler\n- Created JWT token generation utility\n- Added password hashing middleware"}

            Both "summary" and "description" must be strings. Use \n for newlines in the description. Do not use arrays.
        """.trimIndent()

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

        // Strip markdown code fences if present
        val jsonContent = content
            .removePrefix("```json").removePrefix("```")
            .removeSuffix("```")
            .trim()

        parseCommitMessage(jsonContent)
    }

    private fun parseCommitMessage(content: String): CommitMessage {
        try {
            val parsed = json.parseToJsonElement(content).jsonObject
            val summary = parsed["summary"]?.jsonPrimitive?.content.orEmpty()
            val descriptionElement = parsed["description"]
            val description = when (descriptionElement) {
                is JsonArray -> descriptionElement.joinToString("\n") { "- ${(it as JsonPrimitive).content}" }
                is JsonPrimitive -> descriptionElement.content
                else -> ""
            }
            return CommitMessage(summary, description)
        } catch (_: Exception) {
            // Fallback: extract summary from first line, rest as description
            val lines = content.lines().map { it.trim() }.filter { it.isNotBlank() }
            val summary = lines.firstOrNull()
                ?.removePrefix("\"summary\":")
                ?.trim()
                ?.removeSurrounding("\"")
                ?.removeSuffix(",")
                ?: content.take(72)
            val description = lines.drop(1).joinToString("\n")
            return CommitMessage(summary, description)
        }
    }
}
