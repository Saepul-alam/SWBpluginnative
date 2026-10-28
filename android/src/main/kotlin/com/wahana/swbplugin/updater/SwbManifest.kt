package com.wahana.swbplugin.updater

import org.json.JSONException
import org.json.JSONObject
import java.net.MalformedURLException
import java.net.URL

data class SwbManifest(
    val build: Int,
    val version: String,
    val url: String,
    val checksum: String,
    val size: Long? = null
) {
    companion object {
        private val SHA256_HEX_REGEX = Regex("^[0-9a-fA-F]{64}$")

        @Throws(IllegalArgumentException::class, JSONException::class)
        fun parseAndValidate(jsonString: String, allowInsecureHttp: Boolean = false): SwbManifest {
            if (jsonString.isBlank()) {
                throw IllegalArgumentException("Data manifest JSON kosong.")
            }

            val json = try {
                JSONObject(jsonString)
            } catch (e: JSONException) {
                throw IllegalArgumentException("Format JSON manifest tidak valid: ${e.message}", e)
            }

            // 1. Validasi build
            if (!json.has("build")) {
                throw IllegalArgumentException("Field 'build' wajib diisi dalam manifest.")
            }
            val rawBuild = json.get("build")
            val build: Int = when (rawBuild) {
                is Int -> rawBuild
                is Long -> {
                    if (rawBuild > Int.MAX_VALUE || rawBuild < Int.MIN_VALUE) {
                        throw IllegalArgumentException("Nilai 'build' di luar batas integer ($rawBuild).")
                    }
                    rawBuild.toInt()
                }
                is Number -> {
                    val d = rawBuild.toDouble()
                    if (d.isNaN() || d.isInfinite() || d != Math.floor(d)) {
                        throw IllegalArgumentException("Nilai 'build' harus berupa bilangan bulat integer.")
                    }
                    if (d > Int.MAX_VALUE || d < Int.MIN_VALUE) {
                        throw IllegalArgumentException("Nilai 'build' di luar batas integer ($rawBuild).")
                    }
                    rawBuild.toInt()
                }
                is String -> {
                    val trimmed = rawBuild.trim()
                    try {
                        val parsed = trimmed.toLong()
                        if (parsed > Int.MAX_VALUE || parsed < Int.MIN_VALUE) {
                            throw IllegalArgumentException("Nilai 'build' di luar batas integer ($rawBuild).")
                        }
                        parsed.toInt()
                    } catch (e: NumberFormatException) {
                        throw IllegalArgumentException("Tipe data field 'build' harus berupa integer.")
                    }
                }
                else -> throw IllegalArgumentException("Tipe data field 'build' harus berupa integer.")
            }

            if (build <= 0) {
                throw IllegalArgumentException("Nilai 'build' harus merupakan integer positif (1..2147483647).")
            }

            // 2. Validasi version
            if (!json.has("version")) {
                throw IllegalArgumentException("Field 'version' wajib diisi dalam manifest.")
            }
            val rawVersion = json.get("version")
            if (rawVersion !is String || rawVersion.trim().isEmpty()) {
                throw IllegalArgumentException("Field 'version' wajib berupa teks (string) tidak kosong.")
            }
            val version = rawVersion.trim()

            // 3. Validasi url
            if (!json.has("url")) {
                throw IllegalArgumentException("Field 'url' wajib diisi dalam manifest.")
            }
            val rawUrl = json.get("url")
            if (rawUrl !is String || rawUrl.trim().isEmpty()) {
                throw IllegalArgumentException("Field 'url' wajib berupa teks URL tidak kosong.")
            }
            val urlStr = rawUrl.trim()
            val parsedUrl = try {
                URL(urlStr)
            } catch (e: MalformedURLException) {
                throw IllegalArgumentException("URL bundle tidak valid: ${e.message}", e)
            }

            val protocol = parsedUrl.protocol.lowercase()
            if (protocol != "https" && (!allowInsecureHttp || protocol != "http")) {
                throw IllegalArgumentException("URL bundle wajib menggunakan protokol HTTPS yang aman.")
            }

            if (parsedUrl.host.isNullOrBlank()) {
                throw IllegalArgumentException("URL bundle wajib memiliki host yang valid.")
            }

            // 4. Validasi checksum
            if (!json.has("checksum")) {
                throw IllegalArgumentException("Field 'checksum' wajib diisi dalam manifest.")
            }
            val rawChecksum = json.get("checksum")
            if (rawChecksum !is String || rawChecksum.trim().isEmpty()) {
                throw IllegalArgumentException("Field 'checksum' wajib berupa string SHA-256 hex.")
            }
            val checksumTrimmed = rawChecksum.trim()
            if (!SHA256_HEX_REGEX.matches(checksumTrimmed)) {
                throw IllegalArgumentException("Field 'checksum' harus berupa 64 karakter heksadesimal SHA-256.")
            }
            val normalizedChecksum = checksumTrimmed.lowercase()

            // 5. Validasi size (opsional)
            var size: Long? = null
            if (json.has("size") && !json.isNull("size")) {
                val rawSize = json.get("size")
                size = when (rawSize) {
                    is Number -> {
                        val s = rawSize.toLong()
                        if (s < 0) {
                            throw IllegalArgumentException("Field 'size' tidak boleh bernilai negatif.")
                        }
                        s
                    }
                    else -> throw IllegalArgumentException("Field 'size' harus berupa angka ukuran byte.")
                }
            }

            return SwbManifest(
                build = build,
                version = version,
                url = urlStr,
                checksum = normalizedChecksum,
                size = size
            )
        }

        fun isUpdateAvailable(remoteBuild: Int, currentBuild: Int): Boolean {
            return remoteBuild > currentBuild
        }
    }
}

