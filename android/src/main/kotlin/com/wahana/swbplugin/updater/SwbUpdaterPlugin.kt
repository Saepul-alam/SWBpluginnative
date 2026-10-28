package com.wahana.swbplugin.updater

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin

@CapacitorPlugin(name = "SwbUpdater")
class SwbUpdaterPlugin : Plugin() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var stateStore: SwbUpdateStateStore
    private lateinit var manager: SwbUpdateManager

    private val activator = object : BundleActivator {
        override fun activateBundle(bundlePath: String, onComplete: (Boolean, Throwable?) -> Unit) {
            mainHandler.postDelayed({
                try {
                    val prefs = context.getSharedPreferences("CapWebViewSettings", Context.MODE_PRIVATE)
                    prefs.edit().putString("serverBasePath", bundlePath).apply()
                    bridge.setServerBasePath(bundlePath)
                    onComplete(true, null)
                } catch (t: Throwable) {
                    onComplete(false, t)
                }
            }, 350)
        }

        override fun resetToBuiltin(onComplete: (Boolean, Throwable?) -> Unit) {
            mainHandler.postDelayed({
                try {
                    val prefs = context.getSharedPreferences("CapWebViewSettings", Context.MODE_PRIVATE)
                    prefs.edit().remove("serverBasePath").apply()
                    bridge.setServerAssetPath("public")
                    onComplete(true, null)
                } catch (t: Throwable) {
                    onComplete(false, t)
                }
            }, 350)
        }

        override fun getServerBasePath(): String? {
            return bridge?.serverBasePath
        }

        override fun isDevelopmentServer(): Boolean {
            val serverUrl = bridge?.config?.serverUrl
            return !serverUrl.isNullOrBlank()
        }
    }

    override fun load() {
        super.load()
        stateStore = SharedPreferencesStateStore(context)
        manager = SwbUpdateManager(
            context = context,
            stateStore = stateStore,
            activator = activator,
            mainHandler = mainHandler
        )
    }

    override fun handleOnStart() {
        super.handleOnStart()
        manager.handleStartupOrResume(
            config = readPluginConfig(),
            onStateChange = { emitStateEvent(it) }
        )
    }

    override fun handleOnResume() {
        super.handleOnResume()
        manager.handleStartupOrResume(
            config = readPluginConfig(),
            onStateChange = { emitStateEvent(it) }
        )
    }

    override fun handleOnDestroy() {
        super.handleOnDestroy()
        manager.shutdown()
    }

    // --- PLUGIN METHODS ---

    @PluginMethod
    fun checkForUpdate(call: PluginCall) {
        val config = readPluginConfig()
        Thread {
            try {
                val checkResult = manager.checkForUpdate(
                    config = config,
                    onStateChange = { emitStateEvent(it) }
                )

                val ret = JSObject().apply {
                    put("available", checkResult.available)
                    put("currentBuild", checkResult.currentBuild)
                    put("remoteBuild", checkResult.remoteBuild)
                    put("version", checkResult.version)
                    if (checkResult.size != null) {
                        put("size", checkResult.size)
                    }
                }
                mainHandler.post { call.resolve(ret) }
            } catch (e: SwbUpdateException) {
                mainHandler.post { call.reject(e.message, e.code, e) }
            } catch (t: Throwable) {
                val ex = if (t is Exception) t else Exception(t)
                mainHandler.post { call.reject(t.message ?: "Pemeriksaan update gagal", "CHECK_FAILED", ex) }
            }
        }.start()
    }

    @PluginMethod
    fun sync(call: PluginCall) {
        val config = readPluginConfig()
        manager.sync(
            config = config,
            onStateChange = { emitStateEvent(it) },
            onSuccess = { result ->
                val ret = JSObject().apply {
                    put("updated", result.updated)
                    put("fromBuild", result.fromBuild)
                    if (result.toBuild != null) {
                        put("toBuild", result.toBuild)
                    }
                    if (result.version != null) {
                        put("version", result.version)
                    }
                }
                call.resolve(ret)
            },
            onError = { ex ->
                call.reject(ex.message, ex.code, ex)
            }
        )
    }

    @PluginMethod
    fun getCurrentBundle(call: PluginCall) {
        val config = readPluginConfig()
        try {
            val bundle = manager.getCurrentBundle(config)
            val ret = JSObject().apply {
                put("build", bundle.build)
                put("version", bundle.version)
                put("source", bundle.source.value)
                put("path", bundle.path)
            }
            call.resolve(ret)
        } catch (t: Throwable) {
            val ex = if (t is Exception) t else Exception(t)
            call.reject(t.message ?: "Gagal mengambil informasi bundle saat ini", "GET_BUNDLE_FAILED", ex)
        }
    }

    @PluginMethod
    fun resetToBuiltin(call: PluginCall) {
        val config = readPluginConfig()
        manager.resetToBuiltin(
            config = config,
            onStateChange = { emitStateEvent(it) },
            onSuccess = {
                call.resolve()
            },
            onError = { ex ->
                call.reject(ex.message, ex.code, ex)
            }
        )
    }

    // --- PRIVATE HELPERS ---

    private fun readPluginConfig(): SwbUpdaterConfig {
        val pluginConfig = config
        val updateUrl = pluginConfig.getString("updateUrl", "") ?: ""
        val autoUpdate = pluginConfig.getBoolean("autoUpdate", false)
        val baseBuild = pluginConfig.getInt("baseBuild", 1)
        val baseVersion = pluginConfig.getString("baseVersion", "1.0.0") ?: "1.0.0"
        val allowInsecureHttp = pluginConfig.getBoolean("allowInsecureHttp", false)

        return SwbUpdaterConfig(
            updateUrl = updateUrl,
            autoUpdate = autoUpdate,
            baseBuild = baseBuild,
            baseVersion = baseVersion,
            allowInsecureHttp = allowInsecureHttp
        )
    }

    private fun emitStateEvent(event: UpdateStateEvent) {
        mainHandler.post {
            val obj = JSObject().apply {
                put("state", event.state.value)
                if (event.message != null) {
                    put("message", event.message)
                }
                if (event.build != null) {
                    put("build", event.build)
                }
                if (event.version != null) {
                    put("version", event.version)
                }
            }
            notifyListeners("updateStateChanged", obj)
        }
    }
}
