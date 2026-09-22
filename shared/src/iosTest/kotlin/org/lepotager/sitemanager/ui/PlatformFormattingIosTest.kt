package org.lepotager.sitemanager.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlatformFormattingIosTest {
    @Test
    fun dateTimeKeepsBusinessClockAndUsesReadableSeparator() {
        val formatted = formatBusinessDateTime("2026-09-22T14:30:45+02:00")
        assertTrue("14:30" in formatted)
        assertTrue("·" in formatted)
        assertFalse("T14:30" in formatted)
    }

    @Test
    fun fractionalIsoDateTimeKeepsBusinessClock() {
        val formatted = formatBusinessDateTime("2026-09-22T09:07:05.321Z")
        assertTrue("09:07" in formatted)
        assertTrue("·" in formatted)
    }

    @Test
    fun invalidValueFallsBackWithoutCrashing() {
        assertEquals("pas-une-date", formatBusinessDateTime("pas-une-date"))
    }
}
