package com.maderskitech.localllmcommitassist.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LlmServiceHttpTest {

    private fun mockClient(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> io.ktor.client.request.HttpResponseData,
    ): HttpClient {
        return HttpClient(MockEngine { request -> handler(request) }) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }

    @Test
    fun generateCommitMessage_postsExpectedRequest_andParsesSuccess() = runTest {
        var capturedRequest: HttpRequestData? = null
        val client = mockClient { request ->
            capturedRequest = request
            respondJson("""{"choices":[{"message":{"role":"assistant","content":"Add parser coverage\n- Cover LLM parsing edge cases"}}]}""")
        }

        val service = LlmService(client)
        val result = service.generateCommitMessage(
            address = "http://localhost:8080/",
            modelName = "",
            diff = "diff --git a/file.txt b/file.txt\n@@ -1 +1 @@\n-old\n+new",
        )

        assertTrue(result.isSuccess)
        assertEquals("Add parser coverage", result.getOrThrow().summary)
        assertEquals("- Cover LLM parsing edge cases", result.getOrThrow().description)

        val request = assertNotNull(capturedRequest)
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("http://localhost:8080/chat/completions", request.url.toString())
        val body = (request.body as TextContent).text
        assertContains(body, "\"model\":\"local-model\"")
        assertContains(body, "Generate a commit message for this diff:")
    }

    @Test
    fun generateCommitMessage_usesFallbackDescription_whenInitialResponseHasOnlySummary() = runTest {
        val requestBodies = mutableListOf<String>()
        val client = mockClient { request ->
            requestBodies += (request.body as TextContent).text
            when (requestBodies.size) {
                1 -> respondJson("""{"choices":[{"message":{"role":"assistant","content":"Tighten prompt limits"}}]}""")
                2 -> respondJson("""{"choices":[{"message":{"role":"assistant","content":"- Compact oversized diffs\n- Improve limit diagnostics"}}]}""")
                else -> error("Unexpected extra request")
            }
        }

        val service = LlmService(client)
        val result = service.generateCommitMessage(
            address = "http://localhost:8080",
            modelName = "test-model",
            diff = "diff --git a/file.txt b/file.txt\n@@ -1 +1 @@\n-old\n+new",
        )

        assertTrue(result.isSuccess)
        assertEquals("Tighten prompt limits", result.getOrThrow().summary)
        assertEquals("- Compact oversized diffs\n- Improve limit diagnostics", result.getOrThrow().description)
        assertEquals(2, requestBodies.size)
        assertContains(requestBodies[1], "Commit summary: Tighten prompt limits")
        assertContains(requestBodies[1], "\"temperature\":0.2")
    }

    @Test
    fun generateCommitMessage_usesDefaultDescription_whenFallbackBodyIsBlank() = runTest {
        val client = mockClient { request ->
            val body = (request.body as TextContent).text
            when {
                body.contains("Generate a commit message for this diff:") ->
                    respondJson("""{"choices":[{"message":{"role":"assistant","content":"Update tests"}}]}""")
                body.contains("Commit summary: Update tests") ->
                    respondJson("""{"choices":[{"message":{"role":"assistant","content":"```text\nTitle: ignored\n```"}}]}""")
                else -> error("Unexpected request body")
            }
        }

        val diff = """
            diff --git a/src/One.kt b/src/One.kt
            @@ -1 +1 @@
            -old
            +new
        """.trimIndent()
        val service = LlmService(client)
        val result = service.generateCommitMessage("http://localhost", "model", diff)

        assertTrue(result.isSuccess)
        assertEquals("Update tests", result.getOrThrow().summary)
        assertContains(result.getOrThrow().description, "- Update tests.")
        assertContains(result.getOrThrow().description, "- Updated 1 file(s), including src/One.kt.")
    }

    @Test
    fun generateCommitMessage_includesCompactionNotice_forOversizedDiff() = runTest {
        var requestBody = ""
        val client = mockClient { request ->
            requestBody = (request.body as TextContent).text
            respondJson("""{"choices":[{"message":{"role":"assistant","content":"Compact diff\n- Use prompt compaction"}}]}""")
        }

        val diff = buildString {
            append("diff --git a/large.txt b/large.txt\n")
            append("@@ -1,1 +1,12000 @@\n")
            repeat(12_000) { index ->
                append("+line $index\n")
            }
        }
        val service = LlmService(client)
        val result = service.generateCommitMessage("http://localhost", "model", diff)

        assertTrue(result.isSuccess)
        assertContains(requestBody, "[diff truncated to fit model context:")
    }

    @Test
    fun generatePrDescription_includesTemplateAndCompactedCommitLog() = runTest {
        var requestBody = ""
        val client = mockClient { request ->
            requestBody = (request.body as TextContent).text
            respondJson("""{"choices":[{"message":{"role":"assistant","content":"Improve prompt safety\n\nSummarize prompt handling\n\n- Add compaction\n- Add tests"}}]}""")
        }

        val commitLog = buildString {
            repeat(4_000) { index ->
                append("abc$index Update component $index\n")
            }
        }
        val template = buildString {
            append("## Summary\n")
            repeat(3_000) { append("Template line\n") }
        }
        val service = LlmService(client)
        val result = service.generatePrDescription(
            address = "http://localhost",
            modelName = "model",
            commitLog = commitLog,
            currentBranch = "feature/prompt-budget",
            prTemplate = template,
        )

        assertTrue(result.isSuccess)
        assertEquals("Improve prompt safety", result.getOrThrow().title)
        assertContains(result.getOrThrow().body, "Summarize prompt handling")
        assertContains(requestBody, "Current branch: feature/prompt-budget")
        assertContains(requestBody, "[commit log truncated to fit model context:")
        assertContains(requestBody, "[PR template truncated to fit model context:")
        assertContains(requestBody, "IMPORTANT: The repository has a PR template.")
    }

    @Test
    fun generateCommitMessage_returnsDetailedApiError_whenBodyPresent() = runTest {
        val client = mockClient { _ ->
            respond(
                content = ByteReadChannel("""{"error":"request exceeds context limit"}"""),
                status = HttpStatusCode.BadRequest,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        val service = LlmService(client)
        val result = service.generateCommitMessage("http://localhost", "model", "diff --git a/a b/a")

        assertTrue(result.isFailure)
        val message = result.exceptionOrNull()?.message.orEmpty()
        assertContains(message, "LLM API error 400: Bad Request")
        assertContains(message, "request exceeds context limit")
    }

    @Test
    fun generatePrDescription_returnsDetailedApiError_withoutBodySuffix_whenBodyBlank() = runTest {
        val client = mockClient { _ ->
            respond(
                content = ByteReadChannel(""),
                status = HttpStatusCode.InternalServerError,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Plain.toString()),
            )
        }

        val service = LlmService(client)
        val result = service.generatePrDescription("http://localhost", "model", "abc Test", "feature/test")

        assertTrue(result.isFailure)
        val message = result.exceptionOrNull()?.message.orEmpty()
        assertEquals("LLM API error 500: Internal Server Error", message)
    }

    @Test
    fun generatePrDescription_failsOnEmptyChoices() = runTest {
        val client = mockClient { _ ->
            respondJson("""{"choices":[]}""")
        }

        val service = LlmService(client)
        val result = service.generatePrDescription("http://localhost", "model", "abc Test", "feature/test")

        assertTrue(result.isFailure)
        assertContains(result.exceptionOrNull()?.message.orEmpty(), "LLM returned an empty response:")
    }

    @Test
    fun generateCommitMessage_fallsBackFromMalformedJsonText() = runTest {
        val client = mockClient { _ ->
            respondJson("""{"choices":[{"message":{"role":"assistant","content":"{\"summary\":\"Bad json\""}}]}""")
        }

        val service = LlmService(client)
        val result = service.generateCommitMessage("http://localhost", "model", "diff --git a/a b/a")

        assertTrue(result.isSuccess)
        assertContains(result.getOrThrow().summary, "{\"summary\":\"Bad json\"")
        assertTrue(result.getOrThrow().description.isNotBlank())
    }

    @Test
    fun generateCommitMessage_parsesAlternateJsonKeys() = runTest {
        val client = mockClient { _ ->
            respondJson(
                """{"choices":[{"message":{"role":"assistant","content":"{\"title\":\"Refine truncation\",\"changes\":[\"keep diff headers\",\"trim oversized hunks\"]}"}}]}"""
            )
        }

        val service = LlmService(client)
        val result = service.generateCommitMessage("http://localhost", "model", "diff --git a/a b/a")

        assertTrue(result.isSuccess)
        assertEquals("Refine truncation", result.getOrThrow().summary)
        assertContains(result.getOrThrow().description, "- keep diff headers")
        assertContains(result.getOrThrow().description, "- trim oversized hunks")
    }

    @Test
    fun generateCommitMessage_parsesSingleLineSummaryAndDescription() = runTest {
        val client = mockClient { _ ->
            respondJson("""{"choices":[{"message":{"role":"assistant","content":"Refine tests - cover fallback chain"}}]}""")
        }

        val service = LlmService(client)
        val result = service.generateCommitMessage("http://localhost", "model", "diff --git a/a b/a")

        assertTrue(result.isSuccess)
        assertEquals("Refine tests", result.getOrThrow().summary)
        assertEquals("cover fallback chain", result.getOrThrow().description)
    }

    private fun MockRequestHandleScope.respondJson(jsonBody: String) = respond(
        content = ByteReadChannel(jsonBody),
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
    )
}
