package com.white.vpn.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.white.vpn.domain.InstalledAppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class InstalledAppsRepository(
    private val context: Context,
) {
    suspend fun getInstalledApps(): List<InstalledAppInfo> =
        withContext(Dispatchers.Default) {
            val packageManager = context.packageManager
            val installedApplications =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    packageManager.getInstalledApplications(
                        PackageManager.ApplicationInfoFlags.of(0),
                    )
                } else {
                    @Suppress("DEPRECATION")
                    packageManager.getInstalledApplications(0)
                }

            installedApplications
                .asSequence()
                .filter { applicationInfo ->
                    shouldIncludeApp(applicationInfo)
                }
                .map { applicationInfo ->
                    val packageName = applicationInfo.packageName.trim()
                    val label =
                        runCatching {
                            packageManager.getApplicationLabel(applicationInfo)
                                ?.toString()
                                ?.trim()
                                ?.ifBlank { packageName }
                        }.getOrNull() ?: packageName
                    InstalledAppInfo(
                        packageName = packageName,
                        label = label,
                    )
                }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
                .toList()
        }

    private fun shouldIncludeApp(applicationInfo: ApplicationInfo): Boolean {
        val packageName = applicationInfo.packageName.trim()
        if (packageName.isEmpty() || packageName == context.packageName) {
            return false
        }
        if (!applicationInfo.enabled) {
            return false
        }

        val isSystemApp = (applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        val isUpdatedSystemApp = (applicationInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        val hasLauncherEntry = context.packageManager.getLaunchIntentForPackage(packageName) != null

        return when {
            hasLauncherEntry -> true
            !isSystemApp -> true
            isUpdatedSystemApp -> true
            else -> false
        }
    }
}
