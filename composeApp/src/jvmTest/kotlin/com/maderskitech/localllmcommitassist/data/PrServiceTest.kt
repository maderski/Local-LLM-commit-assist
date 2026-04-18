package com.maderskitech.localllmcommitassist.data

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.test.*

class PrServiceTest {

    // --- parseGitHubRemote ---

    @Test
    fun parseGitHubRemote_ssh() {
        val service = PrService()
        val result = service.parseGitHubRemote("git@github.com:myorg/myrepo.git")
        assertEquals(Pair("myorg", "myrepo"), result)
    }

    @Test
    fun parseGitHubRemote_https() {
        val service = PrService()
        val result = service.parseGitHubRemote("https://github.com/myorg/myrepo")
        assertEquals(Pair("myorg", "myrepo"), result)
    }

    @Test
    fun parseGitHubRemote_invalid_returnsNull() {
        val service = PrService()
        assertNull(service.parseGitHubRemote("https://gitlab.com/myorg/myrepo"))
    }

    // --- parseAzureDevOpsRemote ---

    @Test
    fun parseAzureDevOpsRemote_devAzureHttps() {
        val service = PrService()
        val result = service.parseAzureDevOpsRemote("https://dev.azure.com/myorg/myproject/_git/myrepo")
        assertEquals(Triple("https://dev.azure.com/myorg", "myproject", "myrepo"), result)
    }

    @Test
    fun parseAzureDevOpsRemote_devAzureHttpsWithUser() {
        val service = PrService()
        val result = service.parseAzureDevOpsRemote("https://user@dev.azure.com/myorg/myproject/_git/myrepo.git")
        assertEquals(Triple("https://dev.azure.com/myorg", "myproject", "myrepo"), result)
    }

    @Test
    fun parseAzureDevOpsRemote_ssh() {
        val service = PrService()
        val result = service.parseAzureDevOpsRemote("git@ssh.dev.azure.com:v3/myorg/myproject/myrepo")
        assertEquals(Triple("https://dev.azure.com/myorg", "myproject", "myrepo"), result)
    }

    @Test
    fun parseAzureDevOpsRemote_legacyVisualStudio() {
        val service = PrService()
        val result = service.parseAzureDevOpsRemote("https://myorg.visualstudio.com/myproject/_git/myrepo")
        assertEquals(Triple("https://dev.azure.com/myorg", "myproject", "myrepo"), result)
    }

    @Test
    fun parseAzureDevOpsRemote_legacyDefaultCollection() {
        val service = PrService()
        val result = service.parseAzureDevOpsRemote("https://myorg.visualstudio.com/DefaultCollection/myproject/_git/myrepo")
        assertEquals(Triple("https://dev.azure.com/myorg", "myproject", "myrepo"), result)
    }

    @Test
    fun parseAzureDevOpsRemote_invalid_returnsNull() {
        val service = PrService()
        assertNull(service.parseAzureDevOpsRemote("https://github.com/myorg/myrepo"))
    }

    // --- buildMarkdownReference ---

    @Test
    fun buildMarkdownReference_image() {
        val service = PrService()
        val file = File.createTempFile("test", ".png")
        val attachment = PrAttachment(file = file, name = "screenshot.png")
        val result = service.buildMarkdownReference(attachment, "https://example.com/img.png")
        assertEquals("![screenshot.png](https://example.com/img.png)", result)
        file.delete()
    }

    @Test
    fun buildMarkdownReference_video() {
        val service = PrService()
        val file = File.createTempFile("test", ".mp4")
        val attachment = PrAttachment(file = file, name = "demo.mp4")
        val result = service.buildMarkdownReference(attachment, "https://example.com/demo.mp4")
        assertEquals("[![demo.mp4](https://example.com/demo.mp4)](https://example.com/demo.mp4)", result)
        file.delete()
    }

    // --- extractWorkItemIds ---

    @Test
    fun extractWorkItemIds_branchWithNumber() {
        val service = PrService()
        assertEquals(listOf("12345"), service.extractWorkItemIds("bugfix/12345-fix-login"))
    }

