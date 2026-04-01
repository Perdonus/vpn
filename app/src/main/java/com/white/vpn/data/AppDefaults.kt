package com.white.vpn.data

enum class SubscriptionMode(
    val id: String,
    val displayName: String,
    val subscriptionUrl: String,
) {
    MOBILE(
        id = "mobile",
        displayName = "Mobile",
        subscriptionUrl = "https://raw.githubusercontent.com/igareck/vpn-configs-for-russia/refs/heads/main/Vless-Reality-White-Lists-Rus-Mobile.txt",
    ),
    MOBILE_2(
        id = "mobile_2",
        displayName = "Mobile-2",
        subscriptionUrl = "https://raw.githubusercontent.com/igareck/vpn-configs-for-russia/refs/heads/main/Vless-Reality-White-Lists-Rus-Mobile-2.txt",
    ),
    ;

    companion object {
        fun fromId(id: String?): SubscriptionMode =
            entries.firstOrNull { it.id == id } ?: MOBILE

        fun fromUrl(url: String?): SubscriptionMode? =
            entries.firstOrNull { it.subscriptionUrl == url }
    }
}

object AppDefaults {
    val DEFAULT_SUBSCRIPTION_MODE: SubscriptionMode = SubscriptionMode.MOBILE

    const val LEGACY_DEFAULT_SUBSCRIPTION_URL =
        "https://raw.githubusercontent.com/igareck/vpn-configs-for-russia/refs/heads/main/WHITE-CIDR-RU-all.txt"

    val DEFAULT_SUBSCRIPTION_URL: String = DEFAULT_SUBSCRIPTION_MODE.subscriptionUrl
}
