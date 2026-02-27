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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Base64

@Serializable
private data class GitHubPrRequest(
    val title: String,
    val body: String,
    val head: String,
    val base: String,
)

@Serializable
private data class AzureDevOpsPrRequest(
    val title: String,
    val description: String,
    val sourceRefName: String,
    val targetRefName: String,
)

class PrService {
    private val json = Json { ignoreUnknownKeys = true }

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(json)
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
        val regex = Regex("""https://(?:[^@]+@)?dev\.azure\.com/([^/]+)/([^/]+)/_git/(.+)""")
        regex.find(remoteUrl)?.destructured?.let { (org, project, repo) ->
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
    ): Result<String> = runCatching {
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
        parsed["html_url"]?.jsonPrimitive?.content
            ?: error("No html_url in GitHub response: $responseBody")
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
    ): Result<String> = runCatching {
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
                )
            )
        }
        val responseBody = response.bodyAsText()
        if (!response.status.isSuccess()) {
            error("Azure DevOps API error ${response.status.value}: $responseBody")
        }
        val parsed = json.parseToJsonElement(responseBody).jsonObject
        parsed["_links"]?.jsonObject
            ?.get("web")?.jsonObject
            ?.get("href")?.jsonPrimitive?.content
            ?: parsed["remoteUrl"]?.jsonPrimitive?.content
            ?: error("No URL in Azure DevOps response: $responseBody")
    }
}
