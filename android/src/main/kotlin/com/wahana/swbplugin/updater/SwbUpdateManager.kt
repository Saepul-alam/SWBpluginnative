package com.wahana.swbplugin.updater

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

interface BundleActivator {
    fun activateBundle(bundlePath: String, onComplete: (Boolean, Throwable?) -> Unit)
    fun resetToBuiltin(onComplete: (Boolean, Throwable?) -> Unit)
    fun getServerBasePath(): String?
    fun isDevelopmentServer(): Boolean
}

data class SwbUpdaterConfig(
    val updateUrl: String,
    val autoUpdate: Boolean = false,
    val baseBuild: Int = 1,
    val baseVersion: String = "1.0.0",
    val allowInsecureHttp: Boolean = false
)

data class UpdateCheckResult(
    val available: Boolean,
    val currentBuild: Int,
    val remoteBuild: Int,
    val version: String? = null,
    val size: Long? = null
)

data class UpdateResult(
    val updated: Boolean,
    val fromBuild: Int,
    val toBuild: Int? = null,
    val version: String? = null
)

class SwbUpdateManager(
    private val context: Context,
    private val stateStore: SwbUpdateStateStore,
    private val activator: BundleActivator,
    private val mainHandler: Handler = Handler(Looper.getMainLooper()),
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
) {

    private val isOperationInProgress = AtomicBoolean(false)
    private val hasRunAutoUpdateSession = AtomicBoolean(false)

    private val otaRootDir: File
        get() = File(context.filesDir, "swb-ota")

    private val bundlesDir: File
        get() = File(otaRootDir, "bundles")

    private val stagingDir: File
        get() = File(otaRootDir, "staging")

    private val tempDir: File
        get() = File(otaRootDir, "temporary")

    init {
        otaRootDir.mkdirs()
        bundlesDir.mkdirs()
        stagingDir.mkdirs()
        tempDir.mkdirs()
    }

    fun shutdown() {
        try {
            executor.shutdown()
        } catch (_: Throwable) {
        }
    }

    // --- CHECK FOR UPDATE ---

    @Throws(SwbUpdateException::class)
    fun checkForUpdate(
        config: SwbUpdaterConfig,
        onStateChange: ((UpdateStateEvent) -> Unit)? = null
    ): UpdateCheckResult {
        if (activator.isDevelopmentServer()) {
            throw SwbUpdateException(
                "Aplikasi berjalan dalam mode development (server.url aktif). Operasi OTA dinonaktifkan.",
                "DEVELOPMENT_MODE"
            )
        }

        validateConfig(config)
        val activeBundle = stateStore.getActiveBundle(config.baseBuild, config.baseVersion)

        emitEvent(onStateChange, UpdateStateEvent(UpdateState.CHECKING, "Memeriksa ketersediaan pembaruan..."))

        val manifest = try {
            fetchManifest(config.updateUrl, config.allowInsecureHttp)
        } catch (e: Throwable) {
            val errEvent = UpdateStateEvent(UpdateState.ERROR, "Gagal memeriksa pembaruan: ${e.message}")
            emitEvent(onStateChange, errEvent)
            throw SwbUpdateException("Gagal mengambil manifest pembaruan: ${e.message}", "NETWORK_ERROR", e)
        }

        val available = SwbManifest.isUpdateAvailable(manifest.build, activeBundle.build)

        if (available) {
            emitEvent(
                onStateChange,
                UpdateStateEvent(
                    UpdateState.AVAILABLE,
                    "Pembaruan build ${manifest.build} (versi ${manifest.version}) tersedia.",
                    build = manifest.build,
                    version = manifest.version
                )
            )
        } else {
            emitEvent(
                onStateChange,
                UpdateStateEvent(
                    UpdateState.UP_TO_DATE,
                    "Aplikasi sudah menggunakan versi bundle terbaru.",
                    build = activeBundle.build,
                    version = activeBundle.version
                )
            )
        }

        return UpdateCheckResult(
            available = available,
            currentBuild = activeBundle.build,
            remoteBuild = manifest.build,
            version = manifest.version,
            size = manifest.size
        )
    }

    // --- SYNC UPDATE ---

    @Throws(SwbUpdateException::class)
    fun sync(
        config: SwbUpdaterConfig,
        onStateChange: (UpdateStateEvent) -> Unit,
        onSuccess: (UpdateResult) -> Unit,
        onError: (SwbUpdateException) -> Unit
    ) {
        if (!isOperationInProgress.compareAndSet(false, true)) {
            val ex = SwbUpdateException("Operasi pembaruan lain sedang berjalan.", "UPDATE_IN_PROGRESS")
            onError(ex)
            return
        }

        executor.execute {
            var tempZipFile: File? = null
            try {
                if (activator.isDevelopmentServer()) {
                    throw SwbUpdateException(
                        "Aplikasi berjalan dalam mode development. Operasi OTA ditolak.",
                        "DEVELOPMENT_MODE"
                    )
                }

                validateConfig(config)
                val activeBundle = stateStore.getActiveBundle(config.baseBuild, config.baseVersion)

                emitEvent(onStateChange, UpdateStateEvent(UpdateState.CHECKING, "Memeriksa manifest pembaruan..."))
                val manifest = fetchManifest(config.updateUrl, config.allowInsecureHttp)

                val available = SwbManifest.isUpdateAvailable(manifest.build, activeBundle.build)
                if (!available) {
                    emitEvent(
                        onStateChange,
                        UpdateStateEvent(
                            UpdateState.UP_TO_DATE,
                            "Aplikasi sudah versi terbaru.",
                            build = activeBundle.build,
                            version = activeBundle.version
                        )
                    )
                    isOperationInProgress.set(false)
                    val result = UpdateResult(
                        updated = false,
                        fromBuild = activeBundle.build,
                        toBuild = activeBundle.build,
                        version = activeBundle.version
                    )
                    mainHandler.post { onSuccess(result) }
                    return@execute
                }

                // Download
                emitEvent(
                    onStateChange,
                    UpdateStateEvent(
                        UpdateState.DOWNLOADING,
                        "Mengunduh bundle OTA build ${manifest.build}...",
                        build = manifest.build,
                        version = manifest.version
                    )
                )

                tempZipFile = File(tempDir, "ota_${manifest.build}_${System.currentTimeMillis()}.zip")
                downloadAndVerify(
                    urlStr = manifest.url,
                    expectedChecksum = manifest.checksum,
                    expectedSize = manifest.size,
                    allowInsecureHttp = config.allowInsecureHttp,
                    destinationFile = tempZipFile,
                    onVerifying = {
                        emitEvent(
                            onStateChange,
                            UpdateStateEvent(
                                UpdateState.VERIFYING,
                                "Memverifikasi integritas checksum SHA-256...",
                                build = manifest.build,
                                version = manifest.version
                            )
                        )
                    }
                )

                // Install (Extract & Validate)
                emitEvent(
                    onStateChange,
                    UpdateStateEvent(
                        UpdateState.INSTALLING,
                        "Mengekstrak dan memasang bundle OTA...",
                        build = manifest.build,
                        version = manifest.version
                    )
                )

                tempZipFile.inputStream().use { inputStream ->
                    SwbZipInstaller.extractZip(inputStream, stagingDir)
                }

                val installedBundleDir = SwbZipInstaller.promoteStaging(stagingDir, bundlesDir, manifest.build)

                // Simpan state pending
                val pendingState = PendingUpdateState(
                    build = manifest.build,
                    version = manifest.version,
                    path = installedBundleDir.absolutePath,
                    pendingAt = System.currentTimeMillis()
                )
                stateStore.setPendingUpdate(pendingState)

                // Simpan state aktif
                val newState = BundleState(
                    build = manifest.build,
                    version = manifest.version,
                    path = installedBundleDir.absolutePath,
                    source = BundleSource.OTA
                )
                stateStore.setActiveBundle(newState)

                // Emit applying
                emitEvent(
                    onStateChange,
                    UpdateStateEvent(
                        UpdateState.APPLYING,
                        "Menerapkan pembaruan aset web...",
                        build = manifest.build,
                        version = manifest.version
                    )
                )

                // Selesaikan result untuk caller JS terlebih dahulu sebelum switch reload
                val updateResult = UpdateResult(
                    updated = true,
                    fromBuild = activeBundle.build,
                    toBuild = manifest.build,
                    version = manifest.version
                )

                isOperationInProgress.set(false)
                mainHandler.post {
                    onSuccess(updateResult)
                    // Emit applied setelah result diselesaikan dan state tersimpan
                    emitEvent(
                        onStateChange,
                        UpdateStateEvent(
                            UpdateState.APPLIED,
                            "Pembaruan build ${manifest.build} berhasil diterapkan.",
                            build = manifest.build,
                            version = manifest.version
                        )
                    )
                }

                // Jadwalkan aktivasi bundle switch
                activator.activateBundle(installedBundleDir.absolutePath) { success, error ->
                    if (!success && error != null) {
                        Log.e("SwbUpdater", "Gagal melakukan switch bundle WebView: ${error.message}", error)
                        emitEvent(
                            onStateChange,
                            UpdateStateEvent(UpdateState.ERROR, "Gagal mengaktifkan bundle pada WebView: ${error.message}")
                        )
                    }
                }

            } catch (e: Throwable) {
                isOperationInProgress.set(false)
                tempZipFile?.delete()
                stagingDir.deleteRecursively()

                val ex = if (e is SwbUpdateException) e else SwbUpdateException(e.message ?: "Update gagal", "UPDATE_FAILED", e)
                emitEvent(onStateChange, UpdateStateEvent(UpdateState.ERROR, "Pembaruan gagal: ${ex.message}"))
                mainHandler.post { onError(ex) }
            } finally {
                tempZipFile?.delete()
            }
        }
    }

    // --- RESET TO BUILTIN ---

    @Throws(SwbUpdateException::class)
    fun resetToBuiltin(
        config: SwbUpdaterConfig,
        onStateChange: (UpdateStateEvent) -> Unit,
        onSuccess: () -> Unit,
        onError: (SwbUpdateException) -> Unit
    ) {
        if (!isOperationInProgress.compareAndSet(false, true)) {
            val ex = SwbUpdateException("Operasi pembaruan lain sedang berjalan.", "UPDATE_IN_PROGRESS")
            onError(ex)
            return
        }

        executor.execute {
            try {
                if (activator.isDevelopmentServer()) {
                    throw SwbUpdateException("Aplikasi berjalan dalam mode development.", "DEVELOPMENT_MODE")
                }

                // Reset state store ke builtin
                stateStore.resetToBuiltin(config.baseBuild, config.baseVersion)

                emitEvent(
                    onStateChange,
                    UpdateStateEvent(
                        UpdateState.APPLYING,
                        "Mengembalikan aplikasi ke aset bawaan (builtin)..."
                    )
                )

                isOperationInProgress.set(false)
                mainHandler.post {
                    onSuccess()
                    emitEvent(
                        onStateChange,
                        UpdateStateEvent(
                            UpdateState.APPLIED,
                            "Aplikasi berhasil dikembalikan ke aset bawaan (builtin)."
                        )
                    )
                }

                // Jadwalkan reset pada activator
                activator.resetToBuiltin { success, error ->
                    if (!success && error != null) {
                        Log.e("SwbUpdater", "Gagal mereset bundle WebView: ${error.message}", error)
                    } else {
                        // Bersihkan folder bundles setelah switch
                        cleanupAllOtaBundles()
                    }
                }

            } catch (e: Throwable) {
                isOperationInProgress.set(false)
                val ex = if (e is SwbUpdateException) e else SwbUpdateException(e.message ?: "Reset gagal", "RESET_FAILED", e)
                emitEvent(onStateChange, UpdateStateEvent(UpdateState.ERROR, "Reset gagal: ${ex.message}"))
                mainHandler.post { onError(ex) }
            }
        }
    }

    // --- GET CURRENT BUNDLE ---

    fun getCurrentBundle(config: SwbUpdaterConfig): BundleState {
        return stateStore.getActiveBundle(config.baseBuild, config.baseVersion)
    }

    // --- STARTUP & RESUME SELF-HEALING ---

    fun handleStartupOrResume(
        config: SwbUpdaterConfig,
        onStateChange: ((UpdateStateEvent) -> Unit)? = null
    ) {
        if (activator.isDevelopmentServer()) {
            return
        }

        executor.execute {
            try {
                val currentBasePath = activator.getServerBasePath()
                val pending = stateStore.getPendingUpdate()

                // Self-healing pending update
                if (pending != null) {
                    val pendingFolder = File(pending.path)
                    if (currentBasePath == pending.path && pendingFolder.exists() && File(pendingFolder, "index.html").exists()) {
                        stateStore.setActiveBundle(
                            BundleState(
                                build = pending.build,
                                version = pending.version,
                                path = pending.path,
                                source = BundleSource.OTA
                            )
                        )
                        stateStore.clearPendingUpdate()
                    } else {
                        stateStore.clearPendingUpdate()
                    }
                }

                // Validasi bundle aktif saat ini
                val active = stateStore.getActiveBundle(config.baseBuild, config.baseVersion)
                if (active.source == BundleSource.OTA) {
                    val activeFolder = File(active.path)
                    val isFolderValid = activeFolder.exists() && File(activeFolder, "index.html").exists()
                    val isPathConsistent = currentBasePath == active.path

                    if (!isFolderValid || !isPathConsistent) {
                        // Native reload atau pembersihan Capacitor telah mereset serverBasePath
                        Log.w("SwbUpdater", "Terdeteksi inkonsistensi bundle OTA aktif. Melakukan self-healing ke builtin.")
                        stateStore.resetToBuiltin(config.baseBuild, config.baseVersion)
                    }
                }

                // Bersihkan bundle lama yang tidak aktif
                cleanupUnusedBundles(stateStore.getActiveBundle(config.baseBuild, config.baseVersion).build)

                // Auto-update jika diaktifkan dan belum dijalankan pada sesi ini
                if (config.autoUpdate && hasRunAutoUpdateSession.compareAndSet(false, true)) {
                    val validUrl = try {
                        val u = URL(config.updateUrl)
                        u.host.isNotBlank() && (u.protocol == "https" || (config.allowInsecureHttp && u.protocol == "http"))
                    } catch (_: Throwable) {
                        false
                    }

                    if (validUrl) {
                        sync(
                            config = config,
                            onStateChange = { onStateChange?.invoke(it) },
                            onSuccess = { Log.i("SwbUpdater", "Auto-update selesai.") },
                            onError = { Log.w("SwbUpdater", "Auto-update gagal: ${it.message}") }
                        )
                    }
                }
            } catch (t: Throwable) {
                Log.w("SwbUpdater", "Gagal memeriksa self-healing pada startup/resume: ${t.message}")
            }
        }
    }

    // --- CLEANUP ---

    private fun cleanupUnusedBundles(activeBuild: Int) {
        try {
            val list = bundlesDir.listFiles() ?: return
            for (file in list) {
                if (file.isDirectory && file.name != activeBuild.toString()) {
                    file.deleteRecursively()
                }
            }
            stagingDir.deleteRecursively()
            tempDir.deleteRecursively()
            stagingDir.mkdirs()
            tempDir.mkdirs()
        } catch (t: Throwable) {
            Log.w("SwbUpdater", "Pembersihan bundle lama gagal: ${t.message}")
        }
    }

    private fun cleanupAllOtaBundles() {
        try {
            bundlesDir.deleteRecursively()
            stagingDir.deleteRecursively()
            tempDir.deleteRecursively()
            bundlesDir.mkdirs()
            stagingDir.mkdirs()
            tempDir.mkdirs()
        } catch (_: Throwable) {
        }
    }

    // --- HTTP NETWORK HELPERS ---

    @Throws(IOException::class, IllegalArgumentException::class)
    private fun fetchManifest(urlStr: String, allowInsecureHttp: Boolean): SwbManifest {
        var currentUrl = urlStr
        var redirects = 0
        val maxRedirects = 3

        while (true) {
            val url = URL(currentUrl)
            val protocol = url.protocol.lowercase()
            if (protocol != "https" && (!allowInsecureHttp || protocol != "http")) {
                throw SecurityException("Protokol URL manifest tidak aman: $protocol")
            }

            val conn = url.openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 10000
            conn.readTimeout = 15000
            conn.requestMethod = "GET"

            try {
                val code = conn.responseCode
                if (code in 300..399) {
                    redirects++
                    if (redirects > maxRedirects) {
                        throw IOException("Terlalu banyak redirect saat mengambil manifest.")
                    }
                    val location = conn.getHeaderField("Location")
                        ?: throw IOException("Header 'Location' tidak ditemukan pada respons redirect HTTP $code.")
                    val targetUrl = URL(url, location)

                    if (url.protocol.lowercase() == "https" && targetUrl.protocol.lowercase() == "http") {
                        throw SecurityException("Downgrade protokol HTTPS ke HTTP pada redirect ditolak.")
                    }
                    currentUrl = targetUrl.toString()
                    continue
                }

                if (code != HttpURLConnection.HTTP_OK) {
                    throw IOException("Server mengembalikan status HTTP $code saat mengambil manifest.")
                }

                val jsonContent = conn.inputStream.use { input ->
                    readStreamWithLimit(input, 1024 * 1024) // Maks 1 MB
                }

                return SwbManifest.parseAndValidate(jsonContent, allowInsecureHttp)
            } finally {
                conn.disconnect()
            }
        }
    }

    @Throws(IOException::class, SecurityException::class, IllegalStateException::class)
    private fun downloadAndVerify(
        urlStr: String,
        expectedChecksum: String,
        expectedSize: Long?,
        allowInsecureHttp: Boolean,
        destinationFile: File,
        onVerifying: () -> Unit
    ) {
        var currentUrl = urlStr
        var redirects = 0
        val maxRedirects = 3

        while (true) {
            val url = URL(currentUrl)
            val protocol = url.protocol.lowercase()
            if (protocol != "https" && (!allowInsecureHttp || protocol != "http")) {
                throw SecurityException("Protokol URL download tidak diizinkan: $protocol")
            }

            val conn = url.openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 10000
            conn.readTimeout = 15000
            conn.requestMethod = "GET"

            try {
                val code = conn.responseCode
                if (code in 300..399) {
                    redirects++
                    if (redirects > maxRedirects) {
                        throw IOException("Terlalu banyak redirect saat mengunduh bundle ZIP.")
                    }
                    val location = conn.getHeaderField("Location")
                        ?: throw IOException("Header 'Location' tidak ditemukan pada respons redirect HTTP $code.")
                    val targetUrl = URL(url, location)

                    if (url.protocol.lowercase() == "https" && targetUrl.protocol.lowercase() == "http") {
                        throw SecurityException("Downgrade protokol HTTPS ke HTTP pada redirect ditolak.")
                    }
                    currentUrl = targetUrl.toString()
                    continue
                }

                if (code != HttpURLConnection.HTTP_OK) {
                    throw IOException("Server mengembalikan status HTTP $code saat mengunduh bundle.")
                }

                val digest = MessageDigest.getInstance("SHA-256")
                var totalBytes: Long = 0
                val maxDownloadSize = 50 * 1024 * 1024L // 50 MB

                FileOutputStream(destinationFile).use { fos ->
                    conn.inputStream.use { input ->
                        val buffer = ByteArray(8192)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            totalBytes += read
                            if (totalBytes > maxDownloadSize) {
                                throw SecurityException("Ukuran file unduhan melebihi batas maksimum 50 MB.")
                            }
                            digest.update(buffer, 0, read)
                            fos.write(buffer, 0, read)
                        }
                        fos.flush()
                    }
                }

                if (expectedSize != null && totalBytes != expectedSize) {
                    destinationFile.delete()
                    throw IllegalStateException("Ukuran unduhan ($totalBytes byte) tidak cocok dengan ukuran manifest ($expectedSize byte).")
                }

                onVerifying()

                val computedChecksum = digest.digest().joinToString("") { "%02x".format(it) }
                if (!computedChecksum.equals(expectedChecksum, ignoreCase = true)) {
                    destinationFile.delete()
                    throw IllegalStateException("Verifikasi checksum SHA-256 bundle gagal. Harap pastikan keaslian bundle.")
                }

                return
            } finally {
                conn.disconnect()
            }
        }
    }

    private fun readStreamWithLimit(input: InputStream, maxBytes: Int): String {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        var total = 0
        var read: Int
        while (input.read(buffer).also { read = it } != -1) {
            total += read
            if (total > maxBytes) {
                throw IOException("Ukuran pembacaan data melebihi batas aman ($maxBytes bytes).")
            }
            out.write(buffer, 0, read)
        }
        return out.toString("UTF-8")
    }

    private fun validateConfig(config: SwbUpdaterConfig) {
        if (config.updateUrl.isBlank()) {
            throw SwbUpdateException("Konfigurasi 'updateUrl' wajib disediakan.", "INVALID_CONFIG")
        }
        val url = try {
            URL(config.updateUrl)
        } catch (e: Throwable) {
            throw SwbUpdateException("Format 'updateUrl' tidak valid: ${e.message}", "INVALID_CONFIG", e)
        }
        val protocol = url.protocol.lowercase()
        if (protocol != "https" && (!config.allowInsecureHttp || protocol != "http")) {
            throw SwbUpdateException("URL pembaruan wajib menggunakan protokol HTTPS.", "INSECURE_URL")
        }
        if (url.host.isNullOrBlank()) {
            throw SwbUpdateException("URL pembaruan tidak memiliki host valid.", "INVALID_CONFIG")
        }
    }

    private fun emitEvent(listener: ((UpdateStateEvent) -> Unit)?, event: UpdateStateEvent) {
        if (listener == null) return
        mainHandler.post {
            listener.invoke(event)
        }
    }
}

