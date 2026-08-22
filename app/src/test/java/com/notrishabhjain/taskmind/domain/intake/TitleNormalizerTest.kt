package com.notrishabhjain.taskmind.domain.intake

import org.junit.Assert.assertEquals
import org.junit.Test

class TitleNormalizerTest {

    @Test
    fun `normalize trims and collapses whitespace`() {
        assertEquals("Call mom", TitleNormalizer.normalize("  Call   mom  "))
    }

    @Test
    fun `titleKey lowercases`() {
        assertEquals("call mom", TitleNormalizer.titleKey("Call MOM"))
    }

    @Test
    fun `titleKey strips leading politeness prefix`() {
        assertEquals("call mom", TitleNormalizer.titleKey("Please call mom"))
    }

    @Test
    fun `titleKey strips repeated and mixed politeness prefixes`() {
        assertEquals(
            "call mom",
            TitleNormalizer.titleKey("Pls... kindly zara, thoda call mom")
        )
    }

    @Test
    fun `titleKey does not strip politeness inside words`() {
        assertEquals("pleased to meet you", TitleNormalizer.titleKey("Pleased to meet you"))
    }

    @Test
    fun `titleKey strips trailing punctuation`() {
        assertEquals("call mom", TitleNormalizer.titleKey("Call mom!!!"))
    }

    @Test
    fun `titleKey strips devanagari danda`() {
        assertEquals("call mom", TitleNormalizer.titleKey("Call mom।"))
    }

    @Test
    fun `titleKey keeps distinct tasks distinct`() {
        val first = TitleNormalizer.titleKey("Call mom")
        val second = TitleNormalizer.titleKey("Email mom")
        org.junit.Assert.assertNotEquals(first, second)
    }

    @Test
    fun `titleKey collapses equivalent forms to same key`() {
        assertEquals(
            TitleNormalizer.titleKey("Kindly CALL   Mom."),
            TitleNormalizer.titleKey("please call mom")
        )
    }

    @Test
    fun `titleKey falls back when only politeness remains`() {
        assertEquals("please", TitleNormalizer.titleKey("Please!"))
    }

    @Test
    fun `blank title yields empty key`() {
        assertEquals("", TitleNormalizer.titleKey("   "))
    }
}
