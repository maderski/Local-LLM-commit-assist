package com.maderskitech.localllmcommitassist.data

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlin.math.ceil
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

internal enum class ContextWindowSource(val description: String) {
    PROVIDER_METADATA("detected from provider metadata"),
    USER_OVERRIDE("using configured override"),
    DEFAULT_FALLBACK("using conservative fallback"),
}

internal data class ModelContextWindow(
    val tokens: Int,
    val source: ContextWindowSource,
)

internal data class ModelPromptBudget(
    val contextWindowTokens: Int,
    val usableInputTokens: Int,
    val reservedOutputTokens: Int,
    val safetyBufferTokens: Int,
    val source: ContextWindowSource,
    val attempt: Int,
)

class LlmService private constructor(
    dependencies: Dependencies,
) {
    private val client = dependencies.client
    private val json = dependencies.json
    private val providerContextCache = mutableMapOf<String, ModelContextWindow>()

    constructor() : this(defaultDependencies())

    constructor(client: HttpClient) : this(Dependencies(client = client, json = defaultJson()))

    constructor(client: HttpClient, json: Json) : this(Dependencies(client = client, json = json))

    constructor(engine: HttpClientEngine, json: Json = defaultJson()) :
        this(Dependencies(client = createHttpClient(engine, json), json = json))

    suspend fun testConnection(
        address: String,
        modelName: String,
        modelContextWindowTokens: Int? = null,
    ): Result<String> = runCatching {
        val model = modelName.ifBlank { "local-model" }
        val contextWindow = resolveModelContextWindow(address, model, modelContextWindowTokens)
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
        val reply = chatResponse.choices.firstOrNull()?.message?.content?.trim()
            ?: error("LLM returned an empty response: $body")
        "$reply (${contextWindow.source.description}; context window ${contextWindow.tokens} tokens)"
    }

    suspend fun generateCommitMessage(
        address: String,
        modelName: String,
        diff: String,
        modelContextWindowTokens: Int? = null,
    ): Result<CommitMessage> = runCatching {
        val model = modelName.ifBlank { "local-model" }
        val systemPrompt = buildString {
            append("You are a commit message generator. Analyze the provided git diff and write a commit message.\n\n")
            append("Do not use reasoning, chain-of-thought, or deliberation in your response. ")
            append("Respond immediately with the final answer only.\n\n")
            append("Reply with EXACTLY two lines and nothing else:\n")
            append("Line 1: A short imperative commit summary under 72 characters\n")
            append("Line 2: A detailed description with bullet points (use - for bullets) explaining the key changes\n\n")
            append("Do NOT wrap your response in JSON, code fences, or quotes. Just plain text, two lines.")
        }
        val contextWindow = resolveModelContextWindow(address, model, modelContextWindowTokens)
        val chatResponse = sendChatWithRetries(
            address = address,
            model = model,
            contextWindow = contextWindow,
            outputReserveTokens = COMMIT_OUTPUT_RESERVE_TOKENS,
        ) { budget ->
            val promptDiff = PromptCompactor.compactDiffToTokenBudget(diff, budget.usableInputTokens)
            listOf(
                ChatMessage("system", systemPrompt),
                ChatMessage("user", "Generate a commit message for this diff:\n\n$promptDiff"),
            )
        }

        val content = chatResponse.choices.firstOrNull()?.message?.content?.trim()
            ?: error("LLM returned an empty response")

        val parsed = parseResponse(content)
        val summary = parsed.summary.ifBlank { "Update project files" }
        if (parsed.description.isNotBlank()) {
            return@runCatching CommitMessage(summary, parsed.description)
        }

        val fallbackDescription = generateCommitDescriptionFallback(
            address = address,
            model = model,
            summary = summary,
            diff = diff,
            contextWindow = contextWindow,
        )
            .ifBlank { buildDefaultDescription(diff, summary) }
        CommitMessage(summary, fallbackDescription)
    }

    suspend fun generatePrDescription(
        address: String,
        modelName: String,
        commitLog: String,
        currentBranch: String,
        prTemplate: String? = null,
        modelContextWindowTokens: Int? = null,
    ): Result<PrDescription> = runCatching {
        val model = modelName.ifBlank { "local-model" }
        val contextWindow = resolveModelContextWindow(address, model, modelContextWindowTokens)
        val chatResponse = sendChatWithRetries(
            address = address,
            model = model,
            contextWindow = contextWindow,
            outputReserveTokens = PR_OUTPUT_RESERVE_TOKENS,
        ) { budget ->
            val budgetAllocation = PromptCompactor.allocateTextBudgets(
                totalTokens = budget.usableInputTokens,
                labeledTexts = listOfNotNull(
                    prTemplate?.let { "PR template" to it },
                    "commit log" to commitLog,
                ),
                primaryLabel = "commit log",
            )
            val promptTemplate = prTemplate?.let {
                PromptCompactor.compactTextToTokenBudget(
                    text = it,
                    label = "PR template",
                    maxTokens = budgetAllocation["PR template"] ?: budget.usableInputTokens / 3,
                )
            }
            val promptCommitLog = PromptCompactor.compactTextToTokenBudget(
                text = commitLog,
                label = "commit log",
                maxTokens = budgetAllocation["commit log"] ?: budget.usableInputTokens,
            )

            val systemPrompt = buildString {
                append("You are a pull request description generator. Analyze the provided git commit log and write a PR description.\n\n")
                append("Do not use reasoning, chain-of-thought, or deliberation in your response. ")
                append("Respond immediately with the final answer only.\n\n")
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
            listOf(
                ChatMessage("system", systemPrompt),
                ChatMessage("user", userPrompt),
            )
        }

        val content = chatResponse.choices.firstOrNull()?.message?.content?.trim()
            ?: error("LLM returned an empty response")

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
        contextWindow: ModelContextWindow,
    ): String {
        val systemPrompt = buildString {
            append("You generate only commit message descriptions.\n")
            append("Do not use reasoning, chain-of-thought, or deliberation in your response.\n")
            append("Return plain text body only, no title, no JSON, no code fences.\n")
            append("Write 2-4 bullet points using '- ' explaining key changes and impact.")
        }
        val chatResponse = sendChatWithRetries(
            address = address,
            model = model,
            contextWindow = contextWindow,
            outputReserveTokens = FALLBACK_OUTPUT_RESERVE_TOKENS,
            temperature = 0.2,
        ) { budget ->
            val promptDiff = PromptCompactor.compactDiffToTokenBudget(diff, budget.usableInputTokens)
            val userPrompt = buildString {
                append("Commit summary: $summary\n\n")
                append("Diff:\n$promptDiff")
            }
            listOf(
                ChatMessage("system", systemPrompt),
                ChatMessage("user", userPrompt),
            )
        }

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

    private suspend fun sendChatWithRetries(
        address: String,
        model: String,
        contextWindow: ModelContextWindow,
        outputReserveTokens: Int,
        temperature: Double = 0.3,
        buildMessages: (ModelPromptBudget) -> List<ChatMessage>,
    ): ChatResponse {
        var lastApiError = ""
        for ((index, ratio) in INPUT_BUDGET_ATTEMPT_RATIOS.withIndex()) {
            val budget = createPromptBudget(contextWindow, outputReserveTokens, ratio, index + 1)
            val response = client.post("${address.trimEnd('/')}/chat/completions") {
                contentType(ContentType.Application.Json)
                setBody(
                    ChatRequest(
                        model = model,
                        messages = buildMessages(budget),
                        temperature = temperature,
                    )
                )
            }

            val body = response.bodyAsText()
            if (response.status.isSuccess()) {
                return json.decodeFromString(body)
            }

            lastApiError = buildApiError(response.status.value, response.status.description, body)
            if (!isContextOverflowError(response.status, body)) {
                error(lastApiError)
            }
        }

        error(
            "LLM request exceeded the available context window (${contextWindow.tokens} tokens, ${contextWindow.source.description}) " +
                "after ${INPUT_BUDGET_ATTEMPT_RATIOS.size} attempt(s). Last error: $lastApiError"
        )
    }

    private suspend fun resolveModelContextWindow(
        address: String,
        model: String,
        modelContextWindowTokens: Int?,
    ): ModelContextWindow {
        modelContextWindowTokens
            ?.takeIf { it > 0 }
            ?.let { return ModelContextWindow(it, ContextWindowSource.USER_OVERRIDE) }

        val cacheKey = "${address.trimEnd('/')}\n$model"
        providerContextCache[cacheKey]?.let { return it }

        val result = discoverProviderContextWindow(address, model)
            ?: ModelContextWindow(DEFAULT_CONTEXT_WINDOW_TOKENS, ContextWindowSource.DEFAULT_FALLBACK)
        providerContextCache[cacheKey] = result
        return result
    }

    private suspend fun discoverProviderContextWindow(address: String, model: String): ModelContextWindow? {
        val candidates = buildList {
            add("${address.trimEnd('/')}/models")
            add("${address.trimEnd('/')}/models/${model.encodeURLPath()}")
        }

        for (endpoint in candidates) {
            runCatching {
                val response = client.get(endpoint)
                if (!response.status.isSuccess()) return@runCatching null
                val body = response.bodyAsText()
                val root = json.parseToJsonElement(body)
                extractContextWindowTokens(root, model)
                    ?.takeIf { it >= MIN_PROVIDER_CONTEXT_WINDOW_TOKENS }
                    ?.let { ModelContextWindow(it, ContextWindowSource.PROVIDER_METADATA) }
            }.getOrNull()?.let { return it }
        }
        return null
    }

    private fun extractContextWindowTokens(root: JsonElement, model: String): Int? {
        val candidates = mutableListOf<JsonElement>()
        if (root is JsonObject) {
            val data = root["data"] as? JsonArray
            data?.firstOrNull { element ->
                (element as? JsonObject)?.get("id")?.jsonPrimitive?.contentOrNull() == model
            }?.let { candidates += it }
        }
        candidates += root

        return candidates.asSequence()
            .mapNotNull { findContextWindowTokens(it) }
            .firstOrNull()
    }

    private fun findContextWindowTokens(element: JsonElement): Int? = when (element) {
        is JsonPrimitive -> element.content.toIntOrNull()?.takeIf { it >= MIN_PROVIDER_CONTEXT_WINDOW_TOKENS }
        is JsonArray -> element.asSequence().mapNotNull { findContextWindowTokens(it) }.firstOrNull()
        is JsonObject -> {
            CONTEXT_WINDOW_KEYS.asSequence()
                .mapNotNull { key -> element[key]?.let { findContextWindowTokens(it) } }
                .firstOrNull()
        }
    }

    private fun JsonPrimitive.contentOrNull(): String? = runCatching { content }.getOrNull()

    private fun createPromptBudget(
        contextWindow: ModelContextWindow,
        outputReserveTokens: Int,
        attemptRatio: Double,
        attempt: Int,
    ): ModelPromptBudget {
        val safetyBufferTokens = maxOf(MIN_SAFETY_BUFFER_TOKENS, contextWindow.tokens / 10)
        val baseInputBudget = (
            contextWindow.tokens - outputReserveTokens - PROMPT_OVERHEAD_TOKENS - safetyBufferTokens
            ).coerceAtLeast(MIN_INPUT_BUDGET_TOKENS)
        val usableInputTokens = (baseInputBudget * attemptRatio).toInt().coerceAtLeast(MIN_INPUT_BUDGET_TOKENS)
        return ModelPromptBudget(
            contextWindowTokens = contextWindow.tokens,
            usableInputTokens = usableInputTokens,
            reservedOutputTokens = outputReserveTokens,
            safetyBufferTokens = safetyBufferTokens,
            source = contextWindow.source,
            attempt = attempt,
        )
    }

    private fun isContextOverflowError(status: HttpStatusCode, body: String): Boolean {
        if (status == HttpStatusCode.PayloadTooLarge) return true
        val normalized = body.lowercase()
        return (status == HttpStatusCode.BadRequest || status == HttpStatusCode.UnprocessableEntity) &&
            CONTEXT_OVERFLOW_MARKERS.any { it in normalized }
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

    private companion object {
        private data class Dependencies(
            val client: HttpClient,
            val json: Json,
        )

        private const val DEFAULT_CONTEXT_WINDOW_TOKENS = 8_192
        private const val MIN_PROVIDER_CONTEXT_WINDOW_TOKENS = 512
        private const val MIN_INPUT_BUDGET_TOKENS = 1_024
        private const val MIN_SAFETY_BUFFER_TOKENS = 768
        private const val PROMPT_OVERHEAD_TOKENS = 512
        private const val COMMIT_OUTPUT_RESERVE_TOKENS = 700
        private const val PR_OUTPUT_RESERVE_TOKENS = 1_200
        private const val FALLBACK_OUTPUT_RESERVE_TOKENS = 700
        private val INPUT_BUDGET_ATTEMPT_RATIOS = listOf(1.0, 0.72, 0.5)
        private val CONTEXT_WINDOW_KEYS = listOf(
            "context_length",
            "context_window",
            "max_context_length",
            "max_input_tokens",
            "num_ctx",
            "n_ctx",
        )
        private val CONTEXT_OVERFLOW_MARKERS = listOf(
            "context limit",
            "context length",
            "maximum context length",
            "max context length",
            "too many tokens",
            "prompt is too long",
            "request too large",
            "exceeds context",
            "context window",
        )

        private fun defaultDependencies(): Dependencies {
            val json = defaultJson()
            return Dependencies(
                client = createDefaultHttpClient(json),
                json = json,
            )
        }

        private fun defaultJson(): Json = Json { ignoreUnknownKeys = true }

        private fun createDefaultHttpClient(json: Json): HttpClient = HttpClient(CIO) {
            applyDefaultConfiguration(json)
        }

        private fun createHttpClient(engine: HttpClientEngine, json: Json): HttpClient = HttpClient(engine) {
            applyDefaultConfiguration(json)
        }

        private fun HttpClientConfig<*>.applyDefaultConfiguration(json: Json) {
            install(ContentNegotiation) {
                json(json)
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 5 * 60 * 1000 // 5 minutes
                connectTimeoutMillis = 30 * 1000      // 30 seconds
                socketTimeoutMillis = 5 * 60 * 1000   // 5 minutes
            }
        }
    }
}

internal object PromptCompactor {
    private const val DEFAULT_TEXT_LIMIT = 32_000
    private const val DEFAULT_DIFF_LIMIT = 90_000
    private const val DEFAULT_SECTION_LIMIT = 8_000
    private const val MIN_TRAILING_SECTION_CHARS = 600
    private const val CHARS_PER_TOKEN_ESTIMATE = 2.5

    fun compactText(text: String, label: String, maxChars: Int = DEFAULT_TEXT_LIMIT): String {
        if (text.length <= maxChars) return text
        return truncateWithNotice(text, label, maxChars)
    }

    fun compactTextToTokenBudget(text: String, label: String, maxTokens: Int): String =
        compactText(text, label, tokenBudgetToChars(maxTokens))

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

    fun compactDiffToTokenBudget(diff: String, maxTokens: Int): String {
        val maxChars = tokenBudgetToChars(maxTokens)
        val maxCharsPerSection = (maxChars / 3).coerceAtLeast(512)
        return compactDiff(diff, maxChars = maxChars, maxCharsPerSection = maxCharsPerSection)
    }

    fun allocateTextBudgets(
        totalTokens: Int,
        labeledTexts: List<Pair<String, String>>,
        primaryLabel: String,
    ): Map<String, Int> {
        if (labeledTexts.isEmpty()) return emptyMap()
        if (labeledTexts.size == 1) return mapOf(labeledTexts.single().first to totalTokens)

        val primary = labeledTexts.firstOrNull { it.first == primaryLabel } ?: labeledTexts.last()
        val secondary = labeledTexts.first { it.first != primary.first }
        val minimumPerInput = (totalTokens / 4).coerceAtLeast(512)
        val primaryRawTokens = estimateTokens(primary.second)
        val secondaryRawTokens = estimateTokens(secondary.second)

        var primaryBudget = minOf(primaryRawTokens, (totalTokens * 0.7).toInt())
        var secondaryBudget = minOf(secondaryRawTokens, totalTokens - primaryBudget)

        if (primaryBudget < minimumPerInput) primaryBudget = minimumPerInput.coerceAtMost(totalTokens)
        val secondaryFloor = minimumPerInput.coerceAtMost((totalTokens - primaryBudget).coerceAtLeast(0))
        if (secondaryBudget < secondaryFloor) secondaryBudget = secondaryFloor

        val consumed = primaryBudget + secondaryBudget
        if (consumed < totalTokens) {
            val leftover = totalTokens - consumed
            if (primaryRawTokens > primaryBudget) {
                primaryBudget += leftover
            } else {
                secondaryBudget += leftover
            }
        } else if (consumed > totalTokens) {
            val overflow = consumed - totalTokens
            if (secondaryBudget - overflow >= secondaryFloor) {
                secondaryBudget -= overflow
            } else {
                primaryBudget -= overflow - (secondaryBudget - secondaryFloor)
                secondaryBudget = secondaryFloor
            }
        }

        return mapOf(primary.first to primaryBudget, secondary.first to secondaryBudget)
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
        if (headerWithSpacing.length >= maxChars) {
            return truncateMiddle(section, maxChars)
        }
        val remaining = maxChars - headerWithSpacing.length
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

    private fun tokenBudgetToChars(maxTokens: Int): Int =
        ceil(maxTokens * CHARS_PER_TOKEN_ESTIMATE).toInt().coerceAtLeast(256)

    private fun estimateTokens(text: String): Int =
        ceil(text.length / CHARS_PER_TOKEN_ESTIMATE).toInt().coerceAtLeast(1)
}
