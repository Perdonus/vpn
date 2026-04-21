package com.white.vpn.integration

import com.white.vpn.domain.ProxyProtocol
import com.white.vpn.domain.VpnServer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object XrayConfigBuilder {
    fun build(
        server: VpnServer,
        includeTun: Boolean,
    ): String {
        val root =
            buildJsonObject {
                put("stats", buildJsonObject {})
                put(
                    "log",
                    buildJsonObject {
                        put("loglevel", JsonPrimitive("warning"))
                    },
                )
                put(
                    "policy",
                    buildJsonObject {
                        put(
                            "levels",
                            buildJsonObject {
                                put(
                                    "8",
                                    buildJsonObject {
                                        put("handshake", JsonPrimitive(4))
                                        put("connIdle", JsonPrimitive(300))
                                        put("uplinkOnly", JsonPrimitive(1))
                                        put("downlinkOnly", JsonPrimitive(1))
                                    },
                                )
                            },
                        )
                        put(
                            "system",
                            buildJsonObject {
                                put("statsOutboundUplink", JsonPrimitive(true))
                                put("statsOutboundDownlink", JsonPrimitive(true))
                            },
                        )
                    },
                )
                put("inbounds", buildInbounds(includeTun))
                put(
                    "outbounds",
                    buildJsonArray {
                        add(buildProxyOutbound(server))
                        add(buildDirectOutbound())
                        add(buildBlockOutbound())
                    },
                )
                put(
                    "routing",
                    buildJsonObject {
                        put("domainStrategy", JsonPrimitive("AsIs"))
                        put("rules", buildJsonArray {})
                    },
                )
                put(
                    "dns",
                    buildJsonObject {
                        put(
                            "servers",
                            buildJsonArray {
                                add(JsonPrimitive("1.1.1.1"))
                                add(JsonPrimitive("1.0.0.1"))
                            },
                        )
                    },
                )
            }
        return root.toString()
    }

    private fun buildInbounds(includeTun: Boolean): JsonArray =
        buildJsonArray {
            if (includeTun) {
                add(
                    buildJsonObject {
                        put("tag", JsonPrimitive("tun"))
                        put("port", JsonPrimitive(0))
                        put("protocol", JsonPrimitive("tun"))
                        put(
                            "settings",
                            buildJsonObject {
                                put("name", JsonPrimitive("xray0"))
                                put("mtu", JsonPrimitive(1500))
                                put("userLevel", JsonPrimitive(8))
                            },
                        )
                        putSniffing()
                    },
                )
            }
        }

    private fun buildProxyOutbound(server: VpnServer): JsonObject =
        buildJsonObject {
            put("tag", JsonPrimitive("proxy"))
            put("protocol", JsonPrimitive(server.protocol.scheme))
            put("settings", buildOutboundSettings(server))
            put("streamSettings", buildStreamSettings(server))
            put(
                "mux",
                buildJsonObject {
                    put("enabled", JsonPrimitive(false))
                },
            )
        }

    private fun buildOutboundSettings(server: VpnServer): JsonObject =
        when (server.protocol) {
            ProxyProtocol.VLESS ->
                buildJsonObject {
                    put(
                        "vnext",
                        buildJsonArray {
                            add(
                                buildJsonObject {
                                    put("address", JsonPrimitive(server.host))
                                    put("port", JsonPrimitive(server.port))
                                    put(
                                        "users",
                                        buildJsonArray {
                                            add(
                                                buildJsonObject {
                                                    put("id", JsonPrimitive(server.user.orEmpty()))
                                                    put("encryption", JsonPrimitive(server.method ?: "none"))
                                                    putIfNotBlank("flow", server.flow)
                                                    put("level", JsonPrimitive(8))
                                                },
                                            )
                                        },
                                    )
                                },
                            )
                        },
                    )
                }

            ProxyProtocol.VMESS ->
                buildJsonObject {
                    put(
                        "vnext",
                        buildJsonArray {
                            add(
                                buildJsonObject {
                                    put("address", JsonPrimitive(server.host))
                                    put("port", JsonPrimitive(server.port))
                                    put(
                                        "users",
                                        buildJsonArray {
                                            add(
                                                buildJsonObject {
                                                    put("id", JsonPrimitive(server.user.orEmpty()))
                                                    put("alterId", JsonPrimitive(0))
                                                    put("security", JsonPrimitive(server.method ?: "auto"))
                                                    put("level", JsonPrimitive(8))
                                                },
                                            )
                                        },
                                    )
                                },
                            )
                        },
                    )
                }

            ProxyProtocol.TROJAN ->
                buildJsonObject {
                    put(
                        "servers",
                        buildJsonArray {
                            add(
                                buildJsonObject {
                                    put("address", JsonPrimitive(server.host))
                                    put("port", JsonPrimitive(server.port))
                                    put("password", JsonPrimitive(server.password.orEmpty()))
                                    putIfNotBlank("flow", server.flow)
                                    put("level", JsonPrimitive(8))
                                },
                            )
                        },
                    )
                }

            ProxyProtocol.SHADOWSOCKS ->
                buildJsonObject {
                    put(
                        "servers",
                        buildJsonArray {
                            add(
                                buildJsonObject {
                                    put("address", JsonPrimitive(server.host))
                                    put("port", JsonPrimitive(server.port))
                                    put("method", JsonPrimitive(server.method.orEmpty()))
                                    put("password", JsonPrimitive(server.password.orEmpty()))
                                    put("level", JsonPrimitive(8))
                                },
                            )
                        },
                    )
                }

            ProxyProtocol.SOCKS ->
                buildJsonObject {
                    put(
                        "servers",
                        buildJsonArray {
                            add(
                                buildJsonObject {
                                    put("address", JsonPrimitive(server.host))
                                    put("port", JsonPrimitive(server.port))
                                    if (!server.user.isNullOrBlank()) {
                                        put(
                                            "users",
                                            buildJsonArray {
                                                add(
                                                    buildJsonObject {
                                                        put("user", JsonPrimitive(server.user))
                                                        putIfNotBlank("pass", server.password)
                                                        put("level", JsonPrimitive(8))
                                                    },
                                                )
                                            },
                                        )
                                    }
                                },
                            )
                        },
                    )
                }

            ProxyProtocol.AUTO -> error("Auto profile cannot be converted to runtime config")
        }

    private fun buildStreamSettings(server: VpnServer): JsonObject {
        val network = normalizeNetwork(server.network)
        return buildJsonObject {
            put("network", JsonPrimitive(network))
            put("security", JsonPrimitive(normalizeSecurity(server.security)))

            when (network) {
                "ws" -> {
                    put(
                        "wsSettings",
                        buildJsonObject {
                            put("path", JsonPrimitive(server.path ?: "/"))
                            if (!server.hostHeader.isNullOrBlank()) {
                                put(
                                    "headers",
                                    buildJsonObject {
                                        put("Host", JsonPrimitive(server.hostHeader))
                                    },
                                )
                            }
                        },
                    )
                }

                "grpc" -> {
                    put(
                        "grpcSettings",
                        buildJsonObject {
                            put("serviceName", JsonPrimitive(server.serviceName ?: server.path ?: "grpc"))
                            putIfNotBlank("authority", server.authority)
                            if (server.mode == "multi") {
                                put("multiMode", JsonPrimitive(true))
                            }
                        },
                    )
                }

                "httpupgrade" -> {
                    put(
                        "httpupgradeSettings",
                        buildJsonObject {
                            put("path", JsonPrimitive(server.path ?: "/"))
                            putIfNotBlank("host", server.hostHeader)
                        },
                    )
                }

                "xhttp" -> {
                    put(
                        "xhttpSettings",
                        buildJsonObject {
                            put("path", JsonPrimitive(server.path ?: "/"))
                            putIfNotBlank("host", server.hostHeader)
                            putIfNotBlank("mode", server.mode)
                        },
                    )
                }

                "http" -> {
                    put(
                        "httpSettings",
                        buildJsonObject {
                            put(
                                "path",
                                buildJsonArray {
                                    add(JsonPrimitive(server.path ?: "/"))
                                },
                            )
                            if (!server.hostHeader.isNullOrBlank()) {
                                put("host", csvToJsonArray(server.hostHeader))
                            }
                        },
                    )
                }

                else -> {
                    if (server.headerType == "http") {
                        put(
                            "tcpSettings",
                            buildJsonObject {
                                put(
                                    "header",
                                    buildJsonObject {
                                        put("type", JsonPrimitive("http"))
                                        put(
                                            "request",
                                            buildJsonObject {
                                                put(
                                                    "path",
                                                    buildJsonArray {
                                                        add(JsonPrimitive(server.path ?: "/"))
                                                    },
                                                )
                                                if (!server.hostHeader.isNullOrBlank()) {
                                                    put(
                                                        "headers",
                                                        buildJsonObject {
                                                            put("Host", csvToJsonArray(server.hostHeader))
                                                        },
                                                    )
                                                }
                                            },
                                        )
                                    },
                                )
                            },
                        )
                    }
                }
            }

            when (normalizeSecurity(server.security)) {
                "tls" -> {
                    put(
                        "tlsSettings",
                        buildJsonObject {
                            putIfNotBlank("serverName", server.sni ?: server.hostHeader ?: server.host)
                            if (server.allowInsecure) {
                                put("allowInsecure", JsonPrimitive(true))
                            }
                            if (!server.fingerprint.isNullOrBlank()) {
                                put("fingerprint", JsonPrimitive(server.fingerprint))
                            }
                            if (!server.alpn.isNullOrBlank()) {
                                put("alpn", csvToJsonArray(server.alpn))
                            }
                        },
                    )
                }

                "reality" -> {
                    put(
                        "realitySettings",
                        buildJsonObject {
                            put("show", JsonPrimitive(false))
                            putIfNotBlank("serverName", server.sni ?: server.hostHeader ?: server.host)
                            put("fingerprint", JsonPrimitive(server.fingerprint ?: "chrome"))
                            putIfNotBlank("publicKey", server.publicKey)
                            putIfNotBlank("shortId", server.shortId)
                            putIfNotBlank("spiderX", server.spiderX ?: "/")
                        },
                    )
                }
            }
        }
    }

    private fun buildDirectOutbound(): JsonObject =
        buildJsonObject {
            put("protocol", JsonPrimitive("freedom"))
            put("tag", JsonPrimitive("direct"))
            put(
                "settings",
                buildJsonObject {
                    put("domainStrategy", JsonPrimitive("UseIP"))
                },
            )
        }

    private fun buildBlockOutbound(): JsonObject =
        buildJsonObject {
            put("protocol", JsonPrimitive("blackhole"))
            put("tag", JsonPrimitive("block"))
            put(
                "settings",
                buildJsonObject {
                    put(
                        "response",
                        buildJsonObject {
                            put("type", JsonPrimitive("http"))
                        },
                    )
                },
            )
        }

    private fun normalizeNetwork(network: String?): String =
        when (network?.trim()?.lowercase()) {
            null, "", "raw" -> "tcp"
            "h2" -> "http"
            else -> network.lowercase()
        }

    private fun normalizeSecurity(security: String?): String =
        when (security?.trim()?.lowercase()) {
            "tls", "reality" -> security.lowercase()
            else -> "none"
        }

    private fun csvToJsonArray(raw: String?): JsonArray =
        buildJsonArray {
            raw.orEmpty()
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .forEach { add(JsonPrimitive(it)) }
        }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putIfNotBlank(
        key: String,
        value: String?,
    ) {
        if (!value.isNullOrBlank()) {
            put(key, JsonPrimitive(value))
        }
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putSniffing() {
        put(
            "sniffing",
            buildJsonObject {
                put("enabled", JsonPrimitive(true))
                put(
                    "destOverride",
                    buildJsonArray {
                        add(JsonPrimitive("http"))
                        add(JsonPrimitive("tls"))
                    },
                )
            },
        )
    }
}
