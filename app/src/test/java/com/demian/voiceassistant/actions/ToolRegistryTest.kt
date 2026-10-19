package com.demian.voiceassistant.actions

import org.junit.Assert.assertTrue
import org.junit.Test

class ToolRegistryTest {

    private fun names(): List<String> {
        val tools = ToolRegistry.tools()
        return (0 until tools.length()).map {
            tools.getJSONObject(it).getJSONObject("function").getString("name")
        }
    }

    @Test
    fun `包含核心工具`() {
        val names = names()
        assertTrue(
            names.containsAll(
                listOf(
                    "set_alarm", "set_timer", "open_app", "open_url",
                    "media_control", "set_flashlight", "set_volume",
                    "make_call", "save_note", "list_notes",
                )
            )
        )
    }

    @Test
    fun `每个工具都有描述和参数`() {
        val tools = ToolRegistry.tools()
        for (i in 0 until tools.length()) {
            val fn = tools.getJSONObject(i).getJSONObject("function")
            assertTrue(fn.getString("name").isNotBlank())
            assertTrue(fn.getString("description").isNotBlank())
            assertTrue(fn.has("parameters"))
        }
    }

    @Test
    fun `工具名不重复`() {
        val names = names()
        assertTrue(names.size == names.distinct().size)
    }
}
