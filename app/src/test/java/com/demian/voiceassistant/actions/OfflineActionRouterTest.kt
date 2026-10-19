package com.demian.voiceassistant.actions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OfflineActionRouterTest {

    private val router = OfflineActionRouter()

    @Test
    fun `打开手电筒`() {
        val cmd = router.route("打开手电筒")
        assertEquals("set_flashlight", cmd?.name)
        assertEquals(true, cmd?.args?.getBoolean("on"))
    }

    @Test
    fun `关闭手电筒`() {
        val cmd = router.route("关掉手电筒")
        assertEquals("set_flashlight", cmd?.name)
        assertEquals(false, cmd?.args?.getBoolean("on"))
    }

    @Test
    fun `音量最大`() {
        val cmd = router.route("音量最大")
        assertEquals("set_volume", cmd?.name)
        assertEquals("max", cmd?.args?.getString("action"))
    }

    @Test
    fun `下一首`() {
        val cmd = router.route("下一首")
        assertEquals("media_control", cmd?.name)
        assertEquals("next", cmd?.args?.getString("action"))
    }

    @Test
    fun `暂停`() {
        val cmd = router.route("暂停")
        assertEquals("media_control", cmd?.name)
        assertEquals("pause", cmd?.args?.getString("action"))
    }

    @Test
    fun `普通问答不拦截`() {
        assertNull(router.route("现在几点"))
        assertNull(router.route("帮我写一首诗"))
    }

    @Test
    fun `过长句子不当作命令`() {
        assertNull(router.route("请帮我打开手电筒然后告诉我今天天气怎么样顺便设个闹钟"))
    }
}
