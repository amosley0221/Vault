package com.crownedpixel.vault.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class GitHubException(val status: Int, message: String) : IOException(message)

/**
 * A deliberately small GitHub client built on HttpURLConnection and org.json — everything Vault
 * needs is a handful of REST calls, and this keeps the APK free of networking dependencies.
 */
object GitHubApi {

    private const val API = "https://api.github.com"
    private const val WEB = "https://github.com"
    private const val USER_AGENT = "Vault-APK-Manager"
    private const val ACCEPT = "application/vnd.github+json"

    // ---------------------------------------------------------------- releases

    suspend fun releases(owner: String, repo: String, token: String?, perPage: Int = 20): List<ReleaseInfo> {
        val body = get("$API/repos/$owner/$repo/releases?per_page=$perPage", token)
        val array = JSONArray(body)
        return (0 until array.length()).map { parseRelease(array.getJSONObject(it)) }
            .filterNot { it.draft }
    }

    suspend fun latestReleaseWithApk(
        owner: String,
        repo: String,
        token: String?,
        includePrerelease: Boolean,
    ): ReleaseInfo? = releases(owner, repo, token)
        .filter { includePrerelease || !it.prerelease }
        .firstOrNull { it.apkAssets.isNotEmpty() }

    private fun parseRelease(json: JSONObject): ReleaseInfo {
        val assetsJson = json.optJSONArray("assets") ?: JSONArray()
        val assets = (0 until assetsJson.length()).map { index ->
            val asset = assetsJson.getJSONObject(index)
            ReleaseAsset(
                name = asset.optString("name"),
                browserDownloadUrl = asset.optString("browser_download_url"),
                apiUrl = asset.optString("url"),
                size = asset.optLong("size"),
            )
        }
        return ReleaseInfo(
            tag = json.optString("tag_name"),
            name = json.optString("name").ifBlank { json.optString("tag_name") },
            publishedAt = json.optString("published_at").ifBlank { json.optString("created_at") },
            body = json.optString("body"),
            prerelease = json.optBoolean("prerelease"),
            draft = json.optBoolean("draft"),
            assets = assets,
        )
    }

    // ---------------------------------------------------------------- repositories

    suspend fun repository(owner: String, repo: String, token: String?): JSONObject =
        JSONObject(get("$API/repos/$owner/$repo", token))

    suspend fun repositoryExists(owner: String, repo: String, token: String?): Boolean = try {
        repository(owner, repo, token)
        true
    } catch (error: GitHubException) {
        if (error.status == 404) false else throw error
    }

    /** Repositories owned by the signed-in user, most recently pushed first. */
    suspend fun myRepositories(token: String): List<JSONObject> =
        pagedRepos("$API/user/repos?affiliation=owner&sort=pushed&per_page=100", token)

    suspend fun starredRepositories(token: String): List<JSONObject> =
        pagedRepos("$API/user/starred?per_page=100", token)

    private suspend fun pagedRepos(url: String, token: String): List<JSONObject> {
        val array = JSONArray(get(url, token))
        return (0 until array.length()).map { array.getJSONObject(it) }
    }

    suspend fun currentUser(token: String): GitHubAccount {
        val json = JSONObject(get("$API/user", token))
        return GitHubAccount(
            login = json.optString("login"),
            name = json.optString("name").takeIf { it.isNotBlank() },
            scopes = "",
        )
    }

    // ---------------------------------------------------------------- pages

    /** Returns the published Pages URL for a repository, or null when it has no Pages site. */
    suspend fun pagesUrl(owner: String, repo: String, token: String?): String? = try {
        val json = JSONObject(get("$API/repos/$owner/$repo/pages", token))
        json.optString("html_url").takeIf { it.isNotBlank() }
    } catch (error: GitHubException) {
        if (error.status == 404) null else throw error
    }

    // ---------------------------------------------------------------- contents

    suspend fun fileSha(owner: String, repo: String, path: String, token: String): String? = try {
        JSONObject(get("$API/repos/$owner/$repo/contents/$path", token)).optString("sha")
            .takeIf { it.isNotBlank() }
    } catch (error: GitHubException) {
        if (error.status == 404) null else throw error
    }

    suspend fun fileExists(owner: String, repo: String, path: String, token: String?): Boolean = try {
        get("$API/repos/$owner/$repo/contents/$path", token)
        true
    } catch (error: GitHubException) {
        if (error.status == 404) false else throw error
    }

    /** Creates or updates a file; returns the commit sha. */
    suspend fun putFile(
        owner: String,
        repo: String,
        path: String,
        base64Content: String,
        message: String,
        token: String,
        branch: String? = null,
    ): String {
        val payload = JSONObject()
            .put("message", message)
            .put("content", base64Content)
        fileSha(owner, repo, path, token)?.let { payload.put("sha", it) }
        branch?.let { payload.put("branch", it) }
        val response = JSONObject(
            send("PUT", "$API/repos/$owner/$repo/contents/$path", token, payload.toString()),
        )
        return response.optJSONObject("commit")?.optString("sha").orEmpty()
    }

