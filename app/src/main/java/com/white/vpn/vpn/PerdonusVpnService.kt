package com.white.vpn.vpn

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
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
import libv2ray.CoreCallbackHandler

class PerdonusVpnService : VpnService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val operationMutex = Mutex()

    private lateinit var core: VpnCore
    private var tunInterface: ParcelFileDescriptor? = null
    private var activeProfile: TunnelProfile? = null
    private var activePingMs: Long? = null
    private var startedAtEpochMs: Long? = null
    private var isAutoMode: Boolean = true

    private var notificationJob: Job? = null
    private var autoPingJob: Job? = null

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
        notificationJob?.cancel()
        autoPingJob?.cancel()
        stopCoreOnly()
        activeProfile = null
        activePingMs = null
        startedAtEpochMs = null
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
                    message = "VPN permission required",
                    permissionIntent = permissionIntent,
                )
                stopSelf()
                return@withLock
            }

            publishState(TunnelStatus.CONNECTING, message = "Connecting")
            VpnNotificationFactory.ensureChannel(this)
            ServiceCompat.startForeground(
                this,
                VpnNotificationFactory.NOTIFICATION_ID,
                VpnNotificationFactory.build(this, VpnManager.stateSnapshot()),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )

            val selection = dependencies.profileStore.getSelection()
            isAutoMode = selection.mode == TunnelSelectionMode.AUTO
            val profiles = dependencies.profileStore.getProfiles()
            if (profiles.isEmpty()) {
                publishState(TunnelStatus.ERROR, message = "No VPN servers available")
                cleanupStoppedState("No VPN servers available", stopSelfAfter = true)
                return@withLock
            }

            val targetProfile = when {
                requestedProfileId != null -> profiles.firstOrNull { it.id == requestedProfileId }
                isAutoMode -> chooseBestProfile(dependencies, profiles)
                else -> profiles.firstOrNull { it.id == selection.profileId }
            } ?: profiles.minByOrNull { it.lastPingMs ?: Long.MAX_VALUE } ?: profiles.first()

            runCatching {
                startWithProfile(dependencies, targetProfile)
            }.onFailure { error ->
                publishState(TunnelStatus.ERROR, message = error.message ?: "Failed to start VPN")
                cleanupStoppedState(error.message ?: "Failed to start VPN", stopSelfAfter = true)
            }
        }
    }

    private suspend fun startWithProfile(
        dependencies: VpnDependencies,
        profile: TunnelProfile,
    ) {
        stopCoreOnly()

        val runtimeConfig = dependencies.configFactory.buildRuntimeConfig(profile)
        val builder = Builder()
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
        startedAtEpochMs = System.currentTimeMillis()
        activePingMs = measurePing(dependencies, profile).takeIf { it >= 0L }
        dependencies.profileStore.setActiveProfileId(profile.id)
        activePingMs?.let { dependencies.profileStore.updatePing(profile.id, it) }

        publishState(
            TunnelStatus.CONNECTED,
            activeProfile = profile,
            pingMs = activePingMs,
            startedAt = startedAtEpochMs,
        )
        startNotificationTicker()
        startAutoPingLoop(dependencies)
    }

    private suspend fun chooseBestProfile(
        dependencies: VpnDependencies,
        profiles: List<TunnelProfile>,
    ): TunnelProfile {
        val measured = measureAllPings(dependencies, profiles)
        val best = measured
            .filterValues { it != null && it >= 0L }
            .minByOrNull { it.value ?: Long.MAX_VALUE }
            ?.key
        return best ?: profiles.minByOrNull { it.lastPingMs ?: Long.MAX_VALUE } ?: profiles.first()
    }

    private fun startNotificationTicker() {
        notificationJob?.cancel()
        notificationJob = serviceScope.launch {
            while (isActive && core.isRunning) {
                publishState(
                    TunnelStatus.CONNECTED,
                    activeProfile = activeProfile,
                    pingMs = activePingMs,
                    startedAt = startedAtEpochMs,
                )
                val manager = ContextCompat.getSystemService(
                    this@PerdonusVpnService,
                    android.app.NotificationManager::class.java,
                )
                manager?.notify(
                    VpnNotificationFactory.NOTIFICATION_ID,
                    VpnNotificationFactory.build(this@PerdonusVpnService, VpnManager.stateSnapshot()),
                )
                delay(1000L)
            }
        }
    }

    private fun startAutoPingLoop(dependencies: VpnDependencies) {
        autoPingJob?.cancel()
        if (!isAutoMode) return
        autoPingJob = serviceScope.launch {
            while (isActive && core.isRunning) {
                delay(60_000L)
                val profiles = dependencies.profileStore.getProfiles()
                if (profiles.isEmpty()) continue
                val current = activeProfile ?: continue
                val measured = measureAllPings(dependencies, profiles)
                val bestProfile = measured
                    .filterValues { it != null && it >= 0L }
                    .minByOrNull { it.value ?: Long.MAX_VALUE }
                    ?.key
                    ?: continue
                activePingMs = measured[current] ?: activePingMs
                if (bestProfile.id != current.id) {
                    operationMutex.withLock {
                        startWithProfile(dependencies, bestProfile)
                    }
                } else {
                    publishState(
                        TunnelStatus.CONNECTED,
                        activeProfile = current,
                        pingMs = activePingMs,
                        startedAt = startedAtEpochMs,
                    )
                }
            }
        }
    }

    private suspend fun measureAllPings(
        dependencies: VpnDependencies,
        profiles: List<TunnelProfile>,
    ): Map<TunnelProfile, Long?> = supervisorScope {
        val semaphore = Semaphore(4)
        profiles.associateWith { profile ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    measurePing(dependencies, profile).takeIf { it >= 0L }
                }
            }
        }.mapValues { (profile, deferred) ->
            val ping = deferred.await()
            dependencies.profileStore.updatePing(profile.id, ping)
            ping
        }
    }

    private suspend fun measurePing(
        dependencies: VpnDependencies,
        profile: TunnelProfile,
    ): Long = kotlinx.coroutines.withContext(Dispatchers.IO) {
        runCatching {
            core.measureOutboundDelay(
                dependencies.configFactory.buildPingConfig(profile),
                dependencies.pingTestUrl(),
            )
        }.getOrDefault(-1L)
    }

    private suspend fun stopTunnel(
        stopSelfAfter: Boolean = true,
        message: String = "Disconnected",
    ) = operationMutex.withLock {
        publishState(TunnelStatus.STOPPING, activeProfile = activeProfile, message = "Stopping")
        cleanupStoppedState(message = message, stopSelfAfter = stopSelfAfter)
    }

    private fun stopCoreOnly() {
        runCatching { core.stop() }
        runCatching { tunInterface?.close() }
        tunInterface = null
    }

    private fun cleanupStoppedState(
        message: String,
        stopSelfAfter: Boolean,
    ) {
        notificationJob?.cancel()
        notificationJob = null
        autoPingJob?.cancel()
        autoPingJob = null
        stopCoreOnly()
        activeProfile = null
        activePingMs = null
        startedAtEpochMs = null
        publishState(TunnelStatus.IDLE, message = message)
        stopForeground(STOP_FOREGROUND_REMOVE)
        if (stopSelfAfter) {
            stopSelf()
        }
    }

    private fun publishState(
        status: TunnelStatus,
        activeProfile: TunnelProfile? = this.activeProfile,
        pingMs: Long? = this.activePingMs,
        startedAt: Long? = this.startedAtEpochMs,
        message: String? = null,
        permissionIntent: Intent? = null,
    ) {
        VpnManager.publish(
            VpnRuntimeState(
                status = status,
                activeProfileId = activeProfile?.id,
                activeProfileName = activeProfile?.displayName,
                activePingMs = pingMs,
                startedAtEpochMs = startedAt,
                isAutoMode = isAutoMode,
                message = message,
                permissionIntent = permissionIntent,
            ),
            this,
        )
    }

    private val callbackHandler = object : CoreCallbackHandler {
        override fun startup(): Long = 0L

        override fun shutdown(): Long = 0L

        override fun onEmitStatus(status: Long, message: String?): Long {
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

        fun start(context: android.content.Context, profileId: String? = null) {
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
