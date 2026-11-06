package com.wahana.swbplugin.updater

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class UserContext(
    val userId: String? = null,
    val branch: String? = null,
    val role: String? = null
)

class SwbFeatureManager(
    private val stateStore: SwbUpdateStateStore
) {

    /**
     * Mengecek apakah fitur dengan nama tertentu diizinkan aktif
     * berdasarkan aturan: saklar enabled, jadwal tanggal (releaseDate),
     * whitelist user ID, whitelist cabang (branch), atau rollout persentase.
     */
    fun isFeatureEnabled(featureName: String, context: UserContext = stateStore.getUserContext()): Boolean {
        if (featureName.isBlank()) return false

        val featuresJsonStr = stateStore.getFeaturesJson() ?: return false
        val featuresObj = try {
            JSONObject(featuresJsonStr)
        } catch (_: Throwable) {
            return false
        }

        if (!featuresObj.has(featureName)) {
            return false
        }

        val raw = featuresObj.get(featureName)
        if (raw is Boolean) {
            return raw
        }

        if (raw is JSONObject) {
            // 1. Cek saklar utama (enabled)
            val enabled = raw.optBoolean("enabled", true)
            if (!enabled) return false

            // 2. Cek jadwal rilis (releaseDate)
            if (raw.has("releaseDate") && !raw.isNull("releaseDate")) {
                val releaseDateStr = raw.optString("releaseDate")
                if (releaseDateStr.isNotBlank()) {
                    val releaseTime = parseDate(releaseDateStr)
                    if (releaseTime != null && System.currentTimeMillis() < releaseTime) {
                        return false // Belum mencapai tanggal/jam rilis
                    }
                }
            }

            // 3. Cek whitelist pengguna (users)
            if (raw.has("users") && !raw.isNull("users")) {
                val usersArray = raw.optJSONArray("users")
                if (usersArray != null && usersArray.length() > 0) {
                    val currentUserId = context.userId?.trim() ?: ""
                    if (currentUserId.isBlank()) return false

                    var matched = false
                    for (i in 0 until usersArray.length()) {
                        val u = usersArray.optString(i).trim()
                        if (u.equals(currentUserId, ignoreCase = true)) {
                            matched = true
                            break
                        }
                    }
                    if (!matched) return false
                }
            }

            // 4. Cek whitelist cabang (branches)
            if (raw.has("branches") && !raw.isNull("branches")) {
                val branchesArray = raw.optJSONArray("branches")
                if (branchesArray != null && branchesArray.length() > 0) {
                    val currentBranch = context.branch?.trim() ?: ""
                    if (currentBranch.isBlank()) return false

                    var matched = false
                    for (i in 0 until branchesArray.length()) {
                        val b = branchesArray.optString(i).trim()
                        if (b.equals(currentBranch, ignoreCase = true)) {
                            matched = true
                            break
                        }
                    }
                    if (!matched) return false
                }
            }

            // 5. Cek staged rollout persentase (1..100)
            if (raw.has("percentage") && !raw.isNull("percentage")) {
                val percentage = raw.optInt("percentage", 100)
                if (percentage in 1..99) {
                    val identifier = context.userId?.trim()?.ifEmpty { null } ?: "guest_device"
                    val hash = Math.abs(identifier.hashCode()) % 100
                    if (hash >= percentage) {
                        return false
                    }
                } else if (percentage <= 0) {
                    return false
                }
            }

            return true
        }

        return false
    }

    /**
     * Mengambil daftar semua fitur beserta status boolean (aktif/tidak)
     * untuk user context saat ini.
     */
    fun getAllFeatures(context: UserContext = stateStore.getUserContext()): Map<String, Boolean> {
        val result = mutableMapOf<String, Boolean>()
        val featuresJsonStr = stateStore.getFeaturesJson() ?: return result
        val featuresObj = try {
            JSONObject(featuresJsonStr)
        } catch (_: Throwable) {
            return result
        }

        val keys = featuresObj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            result[key] = isFeatureEnabled(key, context)
        }
        return result
    }

    private fun parseDate(dateStr: String): Long? {
        val patterns = arrayOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm",
            "yyyy-MM-dd"
        )
        for (pattern in patterns) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US)
                if (pattern.endsWith("'Z'")) {
                    sdf.timeZone = TimeZone.getTimeZone("UTC")
                }
                val d = sdf.parse(dateStr)
                if (d != null) return d.time
            } catch (_: Throwable) {
            }
        }
        return null
    }
}

