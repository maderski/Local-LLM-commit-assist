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
            You are a commit message generator. Analyze the provided git diff and produce a JSON object with two fields:
            - "summary": A short imperative commit message under 72 characters (e.g., "Add user authentication endpoint")
            - "description": A detailed description with bullet points explaining the key changes

            Respond ONLY with the JSON object, no markdown fences or extra text.
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

        json.decodeFromString<CommitMessage>(jsonContent)
    }
}
