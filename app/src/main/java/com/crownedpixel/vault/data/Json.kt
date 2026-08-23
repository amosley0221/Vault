package com.crownedpixel.vault.data

import org.json.JSONObject

/**
 * org.json hands back the four-character string "null" for a JSON null, which is how a repository
 * with no description ends up displaying the word "null". This returns an empty string instead.
 */
fun JSONObject.text(key: String): String =
    if (isNull(key)) "" else optString(key).let { if (it == "null") "" else it }
