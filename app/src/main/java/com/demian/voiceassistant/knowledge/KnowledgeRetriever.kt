package com.demian.voiceassistant.knowledge

/**
 * 知识库检索：把查询交给 [KeywordScorer] 打分排序。
 * 完全离线、零依赖。
 */
class KnowledgeRetriever(
    private val store: ChunkSource,
    private val scorer: KeywordScorer = KeywordScorer(),
) {

    /** 返回与 query 最相关的若干片段。 */
    fun search(query: String, topK: Int = 3): List<String> =
        scorer.rank(query, store.allChunks(), topK)
}
