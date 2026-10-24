package com.example.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-JVM tests for the AI engine (no Android needed). */
class SGEngineTest {

    private fun vest(i: Int, temp: Double = 30.0, hum: Double = 55.0, fall: Boolean = false, motion: String = "WALKING") =
        VestRow(1_000_000L + i * 30_000L, temp, hum, 1005.0, fall, false, motion, null, null)

    @Test
    fun editableThresholdsChangeTheAlerts() {
        val row = vest(0, temp = 40.0)
        assertTrue(evalVest(row, Thresholds()).isEmpty())                       // 40 C is fine at the default 45 C limit
        val hits = evalVest(row, Thresholds(tempMax = 35.0))                    // user lowered the limit in Safety Rules
        assertEquals(listOf("temp"), hits.map { it.key })
    }

    @Test
    fun consecutiveLogsOfOneEventAreOneIncident() {
        val rows = (0 until 10).map { vest(it, fall = true) } + (10 until 20).map { vest(it) }
        val inc = vestIncidents(rows, Thresholds())
        assertEquals(1, inc.size)
        assertEquals(10, inc[0].snaps)
    }

    @Test
    fun frozenSensorIsFlagged() {
        val rows = (0 until 90).map { vest(it, hum = 55.0, temp = 30.0 + (it % 3) * 0.1) }
        val an = analyze(rows, emptyList(), Thresholds())
        assertTrue(an.findings.any { it.tag == "FLATLINE" && it.key == "hum" })
    }

    @Test
    fun longLyingWithoutFallIsMedium() {
        val rows = (0 until 40).map { vest(it, motion = "LYING") }
        val an = analyze(rows, emptyList(), Thresholds())
        assertTrue(an.findings.any { it.kind == "lying" && it.sev == "medium" })
    }

    @Test
    fun banglaAndEnglishQuestionsAreUnderstood() {
        assertEquals("alertCount", detectIntent("আজকে কয়টা অ্যালার্ট হয়েছে?").intent)
        assertEquals("status", detectIntent("ওয়ার্কারের অবস্থা এখন কেমন?").intent)
        assertEquals("anomaly", detectIntent("anything unusual?").intent)
        assertEquals(3, detectIntent("how many alerts in the last 3 hours").scope.hours)
    }

    @Test
    fun toFixedMatchesJavaScript() {
        // expected values are what JavaScript's Number.toFixed returns (binary-exact rounding)
        assertEquals("0.9", fx(0.95, 1))
        assertEquals("1.00", fx(1.005, 2))
        assertEquals("3", fx(2.5, 0))
        assertEquals("-0.0", fx(-0.001, 1))
        assertEquals("14.7", fx(14.65, 1))
        assertEquals("0.13", fx(0.125, 2))
    }
}
