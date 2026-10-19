package com.demian.voiceassistant.knowledge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeywordScorerTest {

    private val scorer = KeywordScorer()

    @Test
    fun `中文按二元组切词`() {
        assertEquals(listOf("你好", "好小", "小楠"), scorer.tokenize("你好小楠"))
    }

    @Test
    fun `英文与数字整词切分`() {
        assertEquals(listOf("hello", "123"), scorer.tokenize("hello 123"))
    }

    @Test
    fun `能挑出最相关的片段`() {
        val chunks = listOf(
            "a.txt" to "我的生日是三月五日，我喜欢巧克力",
            "b.txt" to "公司的地址在深圳南山区科技园",
        )
        val result = scorer.rank("我的生日是什么时候", chunks, 1)
        assertEquals(1, result.size)
        assertTrue(result[0].contains("生日"))
    }

    @Test
    fun `完全不相关时返回空`() {
        val chunks = listOf("a.txt" to "完全无关的内容")
        assertTrue(scorer.rank("天气怎么样", chunks, 3).isEmpty())
    }

    @Test
    fun `空查询返回空`() {
        val chunks = listOf("a.txt" to "任意内容")
        assertTrue(scorer.rank("   ", chunks, 3).isEmpty())
    }
}
