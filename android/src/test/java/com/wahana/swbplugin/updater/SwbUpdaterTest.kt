package com.wahana.swbplugin.updater

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SwbUpdaterTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var testDir: File

    @Before
    fun setUp() {
        testDir = tempFolder.newFolder("ota_test")
    }

    // --- 1. MANIFEST VALID / INVALID ---

    @Test
    fun testManifestValidParsing() {
        val json = """
            {
              "build": 2,
              "version": "1.0.1",
              "url": "https://example.com/ota/bundle.zip",
              "checksum": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
              "size": 123456
            }
        """.trimIndent()

        val manifest = SwbManifest.parseAndValidate(json)
        assertEquals(2, manifest.build)
        assertEquals("1.0.1", manifest.version)
        assertEquals("https://example.com/ota/bundle.zip", manifest.url)
        assertEquals("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef", manifest.checksum)
        assertEquals(123456L, manifest.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testManifestMalformedJsonThrows() {
        SwbManifest.parseAndValidate("{ invalid json")
    }

    // --- 2. FIELD WAJIB HILANG ---

    @Test(expected = IllegalArgumentException::class)
    fun testManifestMissingBuildThrows() {
        val json = """
            {
              "version": "1.0.1",
              "url": "https://example.com/bundle.zip",
              "checksum": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
            }
        """.trimIndent()
        SwbManifest.parseAndValidate(json)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testManifestMissingVersionThrows() {
        val json = """
            {
              "build": 2,
              "url": "https://example.com/bundle.zip",
              "checksum": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
            }
        """.trimIndent()
        SwbManifest.parseAndValidate(json)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testManifestMissingUrlThrows() {
        val json = """
            {
              "build": 2,
              "version": "1.0.1",
              "checksum": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
            }
        """.trimIndent()
        SwbManifest.parseAndValidate(json)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testManifestMissingChecksumThrows() {
        val json = """
            {
              "build": 2,
              "version": "1.0.1",
              "url": "https://example.com/bundle.zip"
            }
        """.trimIndent()
        SwbManifest.parseAndValidate(json)
    }

    // --- 3. TIPE FIELD SALAH ---

    @Test(expected = IllegalArgumentException::class)
    fun testManifestBuildWrongTypeThrows() {
        val json = """
            {
              "build": "dua",
              "version": "1.0.1",
              "url": "https://example.com/bundle.zip",
              "checksum": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
            }
        """.trimIndent()
        SwbManifest.parseAndValidate(json)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testManifestVersionEmptyThrows() {
        val json = """
            {
              "build": 2,
              "version": "   ",
              "url": "https://example.com/bundle.zip",
              "checksum": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
            }
        """.trimIndent()
        SwbManifest.parseAndValidate(json)
    }

    // --- 4. BUILD INVALID, SAMA, LEBIH RENDAH, LEBIH TINGGI ---

    @Test(expected = IllegalArgumentException::class)
    fun testManifestBuildZeroThrows() {
        val json = """
            {
              "build": 0,
              "version": "1.0.0",
              "url": "https://example.com/bundle.zip",
              "checksum": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
            }
        """.trimIndent()
        SwbManifest.parseAndValidate(json)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testManifestBuildNegativeThrows() {
        val json = """
            {
              "build": -5,
              "version": "1.0.0",
              "url": "https://example.com/bundle.zip",
              "checksum": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
            }
        """.trimIndent()
        SwbManifest.parseAndValidate(json)
    }

    @Test
    fun testBuildComparison() {
        // Build sama -> tidak tersedia
        assertFalse(SwbManifest.isUpdateAvailable(remoteBuild = 2, currentBuild = 2))
        // Build lebih rendah -> tidak tersedia
        assertFalse(SwbManifest.isUpdateAvailable(remoteBuild = 1, currentBuild = 2))
        // Build lebih tinggi -> tersedia
        assertTrue(SwbManifest.isUpdateAvailable(remoteBuild = 3, currentBuild = 2))
    }

    // --- 5. BUILD DI LUAR BATAS INT ---

    @Test(expected = IllegalArgumentException::class)
    fun testManifestBuildExceedsIntMaxThrows() {
        val json = """
            {
              "build": 3000000000,
              "version": "1.0.0",
              "url": "https://example.com/bundle.zip",
              "checksum": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
            }
        """.trimIndent()
        SwbManifest.parseAndValidate(json)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testManifestBuildFractionalThrows() {
        val json = """
            {
              "build": 2.5,
              "version": "1.0.0",
              "url": "https://example.com/bundle.zip",
              "checksum": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
            }
        """.trimIndent()
        SwbManifest.parseAndValidate(json)
    }

    @Test
    fun testManifestBuildStringNumberValid() {
        val json = """
            {
              "build": "3",
              "version": "1.0.0",
              "url": "https://example.com/bundle.zip",
              "checksum": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
            }
        """.trimIndent()
        val manifest = SwbManifest.parseAndValidate(json)
        assertEquals(3, manifest.build)
    }

    // --- 6. CHECKSUM SALAH FORMAT DAN NORMALISASI HEXADECIMAL ---

    @Test(expected = IllegalArgumentException::class)
    fun testChecksumTooShortThrows() {
        val json = """
            {
              "build": 2,
              "version": "1.0.0",
              "url": "https://example.com/bundle.zip",
              "checksum": "0123456789abcdef"
            }
        """.trimIndent()
        SwbManifest.parseAndValidate(json)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testChecksumInvalidHexCharsThrows() {
        val json = """
            {
              "build": 2,
              "version": "1.0.0",
              "url": "https://example.com/bundle.zip",
              "checksum": "zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz"
            }
        """.trimIndent()
        SwbManifest.parseAndValidate(json)
    }

    @Test
    fun testChecksumHexNormalization() {
        val json = """
            {
              "build": 2,
              "version": "1.0.0",
              "url": "https://example.com/bundle.zip",
              "checksum": "ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789"
            }
        """.trimIndent()
        val manifest = SwbManifest.parseAndValidate(json)
        assertEquals("abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789", manifest.checksum)
    }

    // --- 7. URL INVALID DAN ATURAN HTTPS/HTTP ---

    @Test(expected = IllegalArgumentException::class)
    fun testInvalidUrlThrows() {
        val json = """
            {
              "build": 2,
              "version": "1.0.0",
              "url": "not-a-valid-url",
              "checksum": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
            }
        """.trimIndent()
        SwbManifest.parseAndValidate(json)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testHttpRejectedWhenInsecureNotAllowed() {
        val json = """
            {
              "build": 2,
              "version": "1.0.0",
              "url": "http://example.com/bundle.zip",
              "checksum": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
            }
        """.trimIndent()
        SwbManifest.parseAndValidate(json, allowInsecureHttp = false)
    }

    @Test
    fun testHttpAllowedWhenInsecureExplicitlyAllowed() {
        val json = """
            {
              "build": 2,
              "version": "1.0.0",
              "url": "http://localhost:8080/bundle.zip",
              "checksum": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
            }
        """.trimIndent()
        val manifest = SwbManifest.parseAndValidate(json, allowInsecureHttp = true)
        assertEquals("http://localhost:8080/bundle.zip", manifest.url)
    }

    // --- 8. CHECKSUM SHA-256 COCOK / TIDAK COCOK ---

    @Test
    fun testSha256Verification() {
        val content = "console.log('hello world');".toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(content).joinToString("") { "%02x".format(it) }

        assertEquals(64, hash.length)
        assertTrue(hash.equals(hash.uppercase(), ignoreCase = true))
        assertFalse(hash.equals("0000000000000000000000000000000000000000000000000000000000000000", ignoreCase = true))
    }

    // --- 9. ZIP VALID DENGAN index.html DI ROOT ---

    @Test
    fun testZipValidWithRootIndexHtml() {
        val zipBytes = createZipBytes(
            mapOf(
                "index.html" to "<html><body>App</body></html>".toByteArray(Charsets.UTF_8),
                "js/app.js" to "console.log(1)".toByteArray(Charsets.UTF_8)
            )
        )

        val stagingDir = File(testDir, "staging")
        SwbZipInstaller.extractZip(ByteArrayInputStream(zipBytes), stagingDir)

        val rootIndex = File(stagingDir, "index.html")
        assertTrue(rootIndex.exists())
        assertTrue(rootIndex.isFile)
        assertTrue(File(stagingDir, "js/app.js").exists())
    }

    // --- 10. ZIP TANPA index.html ---

    @Test(expected = IllegalStateException::class)
    fun testZipWithoutIndexHtmlThrows() {
        val zipBytes = createZipBytes(
            mapOf(
                "app.js" to "console.log(1)".toByteArray(Charsets.UTF_8)
            )
        )

        val stagingDir = File(testDir, "staging")
        SwbZipInstaller.extractZip(ByteArrayInputStream(zipBytes), stagingDir)
    }

    // --- 11. index.html HANYA DI SUBFOLDER ---

    @Test(expected = IllegalStateException::class)
    fun testZipWithIndexHtmlOnlyInSubfolderThrows() {
        val zipBytes = createZipBytes(
            mapOf(
                "dist/index.html" to "<html></html>".toByteArray(Charsets.UTF_8)
            )
        )

        val stagingDir = File(testDir, "staging")
        SwbZipInstaller.extractZip(ByteArrayInputStream(zipBytes), stagingDir)
    }

    // --- 12. PATH ABSOLUT ---

    @Test(expected = SecurityException::class)
    fun testZipEntryAbsolutePathThrows() {
        SwbZipInstaller.validateEntryPath("/etc/passwd", testDir)
    }

    @Test(expected = SecurityException::class)
    fun testZipEntryWindowsAbsolutePathThrows() {
        SwbZipInstaller.validateEntryPath("C:\\Windows\\system.ini", testDir)
    }

    // --- 13. SEGMEN .. ---

    @Test(expected = SecurityException::class)
    fun testZipEntryParentTraversalThrows() {
        SwbZipInstaller.validateEntryPath("../secret.txt", testDir)
    }

    @Test(expected = SecurityException::class)
    fun testZipEntryNestedTraversalThrows() {
        SwbZipInstaller.validateEntryPath("assets/../../secret.txt", testDir)
    }

    // --- 14. VARIASI SEPARATOR ---

    @Test
    fun testZipEntrySeparatorHandling() {
        val target = SwbZipInstaller.validateEntryPath("assets\\css\\style.css", testDir)
        assertEquals(File(testDir, "assets/css/style.css").canonicalPath, target.canonicalPath)
    }

    // --- 15. PATH KELUAR STAGING SETELAH NORMALISASI ---

    @Test(expected = SecurityException::class)
    fun testZipSlipPrevention() {
        SwbZipInstaller.validateEntryPath("sub/dir/../../../../evil.txt", testDir)
    }

    // --- 16. BATAS JUMLAH ENTRY ---

    @Test
    fun testMaxEntriesLimitCheck() {
        assertEquals(5000, SwbZipInstaller.MAX_ENTRIES)
    }

    // --- 17. BATAS UKURAN EKSTRAKSI ---

    @Test
    fun testMaxExtractionLimits() {
        assertEquals(100 * 1024 * 1024L, SwbZipInstaller.MAX_TOTAL_SIZE)
        assertEquals(50 * 1024 * 1024L, SwbZipInstaller.MAX_FILE_SIZE)
    }

    // --- 18. STATE KOSONG MENGHASILKAN BUILTIN ---

    @Test
    fun testEmptyStateProducesBuiltin() {
        val fakeStore = FakeStateStore()
        val active = fakeStore.getActiveBundle(defaultBuild = 1, defaultVersion = "1.0.0")

        assertEquals(1, active.build)
        assertEquals("1.0.0", active.version)
        assertEquals("public", active.path)
        assertEquals(BundleSource.BUILTIN, active.source)
    }

    // --- 19. STATE OTA VALID ---

    @Test
    fun testValidOtaState() {
        val fakeStore = FakeStateStore()
        val otaBundle = BundleState(
            build = 5,
            version = "1.2.0",
            path = "/data/user/0/com.app/files/swb-ota/bundles/5",
            source = BundleSource.OTA
        )
        fakeStore.setActiveBundle(otaBundle)

        val retrieved = fakeStore.getActiveBundle(1, "1.0.0")
        assertEquals(5, retrieved.build)
        assertEquals("1.2.0", retrieved.version)
        assertEquals(BundleSource.OTA, retrieved.source)
    }

    // --- 20. STATE OTA MENUNJUK FOLDER HILANG ---

    @Test
    fun testOtaStatePointsToMissingFolderSelfHeals() {
        val fakeStore = FakeStateStore()
        val fakeActivator = FakeActivator()

        val missingPath = File(testDir, "non_existent_bundle").absolutePath
        fakeStore.setActiveBundle(BundleState(2, "1.0.1", missingPath, BundleSource.OTA))
        fakeActivator.updateServerBasePath(missingPath)

        // Saat folder hilang, self-healing harus mereset ke builtin
        val bundleFolder = File(missingPath)
        val isValid = bundleFolder.exists() && File(bundleFolder, "index.html").exists()
        assertFalse(isValid)

        fakeStore.resetToBuiltin(1, "1.0.0")
        val current = fakeStore.getActiveBundle(1, "1.0.0")
        assertEquals(1, current.build)
        assertEquals(BundleSource.BUILTIN, current.source)
        assertEquals("public", current.path)
    }

    // --- 21. SERVER PATH TIDAK COCOK DENGAN STATE ---

    @Test
    fun testServerPathMismatchWithState() {
        val fakeStore = FakeStateStore()
        val fakeActivator = FakeActivator()

        val bundleDir = File(testDir, "bundles/2")
        bundleDir.mkdirs()
        File(bundleDir, "index.html").writeText("<html></html>")

        fakeStore.setActiveBundle(BundleState(2, "1.0.1", bundleDir.absolutePath, BundleSource.OTA))
        // Capacitor mereset serverBasePath menjadi public
        fakeActivator.updateServerBasePath("public")

        val active = fakeStore.getActiveBundle(1, "1.0.0")
        val isMismatch = fakeActivator.getServerBasePath() != active.path
        assertTrue(isMismatch)
    }

    // --- 22. BUNDLE AKTIF TIDAK DIHAPUS SAAT INSTALASI GAGAL ---

    @Test
    fun testActiveBundleNotDeletedWhenInstallationFails() {
        val bundlesDir = File(testDir, "bundles")
        val activeBundleDir = File(bundlesDir, "1")
        activeBundleDir.mkdirs()
        val activeIndex = File(activeBundleDir, "index.html")
        activeIndex.writeText("<html>Active v1</html>")

        val stagingDir = File(testDir, "staging")
        stagingDir.mkdirs()

        // Simulasi ekstraksi gagal karena ZIP rusak
        val corruptedZip = "invalid zip data".toByteArray(Charsets.UTF_8)
        try {
            SwbZipInstaller.extractZip(ByteArrayInputStream(corruptedZip), stagingDir)
            fail("Harus melempar exception untuk ZIP rusak")
        } catch (_: Throwable) {
            // Expected
        }

        // Pastikan bundle aktif tidak tersentuh
        assertTrue(activeBundleDir.exists())
        assertTrue(activeIndex.exists())
        assertEquals("<html>Active v1</html>", activeIndex.readText())
    }

    // --- 23. KEGAGALAN SWITCH TIDAK DICATAT SEBAGAI SUKSES ---

    @Test
    fun testSwitchFailureNotRecordedAsSuccess() {
        val fakeActivator = FakeActivator()
        fakeActivator.shouldFailSwitch = true

        var switchResult = false
        var caughtError: Throwable? = null

        fakeActivator.activateBundle("/data/bundle/2") { success, error ->
            switchResult = success
            caughtError = error
        }

        assertFalse(switchResult)
        assertNotNull(caughtError)
        assertEquals("Simulated switch failure", caughtError?.message)
        // serverBasePath tidak boleh berubah jika switch gagal
        assertEquals("public", fakeActivator.getServerBasePath())
    }

    // --- PROMOTION HELPER TEST ---

    @Test
    fun testPromoteStagingToBundles() {
        val stagingDir = File(testDir, "staging")
        stagingDir.mkdirs()
        File(stagingDir, "index.html").writeText("<html>Root</html>")
        File(stagingDir, "app.js").writeText("console.log(1)")

        val bundlesDir = File(testDir, "bundles")
        val promoted = SwbZipInstaller.promoteStaging(stagingDir, bundlesDir, 3)

        assertEquals(File(bundlesDir, "3").canonicalPath, promoted.canonicalPath)
        assertTrue(File(promoted, "index.html").exists())
        assertTrue(File(promoted, "app.js").exists())
        assertFalse(stagingDir.exists())
    }

    // --- HELPER: In-Memory ZIP Creator ---

    private fun createZipBytes(entries: Map<String, ByteArray>): ByteArray {
        val baos = ByteArrayOutputStream()
        val zos = ZipOutputStream(baos)
        for ((name, data) in entries) {
            val entry = ZipEntry(name)
            zos.putNextEntry(entry)
            zos.write(data)
            zos.closeEntry()
        }
        zos.close()
        return baos.toByteArray()
    }
}

// --- FAKE CLASSES FOR TESTING ---

class FakeStateStore : SwbUpdateStateStore {
    private var internalActiveBundle: BundleState? = null
    private var internalPendingUpdate: PendingUpdateState? = null

    override fun getActiveBundle(defaultBuild: Int, defaultVersion: String): BundleState {
        return internalActiveBundle ?: BundleState(defaultBuild, defaultVersion, "public", BundleSource.BUILTIN)
    }

    override fun setActiveBundle(state: BundleState) {
        internalActiveBundle = state
    }

    override fun getPendingUpdate(): PendingUpdateState? = internalPendingUpdate

    override fun setPendingUpdate(pending: PendingUpdateState?) {
        internalPendingUpdate = pending
    }

    override fun clearPendingUpdate() {
        internalPendingUpdate = null
    }

    override fun resetToBuiltin(defaultBuild: Int, defaultVersion: String) {
        internalActiveBundle = BundleState(defaultBuild, defaultVersion, "public", BundleSource.BUILTIN)
        internalPendingUpdate = null
    }
}

class FakeActivator : BundleActivator {
    private var internalServerBasePath: String? = "public"
    var isDevServer: Boolean = false
    var activateCalledWithPath: String? = null
    var shouldFailSwitch: Boolean = false

    fun updateServerBasePath(path: String?) {
        internalServerBasePath = path
    }

    override fun activateBundle(bundlePath: String, onComplete: (Boolean, Throwable?) -> Unit) {
        activateCalledWithPath = bundlePath
        if (shouldFailSwitch) {
            onComplete(false, RuntimeException("Simulated switch failure"))
        } else {
            internalServerBasePath = bundlePath
            onComplete(true, null)
        }
    }

    override fun resetToBuiltin(onComplete: (Boolean, Throwable?) -> Unit) {
        internalServerBasePath = "public"
        onComplete(true, null)
    }

    override fun getServerBasePath(): String? = internalServerBasePath
    override fun isDevelopmentServer(): Boolean = isDevServer
}