    @Test
    fun extractWorkItemIds_branchWithNoNumber() {
        val service = PrService()
        assertEquals(emptyList(), service.extractWorkItemIds("feature/no-ticket-here"))
    }

    // --- inferTags ---

    @Test
    fun inferTags_android() {
        val service = PrService()
        assertContains(service.inferTags("my-android-app"), "Android")
    }

    @Test
    fun inferTags_kmp() {
        val service = PrService()
        assertContains(service.inferTags("kmp-shared-lib"), "Kotlin Multiplatform")
    }

    @Test
    fun inferTags_noMatch_returnsEmpty() {
        val service = PrService()
        assertEquals(emptyList(), service.inferTags("my-random-project"))
    }

    // --- uploadAzureDevOpsPrAttachment (HTTP mock) ---

    private fun mockClient(handler: suspend MockRequestHandleScope.(io.ktor.client.request.HttpRequestData) -> io.ktor.client.request.HttpResponseData): HttpClient {
        return HttpClient(MockEngine { request -> handler(request) }) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
    }

    @Test
    fun uploadAzureDevOpsPrAttachment_success() = runTest {
        val client = mockClient { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertTrue(request.url.toString().contains("pullRequests/42/attachments"))
            assertTrue(request.headers[HttpHeaders.Authorization]?.startsWith("Basic ") == true)
            respond(
                content = ByteReadChannel("""{"url":"https://dev.azure.com/org/proj/_apis/git/repositories/repo/pullRequests/42/attachments/img.png"}"""),
                status = HttpStatusCode.Created,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val file = File.createTempFile("test", ".png").also { it.writeBytes(ByteArray(4)) }
        val attachment = PrAttachment(file = file, name = "img.png")
        val result = PrService(client).uploadAzureDevOpsPrAttachment(
            token = "pat", username = "user",
            orgUrl = "https://dev.azure.com/org", project = "proj",
            repo = "repo", prId = 42, attachment = attachment,
        )
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().contains("attachments/img.png"))
        file.delete()
    }

    @Test
    fun uploadAzureDevOpsPrAttachment_errorResponse_returnsFailure() = runTest {
        val client = mockClient { _ ->
            respond(
                content = ByteReadChannel("""{"message":"Unauthorized"}"""),
                status = HttpStatusCode.Unauthorized,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val file = File.createTempFile("test", ".png").also { it.writeBytes(ByteArray(4)) }
        val attachment = PrAttachment(file = file, name = "img.png")
        val result = PrService(client).uploadAzureDevOpsPrAttachment(
            token = "bad", username = "user",
            orgUrl = "https://dev.azure.com/org", project = "proj",
            repo = "repo", prId = 42, attachment = attachment,
        )
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("401"))
        file.delete()
    }

    // --- updateAzureDevOpsPrDescription (HTTP mock) ---

    @Test
    fun updateAzureDevOpsPrDescription_success() = runTest {
        var capturedBody = ""
        val client = mockClient { request ->
            assertEquals(HttpMethod.Patch, request.method)
            assertTrue(request.url.toString().contains("pullrequests/42"))
            capturedBody = (request.body as io.ktor.http.content.TextContent).text
            respond(
                content = ByteReadChannel("""{"pullRequestId":42}"""),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val result = PrService(client).updateAzureDevOpsPrDescription(
            token = "pat", username = "user",
            orgUrl = "https://dev.azure.com/org", project = "proj",
            repo = "repo", prId = 42, description = "Updated description",
        )
        assertTrue(result.isSuccess)
        assertTrue(capturedBody.contains("description"))
        assertTrue(capturedBody.contains("Updated description"))
    }

    @Test
    fun updateAzureDevOpsPrDescription_errorResponse_returnsFailure() = runTest {
        val client = mockClient { _ ->
            respond(
                content = ByteReadChannel("""{"message":"Not found"}"""),
                status = HttpStatusCode.NotFound,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val result = PrService(client).updateAzureDevOpsPrDescription(
            token = "pat", username = "user",
            orgUrl = "https://dev.azure.com/org", project = "proj",
            repo = "repo", prId = 99, description = "desc",
        )
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("404"))
    }
}
