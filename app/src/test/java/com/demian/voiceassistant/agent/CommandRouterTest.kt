package com.demian.voiceassistant.agent

import com.demian.voiceassistant.phone.PhoneInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CommandRouterTest {

    private class FakePhone : PhoneInfo {
        override fun time() = "现在 10点00分"
        override fun date() = "今天是 2026年10月19日"
        override fun battery() = "当前电量 80%"
    }

    private val router = CommandRouter(FakePhone())

    @Test
    fun `时间命令走本地`() {
        assertEquals("现在 10点00分", router.route("现在几点了"))
    }

    @Test
    fun `日期命令优先于时间`() {
        assertEquals("今天是 2026年10月19日", router.route("今天几号"))
    }

    @Test
    fun `电量命令`() {
        assertEquals("当前电量 80%", router.route("电量还剩多少"))
    }

    @Test
    fun `打招呼`() {
        assertEquals("你好，我在。", router.route("你好"))
    }

    @Test
    fun `未知命令返回 null`() {
        assertNull(router.route("帮我写一首诗"))
    }

    @Test
    fun `空白返回 null`() {
        assertNull(router.route("   "))
    }
}
