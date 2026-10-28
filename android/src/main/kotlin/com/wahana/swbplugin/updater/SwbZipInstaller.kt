package com.wahana.swbplugin.updater

import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

object SwbZipInstaller {

    const val MAX_ENTRIES = 5000
    const val MAX_TOTAL_SIZE = 100 * 1024 * 1024L // 100 MB
    const val MAX_FILE_SIZE = 50 * 1024 * 1024L   // 50 MB
    private const val BUFFER_SIZE = 8192

    @Throws(SecurityException::class)
    fun validateEntryPath(entryName: String, destinationDir: File): File {
        if (entryName.isBlank()) {
            throw SecurityException("Nama entry dalam file ZIP tidak boleh kosong.")
        }

        // Normalisasi separator backslash ke slash
        val normalized = entryName.replace('\\', '/')

        // Tolak path absolut Unix (/...) atau Windows drive (C:...)
        if (normalized.startsWith("/") || (normalized.length >= 2 && normalized[1] == ':')) {
            throw SecurityException("ZIP entry mengandung path absolut yang dilarang: $entryName")
        }

        // Tolak path traversal '..'
        val segments = normalized.split('/')
        for (segment in segments) {
            if (segment == "..") {
                throw SecurityException("ZIP entry mengandung traversal '..' yang dilarang: $entryName")
            }
        }

        val targetFile = File(destinationDir, normalized)
        val canonicalTarget = targetFile.canonicalPath
        val canonicalDest = destinationDir.canonicalPath

        if (!canonicalTarget.startsWith(canonicalDest + File.separator) && canonicalTarget != canonicalDest) {
            throw SecurityException("ZIP entry melarikan diri (Zip-Slip) ke luar direktori target: $entryName")
        }

        return targetFile
    }

    @Throws(IOException::class, SecurityException::class, IllegalStateException::class)
    fun extractZip(inputStream: InputStream, stagingDir: File) {
        if (stagingDir.exists()) {
            stagingDir.deleteRecursively()
        }
        if (!stagingDir.mkdirs() && !stagingDir.exists()) {
            throw IOException("Gagal membuat direktori staging: ${stagingDir.absolutePath}")
        }

        try {
            val zis = ZipInputStream(inputStream)
            var totalBytes: Long = 0
            var entryCount = 0
            var entry: ZipEntry? = zis.nextEntry

            while (entry != null) {
                entryCount++
                if (entryCount > MAX_ENTRIES) {
                    throw SecurityException("Jumlah file dalam ZIP melebihi batas keamanan maksimum ($MAX_ENTRIES file).")
                }

                val targetFile = validateEntryPath(entry.name, stagingDir)

                if (entry.isDirectory || entry.name.endsWith("/")) {
                    if (!targetFile.exists() && !targetFile.mkdirs() && !targetFile.isDirectory) {
                        throw IOException("Gagal membuat direktori dari ZIP: ${targetFile.absolutePath}")
                    }
                } else {
                    val parent = targetFile.parentFile
                    if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.isDirectory) {
                        throw IOException("Gagal membuat subdirektori parent: ${parent.absolutePath}")
                    }

                    var fileBytes: Long = 0
                    FileOutputStream(targetFile).use { fos ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var read: Int
                        while (zis.read(buffer).also { read = it } != -1) {
                            fileBytes += read
                            totalBytes += read

                            if (fileBytes > MAX_FILE_SIZE) {
                                throw SecurityException("Ukuran satu file ZIP melebihi batas maksimum (50 MB).")
                            }
                            if (totalBytes > MAX_TOTAL_SIZE) {
                                throw SecurityException("Total ukuran dekompresi ZIP melebihi batas maksimum (100 MB).")
                            }

                            fos.write(buffer, 0, read)
                        }
                        fos.flush()
                    }
                }

                zis.closeEntry()
                entry = zis.nextEntry
            }

            // Validasi wajib index.html di root staging
            val rootIndexHtml = File(stagingDir, "index.html")
            if (!rootIndexHtml.exists() || !rootIndexHtml.isFile) {
                throw IllegalStateException("Bundle OTA tidak valid: File 'index.html' tidak ditemukan di root direktori ZIP.")
            }
        } catch (e: Throwable) {
            // Bersihkan staging jika ekstraksi gagal
            stagingDir.deleteRecursively()
            throw e
        }
    }

    @Throws(IOException::class, IllegalStateException::class)
    fun promoteStaging(stagingDir: File, bundlesDir: File, buildNumber: Int): File {
        if (!bundlesDir.exists()) {
            bundlesDir.mkdirs()
        }

        val targetBundleDir = File(bundlesDir, buildNumber.toString())
        if (targetBundleDir.exists()) {
            targetBundleDir.deleteRecursively()
        }

        val moved = stagingDir.renameTo(targetBundleDir)
        if (!moved) {
            // Fallback copy jika rename across filesystem gagal
            copyDirectory(stagingDir, targetBundleDir)
            stagingDir.deleteRecursively()
        }

        val targetIndexHtml = File(targetBundleDir, "index.html")
        if (!targetIndexHtml.exists() || !targetIndexHtml.isFile) {
            targetBundleDir.deleteRecursively()
            throw IllegalStateException("Gagal memvalidasi instalasi bundle: 'index.html' tidak ditemukan di bundle terpasang.")
        }

        return targetBundleDir
    }

    private fun copyDirectory(source: File, destination: File) {
        if (!destination.exists() && !destination.mkdirs()) {
            throw IOException("Gagal membuat direktori: ${destination.absolutePath}")
        }
        val files = source.listFiles() ?: return
        for (file in files) {
            val destFile = File(destination, file.name)
            if (file.isDirectory) {
                copyDirectory(file, destFile)
            } else {
                file.inputStream().use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }
}

