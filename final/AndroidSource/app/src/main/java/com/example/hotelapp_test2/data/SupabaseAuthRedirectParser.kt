package com.example.hotelapp_test2.data

import java.net.URLDecoder

data class SupabaseRedirectSession(
    val accessToken: String,
    val refreshToken: String,
    val type: String
)

object SupabaseAuthRedirectParser {
    fun parse(rawUrl: String): SupabaseRedirectSession? {
        val trimmed = rawUrl.trim()
        if (trimmed.isBlank()) return null

        val hashIndex = trimmed.indexOf('#')
        val fragment = if (hashIndex >= 0 && hashIndex < trimmed.lastIndex) trimmed.substring(hashIndex + 1) else ""
        val queryIndex = trimmed.indexOf('?')
        val query = if (queryIndex >= 0 && queryIndex < trimmed.lastIndex) trimmed.substring(queryIndex + 1, hashIndex.takeIf { it > queryIndex } ?: trimmed.length) else ""

        val values = linkedMapOf<String, String>()
        parsePart(query, values)
        parsePart(fragment, values)

        val accessToken = values["access_token"].orEmpty()
        val refreshToken = values["refresh_token"].orEmpty()
        if (accessToken.isBlank() || refreshToken.isBlank()) return null

        return SupabaseRedirectSession(
            accessToken = accessToken,
            refreshToken = refreshToken,
            type = values["type"].orEmpty()
        )
    }

    private fun parsePart(source: String, target: MutableMap<String, String>) {
        if (source.isBlank()) return
        source.split("&")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .forEach { part ->
                val pieces = part.split("=", limit = 2)
                val key = decode(pieces[0])
                if (key.isBlank()) return@forEach
                val value = decode(pieces.getOrElse(1) { "" })
                target[key] = value
            }
    }

    private fun decode(value: String): String = URLDecoder.decode(value, Charsets.UTF_8.name())
}
