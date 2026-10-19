package com.demian.voiceassistant.phone

/** 手机信息的最小接口，便于单元测试时用假实现替换。 */
interface PhoneInfo {
    fun time(): String
    fun date(): String
    fun battery(): String
}
