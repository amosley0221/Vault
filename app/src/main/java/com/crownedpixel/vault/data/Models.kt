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
    /** When the file itself was last uploaded — the only thing that moves on a rolling tag. */
    val updatedAt: String = "",
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

    /**
     * Plenty of repositories publish under a rolling tag — `latest`, `apk-latest`,
     * `android-latest` — which carries no version at all. Look through the tag, then the release
     * title, then the APK filename before giving up.
     */
    val resolvedVersion: String?
        get() = Versions.extract(tag)
            ?: Versions.extract(name)
            ?: apkAssets.firstNotNullOfOrNull { Versions.extract(it.name) }

    /** When the APK was last uploaded, which on a rolling tag outruns the release date. */
    val timestamp: String
        get() = (apkAssets.map { it.updatedAt } + publishedAt)
            .filter { it.isNotBlank() }
            .maxByOrNull { Dates.epochMillis(it) ?: 0L }
            ?: publishedAt

    /** What the interface shows: a real version when there is one, otherwise the build date. */
    val version: String get() = resolvedVersion ?: Dates.day(timestamp)

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
    val latestVersion: String? = null,
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
            AppStatus.NOT_INSTALLED -> Versions.label(latestVersion)
            AppStatus.UPDATE, AppStatus.UPDATING ->
                "${installedVersion ?: "—"} → ${latestVersion ?: "—"}"
            AppStatus.CURRENT -> Versions.label(installedVersion)
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

    private val SEMVER = Regex("\\d+(?:\\.\\d+)+(?:[-+][A-Za-z0-9.]+)?")
    private val DATE = Regex("\\d{4}-\\d{2}-\\d{2}")
    private val NUMBER = Regex("\\d+(?:[-+.][A-Za-z0-9.]+)?")

    /**
     * Digs a version out of a tag, a release title, or an APK filename. Returns null when the text
     * carries no version at all, which is what separates `v1.4.2` from `android-latest`.
     */
    fun extract(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val cleaned = raw.trim()
            .removePrefix("refs/tags/")
            .removeSuffix(".apk")
            .removeSuffix(".aab")
        DATE.find(cleaned)?.let { return it.value }
        SEMVER.find(cleaned)?.let { return it.value }
        return NUMBER.find(cleaned)?.value
    }

    fun hasDigits(value: String?): Boolean = value?.any { it.isDigit() } == true

    fun isDate(value: String?): Boolean = value != null && DATE.matches(value)

    /** "v1.4.2", but "2026-08-23" for a date and the bare text for anything else. */
    fun label(version: String?): String = when {
        version.isNullOrBlank() -> "\u2014"
        isDate(version) -> version
        version.first().isDigit() -> "v$version"
        else -> version
    }

    fun normalize(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val value = raw.trim().removePrefix("refs/tags/").trimStart('v', 'V')
        return extract(value) ?: value
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
        // Build metadata such as the +7 in 0.1.0+7 counts upward, and 10 beats 9 numerically
        // however it sorts as text.
        val aBuild = a.substringAfter('+', "").toIntOrNull()
        val bBuild = b.substringAfter('+', "").toIntOrNull()
        if (aBuild != null && bBuild != null && aBuild != bBuild) return aBuild.compareTo(bBuild)

        val aPre = a.contains('-')
        val bPre = b.contains('-')
        if (aPre != bPre) return if (aPre) -1 else 1
        return a.compareTo(b)
    }

    fun isNewer(candidate: String?, installed: String?): Boolean = compare(candidate, installed) > 0
}

/**
 * Whether a release is newer than what sits on the device. Version numbers decide it when both
 * sides have one of the same kind; a repository that publishes under a rolling tag has no version
 * to compare, so the upload time of the APK asset decides instead.
 */
object UpdateCheck {

    private const val TOLERANCE_MS = 5 * 60 * 1000L

    fun isNewer(
        latestVersion: String?,
        latestMillis: Long?,
        installedVersion: String?,
        installedMillis: Long?,
    ): Boolean {
        if (installedVersion == null || latestVersion == null) return false
        val comparable = Versions.hasDigits(latestVersion) &&
            Versions.hasDigits(installedVersion) &&
            Versions.isDate(latestVersion) == Versions.isDate(installedVersion)
        if (comparable) return Versions.isNewer(latestVersion, installedVersion)
        if (latestMillis == null || installedMillis == null) return false
        return latestMillis > installedMillis + TOLERANCE_MS
    }
}
