package com.demian.voiceassistant

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 在模拟器/真机上跑的 UI 冒烟测试：验证核心页面能正常显示与切换。
 */
@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeShowsCoreElements() {
        rule.onNodeWithText("主页").assertIsDisplayed()
        rule.onNodeWithText("常驻唤醒").assertIsDisplayed()
        rule.onNodeWithText("文字输入（无需麦克风，也能测试助手）").assertExists()
    }

    @Test
    fun canNavigateToSettings() {
        rule.onNodeWithText("设置").performClick()
        rule.onNodeWithText("DeepSeek API Key").assertExists()
        rule.onNodeWithText("自定义唤醒词（中文）").assertExists()
    }

    @Test
    fun canNavigateToKnowledge() {
        rule.onNodeWithText("知识库").performClick()
        rule.onNodeWithText("本地知识库").assertExists()
    }

    @Test
    fun canOpenDiagnostics() {
        rule.onNodeWithText("设置").performClick()
        rule.onNodeWithText("🧪 查看诊断日志").performScrollTo().performClick()
        rule.onNodeWithText("诊断日志").assertExists()
        rule.onNodeWithText("← 返回").performClick()
        rule.onNodeWithText("DeepSeek API Key").assertExists()
    }
}
