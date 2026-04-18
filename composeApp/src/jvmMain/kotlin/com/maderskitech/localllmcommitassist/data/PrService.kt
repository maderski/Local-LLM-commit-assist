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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder
import java.util.Base64
import java.util.UUID

@Serializable
private data class GitHubPrRequest(
    val title: String,
    val body: String,
    val head: String,
    val base: String,
)

@Serializable
private data class GitHubReviewersRequest(
    val reviewers: List<String>,
)

data class GitHubPrResult(
    val url: String,
    val prNumber: Int? = null,
    val reviewerWarning: String? = null,
)

data class AzureDevOpsPrResult(
    val url: String,
    val prNumber: Int? = null,
)

@Serializable
private data class AzureDevOpsPrRequest(
    val title: String,
    val description: String,
    val sourceRefName: String,
    val targetRefName: String,
    val reviewers: List<AzureDevOpsReviewer> = emptyList(),
    val workItemRefs: List<AzureDevOpsWorkItemRef> = emptyList(),
    val labels: List<AzureDevOpsLabel> = emptyList(),
)

@Serializable
private data class AzureDevOpsReviewer(
    val id: String,
    val isRequired: Boolean,
)

@Serializable
private data class AzureDevOpsWorkItemRef(
    val id: String,
)

@Serializable
private data class AzureDevOpsLabel(
    val name: String,
)

class PrService(httpClient: HttpClient? = null) {
    private val json = Json { ignoreUnknownKeys = true }

    private val client = httpClient ?: HttpClient(CIO) {
        install(ContentNegotiation) {
            json(json)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 5 * 60 * 1000 // 5 minutes
            connectTimeoutMillis = 30 * 1000      // 30 seconds
            socketTimeoutMillis = 5 * 60 * 1000   // 5 minutes
        }
    }

    fun parseGitHubRemote(remoteUrl: String): Pair<String, String>? {
        val sshRegex = Regex("""git@github\.com:([^/]+)/(.+?)(?:\.git)?$""")
        sshRegex.find(remoteUrl)?.destructured?.let { (owner, repo) ->
            return Pair(owner, repo)
        }
        val httpsRegex = Regex("""https://github\.com/([^/]+)/(.+?)(?:\.git)?$""")
        httpsRegex.find(remoteUrl)?.destructured?.let { (owner, repo) ->
            return Pair(owner, repo)
        }
        return null
    }

    fun parseAzureDevOpsRemote(remoteUrl: String): Triple<String, String, String>? {
        // HTTPS: https://[user@]dev.azure.com/org/project/_git/repo[.git]
        val devAzureRegex = Regex("""https://(?:[^@]+@)?dev\.azure\.com/([^/]+)/([^/]+)/_git/(.+?)(?:\.git)?$""")
        devAzureRegex.find(remoteUrl)?.destructured?.let { (org, project, repo) ->
            val orgUrl = "https://dev.azure.com/$org"
            return Triple(orgUrl, project, repo)
        }

        // SSH: git@ssh.dev.azure.com:v3/org/project/repo
        val sshRegex = Regex("""git@ssh\.dev\.azure\.com:v3/([^/]+)/([^/]+)/(.+?)(?:\.git)?$""")
        sshRegex.find(remoteUrl)?.destructured?.let { (org, project, repo) ->
            val orgUrl = "https://dev.azure.com/$org"
            return Triple(orgUrl, project, repo)
        }

        // Legacy HTTPS: https://org.visualstudio.com/project/_git/repo[.git]
        val vstsRegex = Regex("""https://([^.]+)\.visualstudio\.com/([^/]+)/_git/(.+?)(?:\.git)?$""")
        vstsRegex.find(remoteUrl)?.destructured?.let { (org, project, repo) ->
            val orgUrl = "https://dev.azure.com/$org"
            return Triple(orgUrl, project, repo)
        }

        // Legacy HTTPS with DefaultCollection: https://org.visualstudio.com/DefaultCollection/project/_git/repo[.git]
        val vstsDefaultCollectionRegex = Regex("""https://([^.]+)\.visualstudio\.com/DefaultCollection/([^/]+)/_git/(.+?)(?:\.git)?$""")
        vstsDefaultCollectionRegex.find(remoteUrl)?.destructured?.let { (org, project, repo) ->
            val orgUrl = "https://dev.azure.com/$org"
            return Triple(orgUrl, project, repo)
        }

        return null
    }

    suspend fun createGitHubPr(
        token: String,
        owner: String,
        repo: String,
        title: String,
        body: String,
        head: String,
        base: String,
        reviewers: List<String> = emptyList(),
    ): Result<GitHubPrResult> = runCatching {
        val response = client.post("https://api.github.com/repos/$owner/$repo/pulls") {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.Accept, "application/vnd.github+json")
            header("X-GitHub-Api-Version", "2022-11-28")
            contentType(ContentType.Application.Json)
            setBody(GitHubPrRequest(title = title, body = body, head = head, base = base))
        }
        val responseBody = response.bodyAsText()
        if (!response.status.isSuccess()) {
            error("GitHub API error ${response.status.value}: $responseBody")
        }
        val parsed = json.parseToJsonElement(responseBody).jsonObject
        val htmlUrl = parsed["html_url"]?.jsonPrimitive?.content
            ?: error("No html_url in GitHub response: $responseBody")

