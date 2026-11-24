package com.example.stayfree.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class HeroDateTest {

    private val original = Locale.getDefault()

    @After
    fun restore() = Locale.setDefault(original)

    @Test
    fun `dashboard date is capitalised with a comma, in Serbian Latin`() {
        Locale.setDefault(Locale.forLanguageTag("sr"))
        assertEquals("Petak, 20. nov", TimeUtils.formatHeroDate("2026-11-20"))
    }

    @Test
    fun `dashboard date in English`() {
        Locale.setDefault(Locale.US)
        assertEquals("Friday, 20. Nov", TimeUtils.formatHeroDate("2026-11-20"))
    }

    @Test
    fun `unparsable input comes back unchanged`() {
        assertEquals("nope", TimeUtils.formatHeroDate("nope"))
    }
}
