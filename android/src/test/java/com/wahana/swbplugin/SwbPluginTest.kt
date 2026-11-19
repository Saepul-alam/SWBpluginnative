package com.wahana.swbplugin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SwbPluginTest {

    private val implementation = SwbPluginImpl()

    @Test
    fun testCalculateStatusUsableOnlyWhenAllConditionsMet() {
        val usableStatus = implementation.calculateStatus(enabled = true, granted = true, isMock = false)
        assertTrue(usableStatus.getBoolean("usable"))
        assertTrue(usableStatus.getBoolean("enabled"))
        assertTrue(usableStatus.getBoolean("granted"))
        assertFalse(usableStatus.getBoolean("isMock"))

        val mockStatus = implementation.calculateStatus(enabled = true, granted = true, isMock = true)
        assertFalse(mockStatus.getBoolean("usable"))

        val noGpsStatus = implementation.calculateStatus(enabled = false, granted = true, isMock = false)
        assertFalse(noGpsStatus.getBoolean("usable"))

        val noPermStatus = implementation.calculateStatus(enabled = true, granted = false, isMock = false)
        assertFalse(noPermStatus.getBoolean("usable"))

        val noBgPermStatus = implementation.calculateStatus(enabled = true, granted = true, isMock = false, backgroundGranted = false)
        assertFalse(noBgPermStatus.getBoolean("usable"))
    }

    @Test
    fun testCalculateStatusDiagnosisMessagesAndSolutions() {
        val usableStatus = implementation.calculateStatus(enabled = true, granted = true, isMock = false)
        assertEquals("OK", usableStatus.getString("code"))
        assertTrue(usableStatus.getString("solution")?.contains("siap digunakan") == true)

        val noGpsStatus = implementation.calculateStatus(enabled = false, granted = true, isMock = false)
        assertEquals("GPS_DISABLED", noGpsStatus.getString("code"))
        assertTrue(noGpsStatus.getString("solution")?.contains("Aktifkan GPS") == true)

        val noGpsNoPermStatus = implementation.calculateStatus(enabled = false, granted = false, isMock = false)
        assertEquals("GPS_DISABLED", noGpsNoPermStatus.getString("code"))
        assertTrue(noGpsNoPermStatus.getString("solution")?.contains("Aktifkan GPS") == true)

        val noPermStatus = implementation.calculateStatus(enabled = true, granted = false, isMock = false)
        assertEquals("PERMISSION_DENIED", noPermStatus.getString("code"))
        assertTrue(noPermStatus.getString("solution")?.contains("Pengaturan") == true)

        val coarseOnlyStatus = implementation.calculateStatus(enabled = true, granted = false, isMock = false, isCoarseOnly = true)
        assertEquals("COARSE_ONLY", coarseOnlyStatus.getString("code"))
        assertTrue(coarseOnlyStatus.getString("solution")?.contains("Akurat") == true)

        val mockStatus = implementation.calculateStatus(enabled = true, granted = true, isMock = true)
        assertEquals("MOCK_DETECTED", mockStatus.getString("code"))
        assertTrue(mockStatus.getString("solution")?.contains("Fake GPS") == true)

        val bgDeniedStatus = implementation.calculateStatus(enabled = true, granted = true, isMock = false, backgroundGranted = false)
        assertEquals("BACKGROUND_LOCATION_DENIED", bgDeniedStatus.getString("code"))
        assertTrue(bgDeniedStatus.getString("solution")?.contains("sepanjang waktu") == true)
    }

    @Test
    fun testLocationMockNullLocationReturnsFalse() {
        assertFalse(implementation.isLocationMock(null))
    }

    @Test
    fun testToUploadPayloadJsonContract() {
        val record = LocationRecordEntity(
            id = "test-uuid-1234",
            latitude = -6.2881792,
            longitude = 106.7614208,
            accuracy = 15.0,
            altitude = 50.0,
            altitudeAccuracy = 5.0,
            heading = 90.0,
            speed = 12.5,
            timestamp = 1700000000000L,
            isMock = false,
            provider = "fused",
            metadata = null,
            uploaded = false,
            uploadedAt = null,
            attempts = 0
        )

        val jsonStr = record.toUploadPayloadJson()
        val json = org.json.JSONObject(jsonStr)

        assertEquals("-6.2881792", json.getString("latitude"))
        assertEquals("106.7614208", json.getString("longitude"))
        assertEquals(2, json.length())
    }
}