    // ---------------------------------------------------------------- actions

    data class WorkflowRun(
        val id: Long,
        val status: String,
        val conclusion: String,
        val htmlUrl: String,
        val name: String,
    )

    suspend fun latestRun(owner: String, repo: String, workflowFile: String, token: String): WorkflowRun? = try {
        val body = get("$API/repos/$owner/$repo/actions/workflows/$workflowFile/runs?per_page=1", token)
        val runs = JSONObject(body).optJSONArray("workflow_runs") ?: JSONArray()
        if (runs.length() == 0) {
            null
        } else {
            val run = runs.getJSONObject(0)
            WorkflowRun(
                id = run.optLong("id"),
                status = run.optString("status"),
                conclusion = run.optString("conclusion"),
                htmlUrl = run.optString("html_url"),
                name = run.optString("name"),
            )
        }
    } catch (error: GitHubException) {
        if (error.status == 404) null else throw error
    }

    suspend fun dispatchWorkflow(
        owner: String,
        repo: String,
        workflowFile: String,
        ref: String,
        token: String,
    ) {
        send(
            "POST",
            "$API/repos/$owner/$repo/actions/workflows/$workflowFile/dispatches",
            token,
            JSONObject().put("ref", ref).toString(),
        )
    }

    // ---------------------------------------------------------------- device flow

    data class DeviceCode(
        val deviceCode: String,
        val userCode: String,
        val verificationUri: String,
        val intervalSeconds: Int,
        val expiresInSeconds: Int,
    )

    sealed interface DevicePoll {
        data class Success(val token: String) : DevicePoll
        data object Pending : DevicePoll
        data class SlowDown(val intervalSeconds: Int) : DevicePoll
        data class Failed(val message: String) : DevicePoll
    }

    suspend fun requestDeviceCode(clientId: String, scope: String): DeviceCode {
        val body = "client_id=${encode(clientId)}&scope=${encode(scope)}"
        val json = JSONObject(form("$WEB/login/device/code", body))
        json.optString("error").takeIf { it.isNotBlank() }?.let {
            throw GitHubException(400, json.optString("error_description").ifBlank { it })
        }
        return DeviceCode(
            deviceCode = json.getString("device_code"),
            userCode = json.getString("user_code"),
            verificationUri = json.optString("verification_uri").ifBlank { "https://github.com/login/device" },
            intervalSeconds = json.optInt("interval", 5),
            expiresInSeconds = json.optInt("expires_in", 900),
        )
    }

    suspend fun pollDeviceToken(clientId: String, deviceCode: String): DevicePoll {
        val body = "client_id=${encode(clientId)}&device_code=${encode(deviceCode)}" +
            "&grant_type=urn:ietf:params:oauth:grant-type:device_code"
        val json = JSONObject(form("$WEB/login/oauth/access_token", body))
        json.optString("access_token").takeIf { it.isNotBlank() }?.let { return DevicePoll.Success(it) }
        return when (val error = json.optString("error")) {
            "authorization_pending" -> DevicePoll.Pending
            "slow_down" -> DevicePoll.SlowDown(json.optInt("interval", 10))
            "" -> DevicePoll.Failed("Unexpected response from GitHub.")
            else -> DevicePoll.Failed(json.optString("error_description").ifBlank { error })
        }
    }

    // ---------------------------------------------------------------- plumbing

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    suspend fun get(url: String, token: String?): String = send("GET", url, token, null)

    private suspend fun form(url: String, body: String): String = withContext(Dispatchers.IO) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 20_000
            readTimeout = 30_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            setRequestProperty("User-Agent", USER_AGENT)
        }
        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        readResponse(connection)
    }

    private suspend fun send(
        method: String,
        url: String,
        token: String?,
        body: String?,
    ): String = withContext(Dispatchers.IO) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20_000
            readTimeout = 30_000
            setRequestProperty("Accept", ACCEPT)
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", USER_AGENT)
            if (!token.isNullOrBlank()) setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }
        if (body != null) {
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        }
        readResponse(connection)
    }

    private fun readResponse(connection: HttpURLConnection): String {
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (status in 200..299) {
            connection.disconnect()
            return text
        }
        val remaining = connection.getHeaderField("x-ratelimit-remaining")
        connection.disconnect()
        val detail = runCatching { JSONObject(text).optString("message") }.getOrNull()
        val message = when {
            status == 403 && remaining == "0" ->
                "GitHub rate limit reached. Sign in to raise the limit to 5,000 checks an hour."
            status == 401 -> "GitHub rejected the token. Sign in again."
            status == 404 -> "Not found on GitHub."
            !detail.isNullOrBlank() -> detail
            else -> "GitHub returned HTTP $status."
        }
        throw GitHubException(status, message)
    }
}