        val prNumberEarly = parsed["number"]?.jsonPrimitive?.content?.toIntOrNull()
        if (reviewers.isEmpty()) return@runCatching GitHubPrResult(url = htmlUrl, prNumber = prNumberEarly)

        val prNumber = prNumberEarly
        if (prNumber == null) {
            return@runCatching GitHubPrResult(url = htmlUrl, reviewerWarning = "PR created but reviewers could not be assigned: no PR number in response")
        }

        val reviewerWarning = runCatching {
            val reviewerResponse = client.post("https://api.github.com/repos/$owner/$repo/pulls/$prNumber/requested_reviewers") {
                header(HttpHeaders.Authorization, "Bearer $token")
                header(HttpHeaders.Accept, "application/vnd.github+json")
                header("X-GitHub-Api-Version", "2022-11-28")
                contentType(ContentType.Application.Json)
                setBody(GitHubReviewersRequest(reviewers = reviewers))
            }
            if (!reviewerResponse.status.isSuccess()) {
                "PR created but reviewer assignment failed (${reviewerResponse.status.value}): ${reviewerResponse.bodyAsText()}"
            } else null
        }.getOrElse { e ->
            "PR created but reviewer assignment failed: ${e.message}"
        }

        GitHubPrResult(url = htmlUrl, prNumber = prNumber, reviewerWarning = reviewerWarning)
    }

    fun inferTags(repoName: String): List<String> {
        val name = repoName.lowercase()
        val tags = mutableListOf<String>()
        if (name.contains("kmp") || name.contains("multiplatform")) tags.add("Kotlin Multiplatform")
        if (name.contains("ios") || name.contains("swift")) tags.add("iOS")
        if (name.contains("android")) tags.add("Android")
        if (name.contains("shell") || name.contains("bash") || name.contains("script")) tags.add("Shell")
        if (name.contains("web") || name.contains("react") || name.contains("angular") || name.contains("vue")) tags.add("Web")
        if (name.contains("api") || name.contains("backend") || name.contains("server")) tags.add("Backend")
        return tags
    }

    fun extractWorkItemIds(branchName: String): List<String> {
        val regex = Regex("""(\d+)""")
        val match = regex.find(branchName)
        return listOfNotNull(match?.value)
    }

    suspend fun createAzureDevOpsPr(
        token: String,
        username: String,
        orgUrl: String,
        project: String,
        repo: String,
        title: String,
        description: String,
        sourceBranch: String,
        targetBranch: String,
        reviewers: List<AzureReviewer> = emptyList(),
        workItemIds: List<String> = emptyList(),
        tags: List<String> = emptyList(),
    ): Result<AzureDevOpsPrResult> = runCatching {
        val encodedToken = Base64.getEncoder().encodeToString("$username:$token".toByteArray())
        val url = "$orgUrl/$project/_apis/git/repositories/$repo/pullrequests?api-version=7.1"

        val response = client.post(url) {
            header(HttpHeaders.Authorization, "Basic $encodedToken")
            contentType(ContentType.Application.Json)
            setBody(
                AzureDevOpsPrRequest(
                    title = title,
                    description = description,
                    sourceRefName = "refs/heads/$sourceBranch",
                    targetRefName = "refs/heads/$targetBranch",
                    reviewers = reviewers.map { AzureDevOpsReviewer(id = it.id, isRequired = it.isRequired) },
                    workItemRefs = workItemIds.map { AzureDevOpsWorkItemRef(id = it) },
                    labels = tags.map { AzureDevOpsLabel(name = it) },
                )
            )
        }
        val responseBody = response.bodyAsText()
        if (!response.status.isSuccess()) {
            error("Azure DevOps API error ${response.status.value}: $responseBody")
        }
        val parsed = json.parseToJsonElement(responseBody).jsonObject
        val pullRequestId = parsed["pullRequestId"]?.jsonPrimitive?.content
        val prUrl = parsed["_links"]?.jsonObject
            ?.get("web")?.jsonObject
            ?.get("href")?.jsonPrimitive?.content
            ?: parsed["remoteUrl"]?.jsonPrimitive?.content
            ?: parsed["repository"]?.jsonObject
                ?.get("webUrl")?.jsonPrimitive?.content
                ?.let { repoWebUrl -> pullRequestId?.let { "$repoWebUrl/pullrequest/$it" } }
            ?: pullRequestId?.let { "$orgUrl/$project/_git/$repo/pullrequest/$it" }
            ?: error("No URL in Azure DevOps response: $responseBody")
        AzureDevOpsPrResult(url = prUrl, prNumber = pullRequestId?.toIntOrNull())
    }

    suspend fun uploadFileToGitHubRepo(
        token: String,
        owner: String,
        repo: String,
        branch: String,
        attachment: PrAttachment,
    ): Result<String> = runCatching {
        val fileBytes = attachment.file.readBytes()
        val base64Content = Base64.getEncoder().encodeToString(fileBytes)
        val filePath = ".github/pr-assets/${UUID.randomUUID()}-${attachment.name}"

        val response = client.put("https://api.github.com/repos/$owner/$repo/contents/$filePath") {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.Accept, "application/vnd.github+json")
            header("X-GitHub-Api-Version", "2022-11-28")
            contentType(ContentType.Application.Json)
            setBody(
                mapOf(
                    "message" to "Add PR attachment: ${attachment.name}",
                    "content" to base64Content,
                    "branch" to branch,
                )
            )
        }
        val responseBody = response.bodyAsText()
        if (!response.status.isSuccess()) {
            error("GitHub upload error ${response.status.value}: $responseBody")
        }
        val parsed = json.parseToJsonElement(responseBody).jsonObject
        parsed["content"]?.jsonObject?.get("download_url")?.jsonPrimitive?.content
            ?: error("No download_url in GitHub upload response: $responseBody")
    }

    suspend fun uploadAzureDevOpsAttachment(
        token: String,
        username: String,
        orgUrl: String,
        project: String,
        attachment: PrAttachment,
    ): Result<String> = runCatching {
        val encodedToken = Base64.getEncoder().encodeToString("$username:$token".toByteArray())
        val fileBytes = attachment.file.readBytes()
        val encodedFileName = URLEncoder.encode(attachment.name, "UTF-8")
        val url = "$orgUrl/$project/_apis/wit/attachments?fileName=$encodedFileName&api-version=7.1"

        val response = client.post(url) {
            header(HttpHeaders.Authorization, "Basic $encodedToken")
            contentType(ContentType.Application.OctetStream)
            setBody(fileBytes)
        }
        val responseBody = response.bodyAsText()
        if (!response.status.isSuccess()) {
            error("Azure DevOps upload error ${response.status.value}: $responseBody")
        }
        val parsed = json.parseToJsonElement(responseBody).jsonObject
        parsed["url"]?.jsonPrimitive?.content
            ?: error("No url in Azure DevOps upload response: $responseBody")
    }

    suspend fun updateAzureWorkItemState(
        token: String,
        username: String,
        orgUrl: String,
        project: String,
        workItemId: String,
        state: String,
    ): Result<Unit> = runCatching {
        val encodedToken = Base64.getEncoder().encodeToString("$username:$token".toByteArray())
        val url = "$orgUrl/$project/_apis/wit/workitems/$workItemId?api-version=7.1"

        val response = client.patch(url) {
            header(HttpHeaders.Authorization, "Basic $encodedToken")
            contentType(ContentType("application", "json-patch+json"))
            setBody("""[{"op":"add","path":"/fields/System.State","value":"$state"}]""")
        }
        val responseBody = response.bodyAsText()
        if (!response.status.isSuccess()) {
            error("Azure DevOps work item update error ${response.status.value}: $responseBody")
        }
    }

    suspend fun uploadAzureDevOpsPrAttachment(
        token: String,
        username: String,
        orgUrl: String,
        project: String,
        repo: String,
        prId: Int,
        attachment: PrAttachment,
    ): Result<String> = runCatching {
        val encodedToken = Base64.getEncoder().encodeToString("$username:$token".toByteArray())
        val fileBytes = attachment.file.readBytes()
        val encodedFileName = URLEncoder.encode(attachment.name, "UTF-8")
        val url = "$orgUrl/$project/_apis/git/repositories/$repo/pullRequests/$prId/attachments/$encodedFileName?api-version=7.1"

        val response = client.post(url) {
            header(HttpHeaders.Authorization, "Basic $encodedToken")
            contentType(ContentType.Application.OctetStream)
            setBody(fileBytes)
        }
        val responseBody = response.bodyAsText()
        if (!response.status.isSuccess()) {
            error("Azure DevOps PR attachment upload error ${response.status.value}: $responseBody")
        }
        val parsed = json.parseToJsonElement(responseBody).jsonObject
        parsed["url"]?.jsonPrimitive?.content
            ?: error("No url in Azure DevOps PR attachment response: $responseBody")
    }

    suspend fun updateAzureDevOpsPrDescription(
        token: String,
        username: String,
        orgUrl: String,
        project: String,
        repo: String,
        prId: Int,
        description: String,
    ): Result<Unit> = runCatching {
        val encodedToken = Base64.getEncoder().encodeToString("$username:$token".toByteArray())
        val url = "$orgUrl/$project/_apis/git/repositories/$repo/pullrequests/$prId?api-version=7.1"

        val response = client.patch(url) {
            header(HttpHeaders.Authorization, "Basic $encodedToken")
            contentType(ContentType.Application.Json)
            setBody("""{"description":${Json.encodeToString(description)}}""")
        }
        val responseBody = response.bodyAsText()
        if (!response.status.isSuccess()) {
            error("Azure DevOps PR description update error ${response.status.value}: $responseBody")
        }
    }

    fun buildMarkdownReference(attachment: PrAttachment, url: String): String {
        val safeName = attachment.name.replace("]", "\\]").replace("[", "\\[")
        return if (attachment.isVideo) {
            "[![$safeName]($url)]($url)"
        } else {
            "![$safeName]($url)"
        }
    }
}
