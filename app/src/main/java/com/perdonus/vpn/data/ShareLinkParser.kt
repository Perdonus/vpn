package com.perdonus.vpn.data

import com.perdonus.vpn.domain.ProxyProtocol
import com.perdonus.vpn.domain.VpnServer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64

object ShareLinkParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(rawLink: String): VpnServer? {
        val link = rawLink.trim()
        val scheme = link.substringBefore("://", "").lowercase()
        return when (ProxyProtocol.fromScheme(scheme)) {
            ProxyProtocol.VLESS -> parseVless(link)
            ProxyProtocol.VMESS -> parseVmess(link)
            ProxyProtocol.TROJAN -> parseTrojan(link)
            ProxyProtocol.SHADOWSOCKS -> parseShadowsocks(link)
            ProxyProtocol.SOCKS -> parseSocks(link)
            else -> null
        }
    }

    private fun parseVless(link: String): VpnServer? {
        val uri = URI(fixIllegalUrl(link))
        val query = parseQuery(uri.rawQuery)
        return VpnServer(
            id = buildServerId(link),
            protocol = ProxyProtocol.VLESS,
            displayName = extractDisplayName(uri.fragment, uri.host),
            host = uri.host ?: return null,
            port = uri.port.takeIf { it > 0 } ?: return null,
            rawLink = link,
            user = uri.userInfo,
            method = query["encryption"] ?: "none",
            network = query["type"],
            security = query["security"],
            flow = query["flow"],
            headerType = query["headerType"],
            hostHeader = query["host"],
            path = query["path"],
            serviceName = query["serviceName"],
            mode = query["mode"],
            authority = query["authority"],
            sni = query["sni"],
            fingerprint = query["fp"],
            alpn = query["alpn"],
            publicKey = query["pbk"],
            shortId = query["sid"],
            spiderX = query["spx"],
            allowInsecure = parseBooleanFlag(query["allowInsecure"], query["insecure"], query["allow_insecure"]),
        )
    }

    private fun parseTrojan(link: String): VpnServer? {
        val uri = URI(fixIllegalUrl(link))
        val query = parseQuery(uri.rawQuery)
        return VpnServer(
            id = buildServerId(link),
            protocol = ProxyProtocol.TROJAN,
            displayName = extractDisplayName(uri.fragment, uri.host),
            host = uri.host ?: return null,
            port = uri.port.takeIf { it > 0 } ?: return null,
            rawLink = link,
            password = uri.userInfo,
            network = query["type"] ?: "tcp",
            security = query["security"] ?: "tls",
            flow = query["flow"],
            headerType = query["headerType"],
            hostHeader = query["host"],
            path = query["path"],
            serviceName = query["serviceName"],
            mode = query["mode"],
            authority = query["authority"],
            sni = query["sni"],
            fingerprint = query["fp"],
            alpn = query["alpn"],
            publicKey = query["pbk"],
            shortId = query["sid"],
            spiderX = query["spx"],
            allowInsecure = parseBooleanFlag(query["allowInsecure"], query["insecure"], query["allow_insecure"]),
        )
    }

    private fun parseVmess(link: String): VpnServer? {
        val payload = link.substringAfter("vmess://", "")
        val decoded = decodeBase64(payload) ?: return null
        val vmess = json.decodeFromString<VmessPayload>(decoded)
        val host = vmess.add?.takeIf { it.isNotBlank() } ?: return null
        val port = vmess.port?.toIntOrNull() ?: return null
        return VpnServer(
            id = buildServerId(link),
            protocol = ProxyProtocol.VMESS,
            displayName = vmess.ps?.takeIf { it.isNotBlank() } ?: host,
            host = host,
            port = port,
            rawLink = link,
            user = vmess.id,
            method = vmess.scy ?: "auto",
            network = vmess.net,
            security = vmess.tls,
            flow = vmess.flow,
            headerType = vmess.type,
            hostHeader = vmess.host,
            path = vmess.path,
            serviceName = vmess.path?.takeIf { vmess.net == "grpc" },
            sni = vmess.sni,
            fingerprint = vmess.fp,
            alpn = vmess.alpn,
            publicKey = vmess.pbk,
            shortId = vmess.sid,
            allowInsecure = vmess.allowInsecure == "1",
        )
    }

    private fun parseShadowsocks(link: String): VpnServer? {
        val uri = URI(fixIllegalUrl(link))
        val host = uri.host ?: return parseLegacyShadowsocks(link)
        val port = uri.port.takeIf { it > 0 } ?: return null
        val userInfo = uri.userInfo ?: return null
        val methodPassword = if (userInfo.contains(":")) {
            userInfo
        } else {
            decodeBase64(userInfo) ?: return null
        }
        val parts = methodPassword.split(":", limit = 2)
        if (parts.size != 2) return null
        return VpnServer(
            id = buildServerId(link),
            protocol = ProxyProtocol.SHADOWSOCKS,
            displayName = extractDisplayName(uri.fragment, host),
            host = host,
            port = port,
            rawLink = link,
            method = parts[0],
            password = parts[1],
        )
    }

    private fun parseLegacyShadowsocks(link: String): VpnServer? {
        val payload = link.removePrefix("ss://")
        val encodedPart = payload.substringBefore("#")
        val decoded = decodeBase64(encodedPart.substringBefore("@")) ?: return null
        val addressPart = if (encodedPart.contains("@")) {
            decoded + "@" + encodedPart.substringAfter("@")
        } else {
            decoded
        }
        val legacy = Regex("^(.+?):(.*)@(.+?):(\\d+?)/?$").matchEntire(addressPart) ?: return null
        val host = legacy.groupValues[3].removeSurrounding("[", "]")
        val name = link.substringAfter("#", "").takeIf { it.isNotBlank() }
        return VpnServer(
            id = buildServerId(link),
            protocol = ProxyProtocol.SHADOWSOCKS,
            displayName = decodeComponent(name).ifBlank { host },
            host = host,
            port = legacy.groupValues[4].toInt(),
            rawLink = link,
            method = legacy.groupValues[1],
            password = legacy.groupValues[2],
        )
    }

    private fun parseSocks(link: String): VpnServer? {
        val uri = URI(fixIllegalUrl(link))
        val query = parseQuery(uri.rawQuery)
        val host = uri.host ?: return null
        val port = uri.port.takeIf { it > 0 } ?: return null
        val credentials = uri.userInfo?.let(::decodeMaybeBase64Credentials)
        return VpnServer(
            id = buildServerId(link),
            protocol = ProxyProtocol.SOCKS,
            displayName = extractDisplayName(uri.fragment, host),
            host = host,
            port = port,
            rawLink = link,
            user = credentials?.first,
            password = credentials?.second,
            security = query["security"],
        )
    }

    private fun decodeMaybeBase64Credentials(value: String): Pair<String?, String?> {
        val decoded = if (value.contains(":")) value else decodeBase64(value) ?: value
        val parts = decoded.split(":", limit = 2)
        return when (parts.size) {
            2 -> parts[0] to parts[1]
            1 -> parts[0] to null
            else -> null to null
        }
    }

    private fun parseQuery(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrBlank()) return emptyMap()
        return rawQuery
            .split("&")
            .mapNotNull { pair ->
                val delimiter = pair.indexOf("=")
                if (delimiter <= 0) return@mapNotNull null
                val key = pair.substring(0, delimiter)
                val value = pair.substring(delimiter + 1)
                key to decodeComponent(value)
            }
            .toMap()
    }

    private fun parseBooleanFlag(vararg values: String?): Boolean =
        values.firstNotNullOfOrNull { value ->
            when (value?.trim()?.lowercase()) {
                "1", "true", "yes" -> true
                "0", "false", "no" -> false
                else -> null
            }
        } ?: false

    private fun extractDisplayName(fragment: String?, fallbackHost: String?): String =
        decodeComponent(fragment).ifBlank { fallbackHost.orEmpty() }

    private fun decodeBase64(value: String): String? {
        val sanitized = value.trim().replace("\n", "").replace(" ", "")
        val candidates = listOf(
            sanitized,
            sanitized.padForBase64(),
        )
        return candidates.firstNotNullOfOrNull { candidate ->
            val bytes = listOf(
                runCatching { Base64.getDecoder().decode(candidate) }.getOrNull(),
                runCatching { Base64.getUrlDecoder().decode(candidate) }.getOrNull(),
                runCatching { Base64.getMimeDecoder().decode(candidate) }.getOrNull(),
            ).firstOrNull { it != null }
            bytes?.decodeToString()
        }
    }

    private fun String.padForBase64(): String {
        val remainder = length % 4
        return if (remainder == 0) this else this + "=".repeat(4 - remainder)
    }

    private fun decodeComponent(value: String?): String {
        if (value.isNullOrBlank()) return ""
        return runCatching {
            URLDecoder.decode(value, StandardCharsets.UTF_8.name())
        }.getOrDefault(value)
    }

    private fun fixIllegalUrl(value: String): String {
        return buildString(value.length) {
            value.forEach { char ->
                when (char) {
                    ' ' -> append("%20")
                    else -> append(char)
                }
            }
        }
    }

    private fun buildServerId(rawLink: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(rawLink.toByteArray())
        return buildString(16) {
            digest.take(8).forEach { byte ->
                append("%02x".format(byte))
            }
        }
    }

    @Serializable
    private data class VmessPayload(
        val ps: String? = null,
        val add: String? = null,
        val port: String? = null,
        val id: String? = null,
        val aid: String? = null,
        val scy: String? = null,
        val net: String? = null,
        val type: String? = null,
        val host: String? = null,
        val path: String? = null,
        val tls: String? = null,
        val sni: String? = null,
        val fp: String? = null,
        val alpn: String? = null,
        val pbk: String? = null,
        val sid: String? = null,
        val flow: String? = null,
        val allowInsecure: String? = null,
    )
}
