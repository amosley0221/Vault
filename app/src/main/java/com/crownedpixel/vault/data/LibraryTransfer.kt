package com.crownedpixel.vault.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A library as it travels between devices. */
data class TransferBundle(
    val repositories: List<TrackedRepo>,
    val preferences: Preferences?,
    val token: String?,
)

/**
 * Moving a library to a second device. Android keystore keys never leave the phone they were
 * generated on, and Vault has backup switched off, so a handoff has to be something the user
 * performs deliberately: a file they export, send however they like, and import on the other side.
 */
object LibraryTransfer {

    private const val FORMAT = 1
    private const val AUTHORITY_SUFFIX = ".files"

    fun encode(
        repositories: List<TrackedRepo>,
        preferences: Preferences,
        token: String?,
    ): String {
        val json = JSONObject()
            .put("vault", FORMAT)
            .put("exportedAt", OffsetDateTime.now(ZoneOffset.UTC).toString())
            .put(
                "preferences",
                JSONObject()
                    .put("includePrereleases", preferences.includePrereleases)
                    .put("wifiOnly", preferences.wifiOnly)
                    .put("notifyOnRelease", preferences.notifyOnRelease)
                    .put("checkIntervalHours", preferences.checkIntervalHours),
            )
            .put("repositories", JSONArray().apply { repositories.forEach { put(it.toJson()) } })
        if (!token.isNullOrBlank()) json.put("token", token)
        return json.toString(2)
    }

    fun decode(raw: String): TransferBundle {
        val json = runCatching { JSONObject(raw) }.getOrNull()
            ?: throw IllegalArgumentException("That file is not a Vault export.")
        val format = json.optInt("vault", 0)
        if (format < 1) throw IllegalArgumentException("That file is not a Vault export.")
        if (format > FORMAT) throw IllegalArgumentException("That export came from a newer Vault.")

        val array = json.optJSONArray("repositories") ?: JSONArray()
        val repositories = (0 until array.length()).mapNotNull { index ->
            trackedRepoFrom(array.getJSONObject(index))
        }
        val preferences = json.optJSONObject("preferences")?.let {
            Preferences(
                includePrereleases = it.optBoolean("includePrereleases", false),
                wifiOnly = it.optBoolean("wifiOnly", true),
                notifyOnRelease = it.optBoolean("notifyOnRelease", true),
                checkIntervalHours = it.optInt("checkIntervalHours", 12),
                onboarded = true,
            )
        }
        return TransferBundle(
            repositories = repositories,
            preferences = preferences,
            token = json.text("token").takeIf { it.isNotBlank() },
        )
    }

    /** Writes the export into the cache and returns a share intent for it. */
    suspend fun share(context: Context, contents: String): Intent = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "exports").apply {
            mkdirs()
            // Only the newest export is ever useful, and stale ones may hold a token.
            listFiles()?.forEach { it.delete() }
        }
        val stamp = OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm", Locale.US))
        val file = File(directory, "vault-library-$stamp.json")
        file.writeText(contents)
        val uri = FileProvider.getUriForFile(
            context,
            context.packageName + AUTHORITY_SUFFIX,
            file,
        )
        Intent(Intent.ACTION_SEND)
            .setType("application/json")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, file.name)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .let { Intent.createChooser(it, "Send library to another device") }
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    suspend fun read(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            stream.bufferedReader().readText()
        } ?: throw IllegalArgumentException("That file could not be opened.")
    }

    /** Deletes any export still sitting in the cache, token and all. */
    fun clearExports(context: Context) {
        File(context.cacheDir, "exports").listFiles()?.forEach { it.delete() }
    }
}
