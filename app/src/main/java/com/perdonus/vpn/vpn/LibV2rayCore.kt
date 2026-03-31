package com.perdonus.vpn.vpn

import android.content.Context
import android.provider.Settings
import go.Seq
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import java.util.concurrent.atomic.AtomicBoolean

interface VpnCore {
    val isRunning: Boolean
    fun start(configJson: String, tunFd: Int)
    fun stop()
    fun measureOutboundDelay(configJson: String, testUrl: String): Long
}

class LibV2rayCore(
    context: Context,
    private val callback: CoreCallbackHandler,
) : VpnCore {
    private val appContext = context.applicationContext
    private val controller: CoreController

    init {
        initializeCore(appContext)
        controller = Libv2ray.newCoreController(callback)
    }

    override val isRunning: Boolean
        get() = controller.isRunning

    override fun start(configJson: String, tunFd: Int) {
        controller.startLoop(configJson, tunFd)
    }

    override fun stop() {
        controller.stopLoop()
    }

    override fun measureOutboundDelay(configJson: String, testUrl: String): Long {
        return Libv2ray.measureOutboundDelay(configJson, testUrl)
    }

    companion object {
        private val initialized = AtomicBoolean(false)

        private fun initializeCore(context: Context) {
            if (!initialized.compareAndSet(false, true)) {
                return
            }
            Seq.setContext(context)
            val androidId = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID,
            ).orEmpty()
            Libv2ray.initCoreEnv(context.filesDir.absolutePath, androidId)
        }
    }
}
