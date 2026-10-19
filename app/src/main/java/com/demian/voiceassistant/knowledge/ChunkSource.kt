package com.demian.voiceassistant.knowledge

/** 片段来源的最小接口，便于测试时用内存实现替换 SQLite。 */
interface ChunkSource {
    fun allChunks(): List<Pair<String, String>>
}
