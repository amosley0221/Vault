package com.crownedpixel.vault.data

import org.json.JSONObject

/**
 * org.json hands back the four-character string "null" for a JSON null, which is how a repository
 * with no description ends up displaying the word "null". This returns an empty string instead.
 */
fun JSONObject.text(key: String): String =
    if (isNull(key)) "" else optString(key).let { if (it == "null") "" else it }

/** One tracked repository as JSON — shared by on-device storage and library exports. */
fun TrackedRepo.toJson(): JSONObject = JSONObject()
    .put("owner", owner)
    .put("repo", repo)
    .put("displayName", displayName)
    .put("description", description)
    .put("packageName", packageName)
    .put("source", source.name)
    .put("latestTag", latestTag)
    .put("latestVersion", latestVersion)
    .put("latestPublishedAt", latestPublishedAt)
    .put("addedAt", addedAt)
    .put("installedTag", installedTag)

/** Returns null for a record missing the two fields that identify it. */
fun trackedRepoFrom(json: JSONObject): TrackedRepo? {
    val owner = json.text("owner")
    val repo = json.text("repo")
    if (owner.isBlank() || repo.isBlank()) return null
    return TrackedRepo(
        owner = owner,
        repo = repo,
        displayName = json.text("displayName").ifBlank { TrackedRepo.prettyName(repo) },
        description = json.text("description"),
        packageName = json.text("packageName").takeIf { it.isNotBlank() },
        source = AppSource.from(json.text("source")),
        latestTag = json.text("latestTag").takeIf { it.isNotBlank() },
        latestVersion = json.text("latestVersion").takeIf { it.isNotBlank() },
        latestPublishedAt = json.text("latestPublishedAt").takeIf { it.isNotBlank() },
        addedAt = json.optLong("addedAt"),
        installedTag = json.text("installedTag").takeIf { it.isNotBlank() },
    )
}
