package com.white.vpn.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.white.vpn.domain.InstalledAppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class InstalledAppsRepository(
    private val context: Context,
) {
    suspend fun getLaunchableApps(): List<InstalledAppInfo> =
        withContext(Dispatchers.Default) {
            val packageManager = context.packageManager
            val launcherIntent =
                Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
            val resolveInfos =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    packageManager.queryIntentActivities(
                        launcherIntent,
                        PackageManager.ResolveInfoFlags.of(0),
                    )
                } else {
                    @Suppress("DEPRECATION")
                    packageManager.queryIntentActivities(launcherIntent, 0)
                }

            resolveInfos
                .asSequence()
                .mapNotNull { resolveInfo ->
                    val packageName = resolveInfo.activityInfo?.packageName?.trim().orEmpty()
                    if (packageName.isEmpty() || packageName == context.packageName) {
                        return@mapNotNull null
                    }
                    val label =
                        resolveInfo.loadLabel(packageManager)
                            ?.toString()
                            ?.trim()
                            ?.ifBlank { packageName }
                            ?: packageName
                    InstalledAppInfo(
                        packageName = packageName,
                        label = label,
                    )
                }
                .distinctBy { it.packageName }
                .toList()
        }
}
