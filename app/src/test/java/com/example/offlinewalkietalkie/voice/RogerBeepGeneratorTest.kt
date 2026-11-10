package com.example.offlinewalkietalkie.voice

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class RogerBeepGeneratorTest {

    private lateinit var generator: RogerBeepGenerator

    @Before
    fun setUp() {
        generator = RogerBeepGenerator()
    }

    @Test
    fun testStyleFromIdFallback() {
        assertEquals(RogerBeepStyle.MOTOROLA, RogerBeepStyle.fromId("motorola"))
        assertEquals(RogerBeepStyle.QUINDAR, RogerBeepStyle.fromId("quindar"))
        assertEquals(RogerBeepStyle.TACTICAL_CHIRP, RogerBeepStyle.fromId("tactical"))
        assertEquals(RogerBeepStyle.SQUELCH, RogerBeepStyle.fromId("squelch"))
        assertEquals(RogerBeepStyle.NONE, RogerBeepStyle.fromId("none"))
        // Unknown defaults to MOTOROLA
        assertEquals(RogerBeepStyle.MOTOROLA, RogerBeepStyle.fromId("unknown_xyz"))
    }

    @Test
    fun testSilentStyleProducesZeroBytes() {
        val intro = generator.generateIntroTone(RogerBeepStyle.NONE)
        val outro = generator.generateOutroTone(RogerBeepStyle.NONE)
        assertEquals(0, intro.size)
        assertEquals(0, outro.size)
    }

    @Test
    fun testMotorolaToneGeneration() {
        val intro = generator.generateIntroTone(RogerBeepStyle.MOTOROLA)
        val outro = generator.generateOutroTone(RogerBeepStyle.MOTOROLA)

        assertTrue("Intro must not be empty", intro.isNotEmpty())
        assertTrue("Outro must not be empty", outro.isNotEmpty())
        assertEquals("PCM 16-bit must have even byte count", 0, intro.size % 2)
        assertEquals("PCM 16-bit must have even byte count", 0, outro.size % 2)

        // Check non-silent samples exist
        assertHasAudioContent(intro)
        assertHasAudioContent(outro)
    }

    @Test
    fun testQuindarToneGeneration() {
        val intro = generator.generateIntroTone(RogerBeepStyle.QUINDAR)
        val outro = generator.generateOutroTone(RogerBeepStyle.QUINDAR)

        assertTrue(intro.isNotEmpty())
        assertTrue(outro.isNotEmpty())
        // 200ms @ 16000Hz = 3200 samples = 6400 bytes
        assertEquals(6400, intro.size)
        assertEquals(6400, outro.size)

        assertHasAudioContent(intro)
        assertHasAudioContent(outro)
    }

    @Test
    fun testTacticalChirpGeneration() {
        val intro = generator.generateIntroTone(RogerBeepStyle.TACTICAL_CHIRP)
        val outro = generator.generateOutroTone(RogerBeepStyle.TACTICAL_CHIRP)

        assertTrue(intro.isNotEmpty())
        assertTrue(outro.isNotEmpty())
        assertHasAudioContent(intro)
        assertHasAudioContent(outro)
    }

    @Test
    fun testSquelchTailGeneration() {
        val intro = generator.generateIntroTone(RogerBeepStyle.SQUELCH)
        val outro = generator.generateOutroTone(RogerBeepStyle.SQUELCH)

        assertTrue(intro.isNotEmpty())
        assertTrue(outro.isNotEmpty())
        assertHasAudioContent(intro)
        assertHasAudioContent(outro)
    }

    private fun assertHasAudioContent(pcm: ByteArray) {
        var nonZeroCount = 0
        for (b in pcm) {
            if (b.toInt() != 0) nonZeroCount++
        }
        assertTrue("Tone must contain audio signal", nonZeroCount > pcm.size / 4)
    }
}
