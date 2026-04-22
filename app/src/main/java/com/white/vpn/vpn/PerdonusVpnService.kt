package com.white.vpn.vpn

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.IBinder
import android.os.ParcelFileDescriptor
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.white.vpn.domain.SplitTunnelMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import libv2ray.CoreCallbackHandler
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

class PerdonusVpnService : VpnService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val operationMutex = Mutex()

    private lateinit var core: VpnCore
    private var tunInterface: ParcelFileDescriptor? = null
    private var activeProfile: TunnelProfile? = null
    private var activePingMs: Long? = null
    private var startedAtEpochMs: Long? = null
    private var isAutoMode: Boolean = true
    private var sessionAccumulatedRxBytes: Long = 0L
    private var sessionAccumulatedTxBytes: Long = 0L
    private var consecutiveProbeFailures: Int = 0
    private var lastCurrentValidationAtMs: Long = 0L

    private var autoSelectionJob: Job? = null
    private var pingRefreshJob: Job? = null
    private var notificationJob: Job? = null
    private val validationMutex = Mutex()
    private val validationCache = ConcurrentHashMap<String, ValidationCacheEntry>()

    override fun onCreate() {
        super.onCreate()
        core = LibV2rayCore(applicationContext, callbackHandler)
    }

    override fun onBind(intent: Intent): IBinder? = super.onBind(intent)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> serviceScope.launch { stopTunnel() }
            ACTION_TOGGLE -> serviceScope.launch { toggleTunnel(intent.getStringExtra(EXTRA_PROFILE_ID)) }
            else -> serviceScope.launch { startTunnel(intent?.getStringExtra(EXTRA_PROFILE_ID)) }
        }
        return Service.START_STICKY
    }

    override fun onDestroy() {
        autoSelectionJob?.cancel()
        pingRefreshJob?.cancel()
        notificationJob?.cancel()
        stopCoreOnly()
        resetSessionState()
        publishState(TunnelStatus.IDLE, message = "Service destroyed")
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onRevoke() {
        serviceScope.launch { stopTunnel(message = "VPN permission revoked") }
        super.onRevoke()
    }

    private suspend fun toggleTunnel(requestedProfileId: String?) {
        if (core.isRunning) {
            stopTunnel()
        } else {
            startTunnel(requestedProfileId)
        }
    }

    private suspend fun startTunnel(requestedProfileId: String?) {
        operationMutex.withLock {
            val dependencies = VpnServiceLocator.dependencies
            if (dependencies == null) {
                publishState(
                    TunnelStatus.ERROR,
                    message = "VPN dependencies are not registered",
                )
                stopSelf()
                return@withLock
            }

            val permissionIntent = VpnService.prepare(this)
            if (permissionIntent != null) {
                publishState(
                    TunnelStatus.PERMISSION_REQUIRED,
                    message = getString(com.white.vpn.R.string.status_permission_required),
                    permissionIntent = permissionIntent,
                )
                stopSelf()
                return@withLock
            }

            val selection = dependencies.profileStore.getSelection()
            isAutoMode = requestedProfileId == null && selection.mode == TunnelSelectionMode.AUTO
            publishState(
                TunnelStatus.CONNECTING,
                message = if (isAutoMode) getString(com.white.vpn.R.string.status_selecting_server) else getString(com.white.vpn.R.string.status_connecting),
            )
            VpnNotificationFactory.ensureChannel(this)
            ServiceCompat.startForeground(
                this,
                VpnNotificationFactory.NOTIFICATION_ID,
                VpnNotificationFactory.build(this, VpnManager.stateSnapshot()),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )

            val profiles = dependencies.profileStore.getProfiles()
            if (profiles.isEmpty()) {
                publishState(TunnelStatus.ERROR, message = "No VPN servers available")
                cleanupStoppedState("No VPN servers available", stopSelfAfter = true)
                return@withLock
            }

            val (targetProfile, initialPingMs) =
                when {
                    requestedProfileId != null -> resolveRequestedProfile(dependencies, profiles, requestedProfileId)
                    isAutoMode -> selectBestProfile(dependencies, profiles, announceProgress = true)
                    else -> resolveManualProfile(dependencies, profiles, selection.profileId)
                }

            runCatching {
                startWithProfile(
                    dependencies = dependencies,
                    profile = targetProfile,
                    initialPingMs = initialPingMs,
                    preserveSession = false,
                )
            }.onFailure { error ->
                publishState(TunnelStatus.ERROR, message = error.message ?: "Failed to start VPN")
                cleanupStoppedState(error.message ?: "Failed to start VPN", stopSelfAfter = true)
            }
        }
    }

    private suspend fun resolveRequestedProfile(
        dependencies: VpnDependencies,
        profiles: List<TunnelProfile>,
        requestedProfileId: String,
    ): Pair<TunnelProfile, Long?> {
        val targetProfile =
            profiles.firstOrNull { it.id == requestedProfileId }
                ?: profiles.minByOrNull { it.lastPingMs ?: Long.MAX_VALUE }
                ?: profiles.first()
        val pingMs = probeProfile(targetProfile)
        dependencies.profileStore.updatePings(mapOf(targetProfile.id to pingMs))
        return targetProfile to pingMs
    }

    private suspend fun resolveManualProfile(
        dependencies: VpnDependencies,
        profiles: List<TunnelProfile>,
        selectedProfileId: String?,
    ): Pair<TunnelProfile, Long?> {
        val targetProfile =
            profiles.firstOrNull { it.id == selectedProfileId }
                ?: profiles.minByOrNull { it.lastPingMs ?: Long.MAX_VALUE }
                ?: profiles.first()
        val pingMs = probeProfile(targetProfile)
        dependencies.profileStore.updatePings(mapOf(targetProfile.id to pingMs))
        return targetProfile to pingMs
    }

    private suspend fun startWithProfile(
        dependencies: VpnDependencies,
        profile: TunnelProfile,
        initialPingMs: Long? = null,
        preserveSession: Boolean,
    ) {
        stopCoreOnly()

        val runtimeConfig = dependencies.configFactory.buildRuntimeConfig(profile)
        val splitTunnelSettings = dependencies.splitTunnelSettings()
        val builder =
            Builder()
                .setSession(profile.displayName)
                .setMtu(dependencies.mtu())
                .addAddress("10.10.0.2", 30)
                .addRoute("0.0.0.0", 0)

        applySplitTunnel(builder, splitTunnelSettings)

        dependencies.dnsServers().forEach(builder::addDnsServer)

        tunInterface = builder.establish()
        val tunFd = tunInterface?.fd ?: throw IllegalStateException("Unable to establish VPN interface")

        core.start(runtimeConfig, tunFd)
        activeProfile = profile
        activePingMs = initialPingMs ?: profile.lastPingMs
        consecutiveProbeFailures = 0
        lastCurrentValidationAtMs = 0L
        if (!preserveSession || startedAtEpochMs == null) {
            startedAtEpochMs = System.currentTimeMillis()
            resetTrafficSession()
        }
        dependencies.profileStore.setActiveProfileId(profile.id)

        publishState(
            TunnelStatus.CONNECTED,
            activeProfile = profile,
            pingMs = activePingMs,
            startedAt = startedAtEpochMs,
        )
        updateNotificationNow()
        startNotificationTicker()
        startHealthMonitoringLoop(dependencies)
    }

    private fun applySplitTunnel(
        builder: Builder,
        settings: com.white.vpn.domain.SplitTunnelSettings,
    ) {
        when (settings.mode) {
            SplitTunnelMode.OFF -> {
                // Keep the app's own sockets out of the tunnel to avoid proxy loops.
                runCatching {
                    builder.addDisallowedApplication(packageName)
                }
            }

            SplitTunnelMode.BYPASS -> {
                sequenceOf(packageName)
                    .plus(settings.packages.asSequence())
                    .map(String::trim)
                    .filter(String::isNotEmpty)
                    .distinct()
                    .forEach { packageName ->
                        runCatching {
                            builder.addDisallowedApplication(packageName)
                        }
                    }
            }

            SplitTunnelMode.INCLUDE -> {
                settings.packages
                    .asSequence()
                    .map(String::trim)
                    .filter(String::isNotEmpty)
                    .filterNot { it == packageName }
                    .distinct()
                    .forEach { allowedPackageName ->
                        runCatching {
                            builder.addAllowedApplication(allowedPackageName)
                        }
                    }
            }
        }
    }

    private suspend fun selectBestProfile(
        dependencies: VpnDependencies,
        profiles: List<TunnelProfile>,
        announceProgress: Boolean,
    ): Pair<TunnelProfile, Long?> {
        if (announceProgress) {
            publishState(TunnelStatus.CONNECTING, message = getString(com.white.vpn.R.string.status_selecting_server))
            updateNotificationNow()
        }

        val probeResults = probeProfiles(profiles)
        dependencies.profileStore.updatePings(probeResults.mapKeys { it.key.id })

        val reachableProfiles =
            probeResults.entries
                .mapNotNull { (profile, pingMs) -> pingMs?.let { profile to it } }
                .sortedBy { it.second }

        if (reachableProfiles.isEmpty()) {
            throw IllegalStateException("No reachable VPN servers available")
        }

        return pickBestReachableProfile(reachableProfiles)
            ?: throw IllegalStateException("No working VPN servers available")
    }

    private fun startHealthMonitoringLoop(dependencies: VpnDependencies) {
        autoSelectionJob?.cancel()
        pingRefreshJob?.cancel()
        autoSelectionJob =
            serviceScope.launch {
                var lastFullSelectionAtMs = 0L
                while (isActive && core.isRunning) {
                    val profiles = dependencies.profileStore.getProfiles()
                    if (profiles.isEmpty()) {
                        delay(ACTIVE_PING_INTERVAL_MS)
                        continue
                    }

                    val probeResults = probeProfiles(profiles)
                    dependencies.profileStore.updatePings(probeResults.mapKeys { it.key.id })

                    val currentProfile = activeProfile
                    if (currentProfile == null) {
                        delay(ACTIVE_PING_INTERVAL_MS)
                        continue
                    }
                    if (!core.isRunning || activeProfile?.id != currentProfile.id) {
                        delay(ACTIVE_PING_INTERVAL_MS)
                        continue
                    }

                    val currentPingMs = probeResults.entries.firstOrNull { it.key.id == currentProfile.id }?.value
                    if (currentPingMs != null) {
                        consecutiveProbeFailures = 0
                        val now = System.currentTimeMillis()
                        if (now - lastCurrentValidationAtMs >= CURRENT_PROFILE_VALIDATION_INTERVAL_MS) {
                            lastCurrentValidationAtMs = now
                            val validationPassed = validateProfile(currentProfile, forceRefresh = true)
                            if (!validationPassed) {
                                activePingMs = null
                                publishState(
                                    if (isAutoMode) TunnelStatus.CONNECTING else TunnelStatus.CONNECTED,
                                    activeProfile = currentProfile,
                                    pingMs = null,
                                    startedAt = startedAtEpochMs,
                                    message =
                                        if (isAutoMode) {
                                            getString(com.white.vpn.R.string.status_selecting_server)
                                        } else {
                                            getString(com.white.vpn.R.string.status_ping_unknown)
                                        },
                                )
                                updateNotificationNow()
                                if (isAutoMode) {
                                    lastFullSelectionAtMs = now
                                    maybeSwitchToBestProfile(
                                        dependencies = dependencies,
                                        profiles = profiles,
                                        probeResults = probeResults,
                                        announceProgress = false,
                                        currentPingMs = currentPingMs,
                                        forceSwitch = true,
                                    )
                                }
                                delay(ACTIVE_PING_INTERVAL_MS)
                                continue
                            }
                        }
                        activeProfile = currentProfile.copy(lastPingMs = currentPingMs)
                        activePingMs = currentPingMs
                        publishState(
                            TunnelStatus.CONNECTED,
                            activeProfile = activeProfile,
                            pingMs = currentPingMs,
                            startedAt = startedAtEpochMs,
                        )
                        updateNotificationNow()
                        if (isAutoMode && System.currentTimeMillis() - lastFullSelectionAtMs >= AUTO_SELECTION_INTERVAL_MS) {
                            lastFullSelectionAtMs = System.currentTimeMillis()
                            maybeSwitchToBestProfile(
                                dependencies = dependencies,
                                profiles = profiles,
                                probeResults = probeResults,
                                announceProgress = false,
                                currentPingMs = currentPingMs,
                                forceSwitch = false,
                            )
                        }
                        delay(ACTIVE_PING_INTERVAL_MS)
                        continue
                    }

                    consecutiveProbeFailures += 1
                    activePingMs = null
                    publishState(
                        if (isAutoMode) TunnelStatus.CONNECTING else TunnelStatus.CONNECTED,
                        activeProfile = currentProfile,
                        pingMs = null,
                        startedAt = startedAtEpochMs,
                        message =
                            if (isAutoMode) {
                                getString(com.white.vpn.R.string.status_selecting_server)
                            } else {
                                getString(com.white.vpn.R.string.status_ping_unknown)
                            },
                    )
                    updateNotificationNow()

                    if (isAutoMode && consecutiveProbeFailures >= MAX_CONSECUTIVE_PROBE_FAILURES) {
                        lastFullSelectionAtMs = System.currentTimeMillis()
                        maybeSwitchToBestProfile(
                            dependencies = dependencies,
                            profiles = profiles,
                            probeResults = probeResults,
                            announceProgress = false,
                            currentPingMs = null,
                            forceSwitch = true,
                        )
                    }
                    delay(ACTIVE_PING_INTERVAL_MS)
                }
            }
    }

    private suspend fun maybeSwitchToBestProfile(
        dependencies: VpnDependencies,
        profiles: List<TunnelProfile>,
        probeResults: Map<TunnelProfile, Long?>,
        announceProgress: Boolean,
        currentPingMs: Long?,
        forceSwitch: Boolean,
    ) {
        if (!isAutoMode) return
        val currentProfile = activeProfile ?: return
        if (profiles.isEmpty() || !core.isRunning || activeProfile?.id != currentProfile.id) return

        val bestCandidate =
            selectBestCandidateFromProbeResults(
                profiles = profiles,
                probeResults = probeResults,
                announceProgress = announceProgress,
            ) ?: return
        val (bestProfile, bestPingMs) = bestCandidate
        if (bestProfile.id == currentProfile.id) {
            activePingMs = bestPingMs ?: activePingMs
            publishState(
                TunnelStatus.CONNECTED,
                activeProfile = currentProfile,
                pingMs = activePingMs,
                startedAt = startedAtEpochMs,
            )
            updateNotificationNow()
            return
        }
        if (!forceSwitch && currentPingMs != null && bestPingMs != null && (currentPingMs - bestPingMs) < MIN_SWITCH_IMPROVEMENT_MS) {
            return
        }

        operationMutex.withLock {
            if (!core.isRunning || activeProfile?.id != currentProfile.id) {
                return@withLock
            }
            startWithProfile(
                dependencies = dependencies,
                profile = bestProfile,
                initialPingMs = bestPingMs,
                preserveSession = true,
            )
        }
    }

    private suspend fun selectBestCandidateFromProbeResults(
        profiles: List<TunnelProfile>,
        probeResults: Map<TunnelProfile, Long?>,
        announceProgress: Boolean,
    ): Pair<TunnelProfile, Long?>? {
        if (profiles.isEmpty()) return null
        if (announceProgress) {
            publishState(
                TunnelStatus.CONNECTING,
                message = getString(com.white.vpn.R.string.status_selecting_server),
            )
            updateNotificationNow()
        }

        val reachableProfiles =
            probeResults.entries
                .mapNotNull { (profile, pingMs) -> pingMs?.let { profile to it } }
                .sortedBy { it.second }

        if (reachableProfiles.isEmpty()) {
            return null
        }

        return pickBestReachableProfile(candidates = reachableProfiles)
    }

    private suspend fun probeProfiles(
        profiles: List<TunnelProfile>,
    ): Map<TunnelProfile, Long?> = supervisorScope {
        val semaphore = Semaphore(PROBE_CONCURRENCY)
        profiles.associateWith { profile ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    probeProfile(profile)
                }
            }
        }.mapValues { (_, deferred) -> deferred.await() }
    }

    private suspend fun pickBestReachableProfile(
        candidates: List<Pair<TunnelProfile, Long>>,
    ): Pair<TunnelProfile, Long>? = candidates.firstOrNull()

    private suspend fun validateProfile(
        profile: TunnelProfile,
        forceRefresh: Boolean = false,
    ): Boolean =
        validationMutex.withLock {
            withContext(Dispatchers.IO) {
                val cacheKey = validationCacheKey(profile)
                val now = System.currentTimeMillis()
                validationCache[cacheKey]?.let { cached ->
                    if (!forceRefresh && now - cached.checkedAtMs <= VALIDATION_CACHE_TTL_MS) {
                        return@withContext cached.result
                    }
                }

                if (!core.isRunning || activeProfile?.id != profile.id) {
                    validationCache[cacheKey] = ValidationCacheEntry(false, now)
                    return@withContext false
                }

                var telegramSuccessCount = 0
                var remainingTelegramChecks = TELEGRAM_VALIDATION_URLS.size
                for (url in TELEGRAM_VALIDATION_URLS) {
                    if (measureValidationUrl(url)) {
                        telegramSuccessCount += 1
                        if (telegramSuccessCount >= MIN_TELEGRAM_VALIDATIONS) {
                            break
                        }
                    }
                    remainingTelegramChecks -= 1
                    if (telegramSuccessCount + remainingTelegramChecks < MIN_TELEGRAM_VALIDATIONS) {
                        break
                    }
                }
                val youtubeSuccess =
                    telegramSuccessCount >= MIN_TELEGRAM_VALIDATIONS &&
                        YOUTUBE_VALIDATION_URLS.any { url -> measureValidationUrl(url) }
                val result = telegramSuccessCount >= MIN_TELEGRAM_VALIDATIONS && youtubeSuccess
                validationCache[cacheKey] = ValidationCacheEntry(result, now)
                result
            }
        }

    private suspend fun measureValidationUrl(
        url: String,
    ): Boolean =
        withTimeoutOrNull(VALIDATION_TIMEOUT_MS) {
            runCatching {
                core.measureCurrentDelay(url) >= 0L
            }.getOrDefault(false)
        } == true

    private suspend fun probeProfile(profile: TunnelProfile): Long? =
        withContext(Dispatchers.IO) {
            if (profile.id.isBlank() || profile.displayName.isBlank()) {
                return@withContext null
            }
            if (profile.id == "auto" || profile.host.isBlank() || profile.port <= 0) {
                return@withContext null
            }
            runCatching {
                val startedAt = System.nanoTime()
                Socket().use { socket ->
                    socket.tcpNoDelay = true
                    socket.connect(
                        InetSocketAddress(profile.host, profile.port),
                        TCP_PROBE_TIMEOUT_MS,
                    )
                }
                ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(1L)
            }.getOrNull()
        }

    private suspend fun stopTunnel(
        stopSelfAfter: Boolean = true,
        message: String = "Disconnected",
    ) = operationMutex.withLock {
        publishState(
            TunnelStatus.STOPPING,
            activeProfile = activeProfile,
            message = getString(com.white.vpn.R.string.status_stopping),
        )
        cleanupStoppedState(message = message, stopSelfAfter = stopSelfAfter)
    }

    private fun stopCoreOnly() {
        pingRefreshJob?.cancel()
        pingRefreshJob = null
        notificationJob?.cancel()
        notificationJob = null
        collectCoreTrafficSnapshot()
        runCatching { core.stop() }
        runCatching { tunInterface?.close() }
        tunInterface = null
    }

    private fun cleanupStoppedState(
        message: String,
        stopSelfAfter: Boolean,
    ) {
        autoSelectionJob?.cancel()
        autoSelectionJob = null
        pingRefreshJob?.cancel()
        pingRefreshJob = null
        stopCoreOnly()
        resetSessionState()
        publishState(TunnelStatus.IDLE, message = message)
        stopForeground(STOP_FOREGROUND_REMOVE)
        if (stopSelfAfter) {
            stopSelf()
        }
    }

    private fun resetSessionState() {
        activeProfile = null
        activePingMs = null
        startedAtEpochMs = null
        isAutoMode = true
        sessionAccumulatedRxBytes = 0L
        sessionAccumulatedTxBytes = 0L
        consecutiveProbeFailures = 0
        lastCurrentValidationAtMs = 0L
        validationCache.clear()
    }

    private fun resetTrafficSession() {
        sessionAccumulatedRxBytes = 0L
        sessionAccumulatedTxBytes = 0L
    }

    private fun collectCoreTrafficSnapshot() {
        if (!core.isRunning) return
        sessionAccumulatedRxBytes += queryCoreTraffic(TrafficDirection.DOWNLINK)
        sessionAccumulatedTxBytes += queryCoreTraffic(TrafficDirection.UPLINK)
    }

    private fun queryCoreTraffic(direction: TrafficDirection): Long {
        return TRAFFIC_OUTBOUND_TAGS.sumOf { tag ->
            runCatching {
                core.queryStats(tag, direction.wireValue)
            }.getOrDefault(0L).coerceAtLeast(0L)
        }
    }

    private fun currentSessionRxBytes(): Long {
        return sessionAccumulatedRxBytes.coerceAtLeast(0L)
    }

    private fun currentSessionTxBytes(): Long {
        return sessionAccumulatedTxBytes.coerceAtLeast(0L)
    }

    private fun publishState(
        status: TunnelStatus,
        activeProfile: TunnelProfile? = this.activeProfile,
        pingMs: Long? = this.activePingMs,
        startedAt: Long? = this.startedAtEpochMs,
        message: String? = null,
        permissionIntent: Intent? = null,
        updateWidgets: Boolean = true,
    ) {
        VpnManager.publish(
            VpnRuntimeState(
                status = status,
                activeProfileId = activeProfile?.id,
                activeProfileName = activeProfile?.displayName,
                activePingMs = pingMs,
                startedAtEpochMs = startedAt,
                sessionRxBytes = currentSessionRxBytes(),
                sessionTxBytes = currentSessionTxBytes(),
                isAutoMode = isAutoMode,
                message = message,
                permissionIntent = permissionIntent,
            ),
            this,
            updateWidgets = updateWidgets,
        )
    }

    private fun startNotificationTicker() {
        notificationJob?.cancel()
        notificationJob =
            serviceScope.launch {
                while (isActive && core.isRunning && startedAtEpochMs != null) {
                    collectCoreTrafficSnapshot()
                    publishState(
                        TunnelStatus.CONNECTED,
                        activeProfile = activeProfile,
                        pingMs = activePingMs,
                        startedAt = startedAtEpochMs,
                        updateWidgets = false,
                    )
                    updateNotificationNow()
                    delay(NOTIFICATION_UPDATE_INTERVAL_MS)
                }
            }
    }

    private fun updateNotificationNow() {
        val manager =
            ContextCompat.getSystemService(
                this,
                android.app.NotificationManager::class.java,
            )
        manager?.notify(
            VpnNotificationFactory.NOTIFICATION_ID,
            VpnNotificationFactory.build(this, VpnManager.stateSnapshot()),
        )
    }

    private val callbackHandler =
        object : CoreCallbackHandler {
            override fun startup(): Long = 0L

            override fun shutdown(): Long = 0L

            override fun onEmitStatus(
                status: Long,
                message: String?,
            ): Long {
                if (status != 0L) {
                    publishState(
                        TunnelStatus.ERROR,
                        activeProfile = activeProfile,
                        message = message ?: "Core error",
                    )
                }
                return 0L
            }
        }

    companion object {
        const val ACTION_START = "com.white.vpn.action.START"
        const val ACTION_STOP = "com.white.vpn.action.STOP"
        const val ACTION_TOGGLE = "com.white.vpn.action.TOGGLE"
        const val EXTRA_PROFILE_ID = "profile_id"

        private const val ACTIVE_PING_INTERVAL_MS = 5_000L
        private const val AUTO_SELECTION_INTERVAL_MS = 5 * 60_000L
        private const val CURRENT_PROFILE_VALIDATION_INTERVAL_MS = 5_000L
        private const val NOTIFICATION_UPDATE_INTERVAL_MS = 1_000L
        private const val TCP_PROBE_TIMEOUT_MS = 1_500
        private const val PROBE_CONCURRENCY = 16
        private const val VALIDATION_TIMEOUT_MS = 3_000L
        private const val VALIDATION_CACHE_TTL_MS = 60_000L
        private const val MAX_CONSECUTIVE_PROBE_FAILURES = 2
        private const val MIN_SWITCH_IMPROVEMENT_MS = 25L
        private const val MIN_TELEGRAM_VALIDATIONS = 2
        private val TELEGRAM_VALIDATION_URLS =
            listOf(
                "https://telegram.org/robots.txt",
                "https://core.telegram.org/robots.txt",
                "https://web.telegram.org/",
                "https://t.me/",
                "https://desktop.telegram.org/",
            )
        private val YOUTUBE_VALIDATION_URLS =
            listOf(
                "https://www.youtube.com/robots.txt",
                "https://m.youtube.com/robots.txt",
            )
        private val TRAFFIC_OUTBOUND_TAGS = listOf("proxy", "direct")

        fun start(
            context: android.content.Context,
            profileId: String? = null,
        ) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, PerdonusVpnService::class.java).apply {
                    action = ACTION_START
                    putExtra(EXTRA_PROFILE_ID, profileId)
                },
            )
        }
    }

    private enum class TrafficDirection(
        val wireValue: String,
    ) {
        UPLINK("uplink"),
        DOWNLINK("downlink"),
    }

    private data class ValidationCacheEntry(
        val result: Boolean,
        val checkedAtMs: Long,
    )

    private fun validationCacheKey(profile: TunnelProfile): String {
        return buildString {
            append(profile.id)
            append('|')
            append(profile.host)
            append('|')
            append(profile.port)
        }
    }
}
