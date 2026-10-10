package com.demian.voiceassistant.knowledge

/**
 * 知识库检索。没有用向量模型，而是用**中文二元组 + 英文词**的关键词打分，
 * 够用且完全离线、零依赖。
 */
class KnowledgeRetriever(private val store: KnowledgeStore) {

    /** 返回与 query 最相关的若干片段。 */
    fun search(query: String, topK: Int = 3): List<String> {
        val terms = tokenize(query)
        if (terms.isEmpty()) return emptyList()
        return store.allChunks()
            .map { (_, content) -> content to score(terms, content) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(topK)
            .map { it.first }
    }

    /** 把查询切成检索用的「词」：中文取相邻两字的组合，英文数字取整词。 */
    private fun tokenize(text: String): List<String> {
        val terms = mutableListOf<String>()

        // 中文：二元组（bigram）
        Regex("[\\u4e00-\\u9fff]+").findAll(text).forEach { match ->
            val run = match.value
            if (run.length == 1) {
                terms.add(run)
            } else {
                for (i in 0 until run.length - 1) {
                    terms.add(run.substring(i, i + 2))
                }
            }
        }

        // 英文 / 数字：长度 >= 2 的词
        Regex("[A-Za-z0-9]{2,}").findAll(text).forEach {
            terms.add(it.value.lowercase())
        }

        return terms.distinct()
    }

    private fun score(terms: List<String>, chunk: String): Int {
        val lower = chunk.lowercase()
        var total = 0
        for (term in terms) {
            if (lower.contains(term)) total += term.length // 长词权重更高
        }
        return total
    }
}
