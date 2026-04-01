package com.white.vpn.vpn

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.TrafficStats
import android.net.VpnService
import android.os.IBinder
import android.os.ParcelFileDescriptor
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
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
import libv2ray.CoreCallbackHandler
import java.net.InetSocketAddress
import java.net.Socket

class PerdonusVpnService : VpnService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val operationMutex = Mutex()

    private lateinit var core: VpnCore
    private var tunInterface: ParcelFileDescriptor? = null
    private var activeProfile: TunnelProfile? = null
    private var activePingMs: Long? = null
    private var startedAtEpochMs: Long? = null
    private var isAutoMode: Boolean = true
    private var sessionBaseRxBytes: Long = 0L
    private var sessionBaseTxBytes: Long = 0L
    private var trafficSupported: Boolean = false
    private var consecutiveProbeFailures: Int = 0

    private var autoSelectionJob: Job? = null
    private var pingRefreshJob: Job? = null
    private var notificationJob: Job? = null

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
        val builder =
            Builder()
                .setSession(profile.displayName)
                .setMtu(dependencies.mtu())
                .addAddress("10.10.0.2", 30)
                .addAddress("fdfe:dcba:9876::2", 126)
                .addRoute("0.0.0.0", 0)
                .addRoute("::", 0)

        dependencies.dnsServers().forEach(builder::addDnsServer)

        tunInterface = builder.establish()
        val tunFd = tunInterface?.fd ?: throw IllegalStateException("Unable to establish VPN interface")

        core.start(runtimeConfig, tunFd)
        activeProfile = profile
        activePingMs = initialPingMs ?: profile.lastPingMs
        consecutiveProbeFailures = 0
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
        startActivePingLoop(dependencies)
        startAutoSelectionLoop(dependencies)
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
            val fallback =
                profiles
                    .filter { it.lastPingMs != null && it.lastPingMs >= 0L }
                    .minByOrNull { it.lastPingMs ?: Long.MAX_VALUE }
                    ?: profiles.first()
            return fallback to fallback.lastPingMs
        }

        if (announceProgress) {
            publishState(TunnelStatus.CONNECTING, message = getString(com.white.vpn.R.string.status_checking_youtube))
            updateNotificationNow()
        }

        return validateReachableProfiles(dependencies, reachableProfiles) ?: reachableProfiles.first()
    }

    private fun startAutoSelectionLoop(dependencies: VpnDependencies) {
        autoSelectionJob?.cancel()
        if (!isAutoMode) return
        autoSelectionJob =
            serviceScope.launch {
                while (isActive && core.isRunning) {
                    delay(AUTO_SELECTION_INTERVAL_MS)
                    maybeSwitchToBestProfile(dependencies, announceProgress = false)
                }
            }
    }

    private fun startActivePingLoop(dependencies: VpnDependencies) {
        pingRefreshJob?.cancel()
        pingRefreshJob =
            serviceScope.launch {
                while (isActive && core.isRunning) {
                    delay(ACTIVE_PING_INTERVAL_MS)
                    val profile = activeProfile ?: continue
                    val measuredPing = probeProfile(profile)

                    if (!core.isRunning || activeProfile?.id != profile.id) {
                        continue
                    }

                    if (measuredPing != null) {
                        consecutiveProbeFailures = 0
                        activePingMs = measuredPing
                        publishState(
                            TunnelStatus.CONNECTED,
                            activeProfile = profile,
                            pingMs = measuredPing,
                            startedAt = startedAtEpochMs,
                        )
                        updateNotificationNow()
                        continue
                    }

                    activePingMs = null
                    consecutiveProbeFailures += 1
                    publishState(
                        TunnelStatus.CONNECTED,
                        activeProfile = profile,
                        pingMs = null,
                        startedAt = startedAtEpochMs,
                    )
                    updateNotificationNow()

                    if (isAutoMode && consecutiveProbeFailures >= MAX_CONSECUTIVE_PROBE_FAILURES) {
                        consecutiveProbeFailures = 0
                        maybeSwitchToBestProfile(dependencies, announceProgress = false)
                    }
                }
            }
    }

    private suspend fun maybeSwitchToBestProfile(
        dependencies: VpnDependencies,
        announceProgress: Boolean,
    ) {
        val profiles = dependencies.profileStore.getProfiles()
        if (profiles.isEmpty()) return
        val currentProfile = activeProfile ?: return
        val (bestProfile, bestPingMs) = selectBestProfile(dependencies, profiles, announceProgress)

        if (!core.isRunning) return
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

    private suspend fun validateReachableProfiles(
        dependencies: VpnDependencies,
        candidates: List<Pair<TunnelProfile, Long>>,
    ): Pair<TunnelProfile, Long>? {
        val deadlineAt = System.currentTimeMillis() + VALIDATION_TIME_BUDGET_MS
        candidates.forEach { (profile, pingMs) ->
            if (System.currentTimeMillis() >= deadlineAt) {
                return null
            }
            if (validateProfile(dependencies, profile)) {
                return profile to pingMs
            }
        }
        return null
    }

    private suspend fun validateProfile(
        dependencies: VpnDependencies,
        profile: TunnelProfile,
    ): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            core.measureOutboundDelay(
                dependencies.configFactory.buildPingConfig(profile),
                YOUTUBE_VALIDATION_URL,
            ) >= 0L
        }.getOrDefault(false)
    }

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
        sessionBaseRxBytes = 0L
        sessionBaseTxBytes = 0L
        trafficSupported = false
        consecutiveProbeFailures = 0
    }

    private fun resetTrafficSession() {
        val uid = applicationInfo.uid
        val rxBytes = TrafficStats.getUidRxBytes(uid)
        val txBytes = TrafficStats.getUidTxBytes(uid)
        trafficSupported =
            rxBytes != TrafficStats.UNSUPPORTED.toLong() &&
                txBytes != TrafficStats.UNSUPPORTED.toLong()
        sessionBaseRxBytes = if (trafficSupported) rxBytes else 0L
        sessionBaseTxBytes = if (trafficSupported) txBytes else 0L
    }

    private fun currentSessionRxBytes(): Long {
        if (!trafficSupported) return 0L
        val current = TrafficStats.getUidRxBytes(applicationInfo.uid)
        if (current == TrafficStats.UNSUPPORTED.toLong()) return 0L
        return (current - sessionBaseRxBytes).coerceAtLeast(0L)
    }

    private fun currentSessionTxBytes(): Long {
        if (!trafficSupported) return 0L
        val current = TrafficStats.getUidTxBytes(applicationInfo.uid)
        if (current == TrafficStats.UNSUPPORTED.toLong()) return 0L
        return (current - sessionBaseTxBytes).coerceAtLeast(0L)
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
        private const val NOTIFICATION_UPDATE_INTERVAL_MS = 1_000L
        private const val TCP_PROBE_TIMEOUT_MS = 1_500
        private const val PROBE_CONCURRENCY = 16
        private const val VALIDATION_TIME_BUDGET_MS = 20_000L
        private const val MAX_CONSECUTIVE_PROBE_FAILURES = 2
        private const val YOUTUBE_VALIDATION_URL = "https://www.youtube.com/robots.txt"

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
}
