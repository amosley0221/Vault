package com.crownedpixel.vault.data

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** What Vault can do with a repository that publishes no APK of its own. */
enum class RepoKind { GRADLE_PROJECT, PAGES_SITE, UNKNOWN }

/**
 * Commits the build workflows Vault ships in its assets into a repository the user owns. From
 * then on GitHub Actions does the compiling and publishing, and the repository behaves like any
 * other tracked app.
 */
object WorkflowCommitter {

    const val BUILD_WORKFLOW = "vault-android-build.yml"
    const val WRAPPER_WORKFLOW = "vault-web-wrapper.yml"

    private fun template(context: Context, name: String): String =
        context.assets.open("workflows/$name").bufferedReader().use { it.readText() }

    suspend fun defaultBranch(owner: String, repo: String, token: String?): String =
        GitHubApi.repository(owner, repo, token).text("default_branch").ifBlank { "main" }

    /** Looks for the marks of a Gradle Android project, or of a Pages site. */
    suspend fun detectKind(owner: String, repo: String, token: String?): RepoKind {
        val repository: JSONObject = GitHubApi.repository(owner, repo, token)
        val gradleFiles = listOf("settings.gradle.kts", "settings.gradle", "build.gradle.kts", "build.gradle")
        for (path in gradleFiles) {
            if (GitHubApi.fileExists(owner, repo, path, token)) return RepoKind.GRADLE_PROJECT
        }
        if (repository.optBoolean("has_pages")) return RepoKind.PAGES_SITE
        return RepoKind.UNKNOWN
    }

    suspend fun commitBuildWorkflow(
        context: Context,
        owner: String,
        repo: String,
        token: String,
        branch: String,
    ) {
        val body = template(context, BUILD_WORKFLOW).replace("__VAULT_BRANCH__", branch)
        commit(owner, repo, ".github/workflows/$BUILD_WORKFLOW", body, token, branch, "Add Vault APK build workflow")
    }

    suspend fun commitWrapperWorkflow(
        context: Context,
        owner: String,
        repo: String,
        token: String,
        branch: String,
        siteUrl: String,
        appName: String,
        packageName: String,
    ) {
        val body = template(context, WRAPPER_WORKFLOW)
            .replace("__VAULT_BRANCH__", branch)
            .replace("__VAULT_SITE_URL__", siteUrl.trimEnd('/'))
            .replace("__VAULT_APP_NAME__", appName.replace("'", ""))
            .replace("__VAULT_PACKAGE__", packageName)
            .replace("__VAULT_INITIAL__", appName.trim().take(1).uppercase().ifBlank { "V" })
        commit(owner, repo, ".github/workflows/$WRAPPER_WORKFLOW", body, token, branch, "Add Vault web wrapper workflow")
    }

    private suspend fun commit(
        owner: String,
        repo: String,
        path: String,
        body: String,
        token: String,
        branch: String,
        message: String,
    ) = withContext(Dispatchers.IO) {
        val encoded = Base64.encodeToString(body.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        GitHubApi.putFile(owner, repo, path, encoded, message, token, branch)
        Unit
    }

    /** A launcher package name derived from the repository, stable across regenerations. */
    fun wrapperPackageName(owner: String, repo: String): String {
        fun sanitize(value: String): String = value.lowercase()
            .map { if (it.isLetterOrDigit()) it else '_' }
            .joinToString("")
            .trim('_')
            .ifBlank { "site" }
            .let { if (it.first().isDigit()) "x$it" else it }
        return "io.github.${sanitize(owner)}.${sanitize(repo)}"
    }
}
