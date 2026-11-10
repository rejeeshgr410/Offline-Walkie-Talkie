package com.example.offlinewalkietalkie.mbtiles

import com.example.offlinewalkietalkie.data.mbtiles.MbTilesDatabase
import com.example.offlinewalkietalkie.voice.AudioRoute
import org.junit.Assert.assertEquals
import org.junit.Test

class MbTilesTest {

    @Test
    fun testTmsCoordinateMath() {
        // Zoom 0 (1 tile total, 1x1)
        assertEquals(0, MbTilesDatabase.xyzToTms(zoom = 0, y = 0))
        assertEquals(0, MbTilesDatabase.tmsToXyz(zoom = 0, tmsRow = 0))

        // Zoom 1 (4 tiles total, 2x2)
        assertEquals(1, MbTilesDatabase.xyzToTms(zoom = 1, y = 0))
        assertEquals(0, MbTilesDatabase.xyzToTms(zoom = 1, y = 1))
        assertEquals(0, MbTilesDatabase.tmsToXyz(zoom = 1, tmsRow = 1))
        assertEquals(1, MbTilesDatabase.tmsToXyz(zoom = 1, tmsRow = 0))

        // Zoom 2 (16 tiles total, 4x4)
        for (y in 0 until 4) {
            val tms = MbTilesDatabase.xyzToTms(zoom = 2, y = y)
            val roundTrip = MbTilesDatabase.tmsToXyz(zoom = 2, tmsRow = tms)
            assertEquals(y, roundTrip)
        }

        // Zoom 14 (high resolution)
        val testY = 5421
        val tms14 = MbTilesDatabase.xyzToTms(zoom = 14, y = testY)
        val roundTrip14 = MbTilesDatabase.tmsToXyz(zoom = 14, tmsRow = tms14)
        assertEquals(testY, roundTrip14)
    }

    @Test
    fun testAudioRouteNames() {
        assertEquals("Speakerphone", AudioRoute.SPEAKER.displayName)
        assertEquals("Private Earpiece", AudioRoute.EARPIECE.displayName)
        assertEquals("Bluetooth Headset", AudioRoute.BLUETOOTH_SCO.displayName)
        assertEquals("Wired Headset", AudioRoute.WIRED_HEADSET.displayName)
    }
}
