package com.white.vpn.data

private const val MOBILE_SUBSCRIPTION_URL =
    "https://raw.githubusercontent.com/igareck/vpn-configs-for-russia/refs/heads/main/Vless-Reality-White-Lists-Rus-Mobile.txt"

private const val MOBILE_2_SUBSCRIPTION_URL =
    "https://raw.githubusercontent.com/igareck/vpn-configs-for-russia/refs/heads/main/Vless-Reality-White-Lists-Rus-Mobile-2.txt"

object AppDefaults {
    val DEFAULT_SUBSCRIPTION_URLS: List<String> = listOf(MOBILE_SUBSCRIPTION_URL, MOBILE_2_SUBSCRIPTION_URL)

    const val LEGACY_DEFAULT_SUBSCRIPTION_URL =
        "https://raw.githubusercontent.com/igareck/vpn-configs-for-russia/refs/heads/main/WHITE-CIDR-RU-all.txt"

    val DEFAULT_SUBSCRIPTION_URL: String = DEFAULT_SUBSCRIPTION_URLS.first()

    fun isBundledSubscriptionUrl(url: String?): Boolean {
        val normalizedUrl = url?.trim().orEmpty()
        return normalizedUrl == LEGACY_DEFAULT_SUBSCRIPTION_URL || normalizedUrl in DEFAULT_SUBSCRIPTION_URLS
    }
}
