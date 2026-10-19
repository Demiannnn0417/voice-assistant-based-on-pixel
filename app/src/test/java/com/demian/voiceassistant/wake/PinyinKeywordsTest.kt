package com.demian.voiceassistant.wake

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinyinKeywordsTest {

    private val vocab = setOf(
        "n", "ǐ", "h", "ǎo", "x", "iǎo", "án", "ǚ",
        "l", "ín", "m", "ěi", "ì", "s", "è", "ǜ",
    )

    @Test
    fun `你好小楠 能正确转成拼音 token`() {
        val result = PinyinKeywords.toKeywords("你好小楠", vocab)
        assertTrue(result is PinyinKeywords.Result.Ok)
        assertEquals(
            "n ǐ h ǎo x iǎo n án @你好小楠",
            (result as PinyinKeywords.Result.Ok).keywords,
        )
    }

    @Test
    fun `女 的 ü 韵母处理正确`() {
        val result = PinyinKeywords.toKeywords("女", vocab)
        assertTrue(result is PinyinKeywords.Result.Ok)
        assertEquals("n ǚ @女", (result as PinyinKeywords.Result.Ok).keywords)
    }

    @Test
    fun `词表缺失时给出错误`() {
        val result = PinyinKeywords.toKeywords("你好小楠", setOf("n", "ǐ"))
        assertTrue(result is PinyinKeywords.Result.Invalid)
    }

    @Test
    fun `不支持的英文不会被当作唤醒词`() {
        val result = PinyinKeywords.toKeywords("hey", vocab)
        assertTrue(result is PinyinKeywords.Result.Invalid)
    }

    @Test
    fun `空唤醒词报错`() {
        val result = PinyinKeywords.toKeywords("   ", vocab)
        assertTrue(result is PinyinKeywords.Result.Invalid)
    }
}
