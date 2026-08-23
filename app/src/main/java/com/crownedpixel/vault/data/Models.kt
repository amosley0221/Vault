package com.crownedpixel.vault.data

/** How a tracked repository ended up in the library. */
enum class AppSource {
    /** The repository publishes .apk assets on its releases. */
    RELEASES,

    /** A GitHub Pages site wrapped into a WebView APK by a Vault-committed workflow. */
    WRAPPER,

    /** An Android source repository built by a Vault-committed GitHub Actions workflow. */
    ACTIONS_BUILD;

    companion object {
        fun from(raw: String?): AppSource = entries.firstOrNull { it.name == raw } ?: RELEASES
    }
}

enum class AppStatus { NOT_INSTALLED, UPDATE, UPDATING, CURRENT }

data class ReleaseAsset(
    val name: String,
    val browserDownloadUrl: String,
    val apiUrl: String,
    val size: Long,
) {
    val isApk: Boolean get() = name.endsWith(".apk", ignoreCase = true)
}

data class ReleaseInfo(
    val tag: String,
    val name: String,
    val publishedAt: String,
    val body: String,
    val prerelease: Boolean,
    val draft: Boolean,
    val assets: List<ReleaseAsset>,
) {
    val apkAssets: List<ReleaseAsset> get() = assets.filter { it.isApk }
    val version: String get() = Versions.normalize(tag.ifBlank { name })

    /** Release-note bullets, one per meaningful markdown line. */
    fun noteLines(limit: Int = 6): List<String> = body
        .lineSequence()
        .map { it.trim().removePrefix("*").removePrefix("-").removePrefix("•").trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith("<") && !it.startsWith("|") }
        .map { it.replace(Regex("\\*\\*|__|`"), "") }
        .take(limit)
        .toList()
}

/** A repository the user tracks, as persisted on device. */
data class TrackedRepo(
    val owner: String,
    val repo: String,
    val displayName: String,
    val description: String = "",
    val packageName: String? = null,
    val source: AppSource = AppSource.RELEASES,
    val latestTag: String? = null,
    val latestPublishedAt: String? = null,
    val addedAt: Long = 0L,
    /** Version last installed through Vault; a fallback when the package is not visible. */
    val installedTag: String? = null,
) {
    val slug: String get() = "$owner/$repo"
    val initial: String get() = displayName.trim().firstOrNull()?.uppercase() ?: "·"

    companion object {
        fun fromSlug(slug: String, source: AppSource = AppSource.RELEASES): TrackedRepo {
            val owner = slug.substringBefore('/')
            val repo = slug.substringAfter('/')
            return TrackedRepo(
                owner = owner,
                repo = repo,
                displayName = prettyName(repo),
                source = source,
            )
        }

        fun prettyName(repo: String): String = repo
            .replace('-', ' ')
            .replace('_', ' ')
            .split(' ')
            .filter { it.isNotBlank() }
            .joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
            .ifBlank { repo }
    }
}

/** A tracked repository combined with everything known at runtime. */
data class LibraryApp(
    val tracked: TrackedRepo,
    val installedVersion: String?,
    val latestVersion: String?,
    val publishedLabel: String,
    val status: AppStatus,
    val progress: Float = 0f,
    val progressStage: String = "",
    val releases: List<ReleaseInfo> = emptyList(),
    val error: String? = null,
) {
    val id: String get() = tracked.slug
    val initial: String get() = tracked.initial
    val name: String get() = tracked.displayName

    val versionLine: String
        get() = when (status) {
            AppStatus.NOT_INSTALLED -> latestVersion?.let { "v$it" } ?: "—"
            AppStatus.UPDATE, AppStatus.UPDATING ->
                "${installedVersion ?: "—"} → ${latestVersion ?: "—"}"
            AppStatus.CURRENT -> installedVersion?.let { "v$it" } ?: "—"
        }

    val badge: String
        get() = when (status) {
            AppStatus.NOT_INSTALLED -> "Not installed"
            AppStatus.UPDATE -> "Update"
            AppStatus.UPDATING -> "Updating"
            AppStatus.CURRENT -> "Current"
        }
}

/** A repository offered by one of the pickers. */
data class RepoCandidate(
    val slug: String,
    val description: String,
    val tag: String,
    val buildable: Boolean,
    val isWebsite: Boolean = false,
)

data class GitHubAccount(
    val login: String,
    val name: String?,
    val scopes: String,
)

/** Version comparison over the loose tags found in the wild: v1.2.3, 1.2.3-beta.1, 2024.08.01. */
object Versions {
    fun normalize(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        var value = raw.trim()
        value = value.removePrefix("refs/tags/")
        value = value.trimStart('v', 'V')
        // Tags such as "release-1.4.0" or "app-v2.1".
        val match = Regex("\\d+(\\.\\d+)*([\\-+._][A-Za-z0-9.\\-]+)?").find(value)
        return match?.value ?: value
    }

    fun compare(left: String?, right: String?): Int {
        val a = normalize(left)
        val b = normalize(right)
        if (a == b) return 0
        if (a.isEmpty()) return -1
        if (b.isEmpty()) return 1
        val aCore = a.substringBefore('-').substringBefore('+')
        val bCore = b.substringBefore('-').substringBefore('+')
        val aParts = aCore.split('.').mapNotNull { it.toIntOrNull() }
        val bParts = bCore.split('.').mapNotNull { it.toIntOrNull() }
        for (i in 0 until maxOf(aParts.size, bParts.size)) {
            val x = aParts.getOrElse(i) { 0 }
            val y = bParts.getOrElse(i) { 0 }
            if (x != y) return x.compareTo(y)
        }
        val aPre = a.contains('-')
        val bPre = b.contains('-')
        if (aPre != bPre) return if (aPre) -1 else 1
        return a.compareTo(b)
    }

    fun isNewer(candidate: String?, installed: String?): Boolean = compare(candidate, installed) > 0
}
