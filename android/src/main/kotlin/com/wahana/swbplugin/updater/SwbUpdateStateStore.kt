package com.wahana.swbplugin.updater

import android.content.Context
import android.content.SharedPreferences

interface SwbUpdateStateStore {
    fun getActiveBundle(defaultBuild: Int, defaultVersion: String): BundleState
    fun setActiveBundle(state: BundleState)
    fun getPendingUpdate(): PendingUpdateState?
    fun setPendingUpdate(pending: PendingUpdateState?)
    fun clearPendingUpdate()
    fun resetToBuiltin(defaultBuild: Int, defaultVersion: String)
}

class SharedPreferencesStateStore(private val context: Context) : SwbUpdateStateStore {

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    override fun getActiveBundle(defaultBuild: Int, defaultVersion: String): BundleState {
        if (!prefs.contains(KEY_ACTIVE_BUILD)) {
            return BundleState(
                build = defaultBuild,
                version = defaultVersion,
                path = DEFAULT_BUILTIN_PATH,
                source = BundleSource.BUILTIN
            )
        }

        val build = prefs.getInt(KEY_ACTIVE_BUILD, defaultBuild)
        val version = prefs.getString(KEY_ACTIVE_VERSION, defaultVersion) ?: defaultVersion
        val path = prefs.getString(KEY_ACTIVE_PATH, DEFAULT_BUILTIN_PATH) ?: DEFAULT_BUILTIN_PATH
        val sourceStr = prefs.getString(KEY_ACTIVE_SOURCE, BundleSource.BUILTIN.value)
        val source = BundleSource.fromString(sourceStr)

        return BundleState(
            build = build,
            version = version,
            path = path,
            source = source
        )
    }

    override fun setActiveBundle(state: BundleState) {
        prefs.edit()
            .putInt(KEY_ACTIVE_BUILD, state.build)
            .putString(KEY_ACTIVE_VERSION, state.version)
            .putString(KEY_ACTIVE_PATH, state.path)
            .putString(KEY_ACTIVE_SOURCE, state.source.value)
            .apply()
    }

    override fun getPendingUpdate(): PendingUpdateState? {
        if (!prefs.contains(KEY_PENDING_BUILD)) {
            return null
        }
        val build = prefs.getInt(KEY_PENDING_BUILD, -1)
        if (build <= 0) return null

        val version = prefs.getString(KEY_PENDING_VERSION, "") ?: ""
        val path = prefs.getString(KEY_PENDING_PATH, "") ?: ""
        val pendingAt = prefs.getLong(KEY_PENDING_AT, 0L)

        return PendingUpdateState(
            build = build,
            version = version,
            path = path,
            pendingAt = pendingAt
        )
    }

    override fun setPendingUpdate(pending: PendingUpdateState?) {
        if (pending == null) {
            clearPendingUpdate()
            return
        }
        prefs.edit()
            .putInt(KEY_PENDING_BUILD, pending.build)
            .putString(KEY_PENDING_VERSION, pending.version)
            .putString(KEY_PENDING_PATH, pending.path)
            .putLong(KEY_PENDING_AT, pending.pendingAt)
            .apply()
    }

    override fun clearPendingUpdate() {
        prefs.edit()
            .remove(KEY_PENDING_BUILD)
            .remove(KEY_PENDING_VERSION)
            .remove(KEY_PENDING_PATH)
            .remove(KEY_PENDING_AT)
            .apply()
    }

    override fun resetToBuiltin(defaultBuild: Int, defaultVersion: String) {
        prefs.edit()
            .putInt(KEY_ACTIVE_BUILD, defaultBuild)
            .putString(KEY_ACTIVE_VERSION, defaultVersion)
            .putString(KEY_ACTIVE_PATH, DEFAULT_BUILTIN_PATH)
            .putString(KEY_ACTIVE_SOURCE, BundleSource.BUILTIN.value)
            .remove(KEY_PENDING_BUILD)
            .remove(KEY_PENDING_VERSION)
            .remove(KEY_PENDING_PATH)
            .remove(KEY_PENDING_AT)
            .apply()
    }

    companion object {
        const val PREFS_NAME = "swb_updater"
        const val DEFAULT_BUILTIN_PATH = "public"

        private const val KEY_ACTIVE_BUILD = "active_build"
        private const val KEY_ACTIVE_VERSION = "active_version"
        private const val KEY_ACTIVE_PATH = "active_path"
        private const val KEY_ACTIVE_SOURCE = "active_source"

        private const val KEY_PENDING_BUILD = "pending_build"
        private const val KEY_PENDING_VERSION = "pending_version"
        private const val KEY_PENDING_PATH = "pending_path"
        private const val KEY_PENDING_AT = "pending_at"
    }
}

