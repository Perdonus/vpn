package com.white.vpn.data

private const val MOBILE_SUBSCRIPTION_URL =
    "https://raw.githubusercontent.com/igareck/vpn-configs-for-russia/refs/heads/main/Vless-Reality-White-Lists-Rus-Mobile.txt"

private const val MOBILE_2_SUBSCRIPTION_URL =
    "https://raw.githubusercontent.com/igareck/vpn-configs-for-russia/refs/heads/main/Vless-Reality-White-Lists-Rus-Mobile-2.txt"

enum class SubscriptionMode(
    val id: String,
    val displayName: String,
    val subscriptionUrl: String,
) {
    AUTO(
        id = "auto",
        displayName = "Auto",
        subscriptionUrl = MOBILE_SUBSCRIPTION_URL,
    ),
    MOBILE(
        id = "mobile",
        displayName = "Mobile",
        subscriptionUrl = MOBILE_SUBSCRIPTION_URL,
    ),
    MOBILE_2(
        id = "mobile_2",
        displayName = "Mobile-2",
        subscriptionUrl = MOBILE_2_SUBSCRIPTION_URL,
    ),
    ;

    val subscriptionUrls: List<String>
        get() =
            when (this) {
                AUTO -> listOf(MOBILE_SUBSCRIPTION_URL, MOBILE_2_SUBSCRIPTION_URL)
                MOBILE -> listOf(MOBILE_SUBSCRIPTION_URL)
                MOBILE_2 -> listOf(MOBILE_2_SUBSCRIPTION_URL)
            }

    companion object {
        fun fromId(id: String?): SubscriptionMode =
            entries.firstOrNull { it.id == id } ?: AUTO

        fun fromUrl(url: String?): SubscriptionMode? =
            when (url?.trim()) {
                MOBILE_SUBSCRIPTION_URL -> MOBILE
                MOBILE_2_SUBSCRIPTION_URL -> MOBILE_2
                AppDefaults.LEGACY_DEFAULT_SUBSCRIPTION_URL -> MOBILE
                else -> entries.firstOrNull { it.subscriptionUrl == url?.trim() }
            }
    }
}

object AppDefaults {
    val DEFAULT_SUBSCRIPTION_MODE: SubscriptionMode = SubscriptionMode.AUTO

    const val LEGACY_DEFAULT_SUBSCRIPTION_URL =
        "https://raw.githubusercontent.com/igareck/vpn-configs-for-russia/refs/heads/main/WHITE-CIDR-RU-all.txt"

    val DEFAULT_SUBSCRIPTION_URL: String = DEFAULT_SUBSCRIPTION_MODE.subscriptionUrl
}
