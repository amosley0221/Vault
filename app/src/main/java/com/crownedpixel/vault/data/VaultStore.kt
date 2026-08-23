package com.crownedpixel.vault.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Preferences(
    val includePrereleases: Boolean = false,
    val wifiOnly: Boolean = true,
    val notifyOnRelease: Boolean = true,
    val checkIntervalHours: Int = 12,
    val onboarded: Boolean = false,
)

/** Tracked repositories and user preferences, persisted as plain shared preferences. */
class VaultStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("vault_state", Context.MODE_PRIVATE)

    // ---------------------------------------------------------------- repositories

    fun readRepos(): List<TrackedRepo> {
        val raw = prefs.getString(KEY_REPOS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { index ->
                val json = array.getJSONObject(index)
                TrackedRepo(
                    owner = json.getString("owner"),
                    repo = json.getString("repo"),
                    displayName = json.text("displayName").ifBlank { json.getString("repo") },
                    description = json.text("description"),
                    packageName = json.text("packageName").takeIf { it.isNotBlank() },
                    source = AppSource.from(json.text("source")),
                    latestTag = json.text("latestTag").takeIf { it.isNotBlank() },
                    latestPublishedAt = json.text("latestPublishedAt").takeIf { it.isNotBlank() },
                    addedAt = json.optLong("addedAt"),
                    installedTag = json.text("installedTag").takeIf { it.isNotBlank() },
                )
            }
        }.getOrDefault(emptyList())
    }

    fun writeRepos(repos: List<TrackedRepo>) {
        val array = JSONArray()
        repos.forEach { repo ->
            array.put(
                JSONObject()
                    .put("owner", repo.owner)
                    .put("repo", repo.repo)
                    .put("displayName", repo.displayName)
                    .put("description", repo.description)
                    .put("packageName", repo.packageName)
                    .put("source", repo.source.name)
                    .put("latestTag", repo.latestTag)
                    .put("latestPublishedAt", repo.latestPublishedAt)
                    .put("addedAt", repo.addedAt)
                    .put("installedTag", repo.installedTag),
            )
        }
        prefs.edit().putString(KEY_REPOS, array.toString()).apply()
    }

    /** Tags already announced through a notification, so each release is only reported once. */
    fun notifiedTag(slug: String): String? = prefs.getString("notified:$slug", null)

    fun markNotified(slug: String, tag: String) {
        prefs.edit().putString("notified:$slug", tag).apply()
    }

    // ---------------------------------------------------------------- preferences

    fun readPreferences(): Preferences = Preferences(
        includePrereleases = prefs.getBoolean(KEY_PRERELEASE, false),
        wifiOnly = prefs.getBoolean(KEY_WIFI, true),
        notifyOnRelease = prefs.getBoolean(KEY_NOTIFY, true),
        checkIntervalHours = prefs.getInt(KEY_INTERVAL, 12),
        onboarded = prefs.getBoolean(KEY_ONBOARDED, false),
    )

    fun writePreferences(preferences: Preferences) {
        prefs.edit()
            .putBoolean(KEY_PRERELEASE, preferences.includePrereleases)
            .putBoolean(KEY_WIFI, preferences.wifiOnly)
            .putBoolean(KEY_NOTIFY, preferences.notifyOnRelease)
            .putInt(KEY_INTERVAL, preferences.checkIntervalHours)
            .putBoolean(KEY_ONBOARDED, preferences.onboarded)
            .apply()
    }

    private companion object {
        const val KEY_REPOS = "repos"
        const val KEY_PRERELEASE = "pref_prerelease"
        const val KEY_WIFI = "pref_wifi_only"
        const val KEY_NOTIFY = "pref_notify"
        const val KEY_INTERVAL = "pref_interval_hours"
        const val KEY_ONBOARDED = "onboarded"
    }
}
