package com.white.vpn.data

import com.white.vpn.domain.VpnServer
import java.util.Base64
import java.util.LinkedHashMap

object SubscriptionImporter {
    fun import(rawBody: String): List<VpnServer> {
        val candidates = extractCandidateLines(rawBody)
        return candidates.mapNotNull(ShareLinkParser::parse).dedupeServers()
    }

    private fun extractCandidateLines(rawBody: String): List<String> {
        val directLines = normalize(rawBody)
        if (directLines.isNotEmpty()) return directLines

        val trimmed = rawBody.trim()
        if (trimmed.isEmpty()) return emptyList()

        val decodedBody = decodeBase64Payload(trimmed) ?: return emptyList()
        return normalize(decodedBody)
    }

    private fun normalize(body: String): List<String> =
        body.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .filterNot { it.startsWith("#") }
            .filter { it.contains("://") }
            .toList()

    private fun decodeBase64Payload(payload: String): String? {
        val sanitized = payload
            .lineSequence()
            .map(String::trim)
            .joinToString(separator = "")
            .replace(" ", "")
        if (sanitized.isEmpty()) return null

        val decoders = listOf(
            { value: String -> Base64.getMimeDecoder().decode(value) },
            { value: String -> Base64.getDecoder().decode(value) },
            { value: String -> Base64.getUrlDecoder().decode(value.padForBase64()) },
        )
        return decoders.firstNotNullOfOrNull { decoder ->
            runCatching { decoder(sanitized).decodeToString() }.getOrNull()
        }
    }

    private fun String.padForBase64(): String {
        val remainder = length % 4
        return if (remainder == 0) this else this + "=".repeat(4 - remainder)
    }

    private fun List<VpnServer>.dedupeServers(): List<VpnServer> {
        if (isEmpty()) return this
        val unique = LinkedHashMap<String, VpnServer>(size)
        for (server in this) {
            unique.putIfAbsent(server.dedupeKey(), server)
        }
        return unique.values.toList()
    }
}

internal fun VpnServer.dedupeKey(): String =
    listOf(
        protocol.name,
        host.lowercase(),
        port.toString(),
        user.orEmpty().trim(),
        password.orEmpty().trim(),
        method.orEmpty().trim().lowercase(),
        network.orEmpty().trim().lowercase(),
        security.orEmpty().trim().lowercase(),
        flow.orEmpty().trim(),
        headerType.orEmpty().trim().lowercase(),
        hostHeader.orEmpty().trim().lowercase(),
        path.orEmpty().trim(),
        serviceName.orEmpty().trim(),
        mode.orEmpty().trim().lowercase(),
        authority.orEmpty().trim().lowercase(),
        sni.orEmpty().trim().lowercase(),
        fingerprint.orEmpty().trim().lowercase(),
        alpn.orEmpty().trim().lowercase(),
        publicKey.orEmpty().trim(),
        shortId.orEmpty().trim(),
        spiderX.orEmpty().trim(),
        allowInsecure.toString(),
    ).joinToString(separator = "|")
