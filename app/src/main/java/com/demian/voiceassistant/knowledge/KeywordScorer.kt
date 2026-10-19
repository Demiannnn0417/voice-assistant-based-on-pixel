package com.demian.voiceassistant.knowledge

/**
 * 纯逻辑的关键词打分器（不依赖 Android，可直接单元测试）。
 *
 * 检索方式：中文取相邻两字组合（bigram），英文/数字取整词；
 * 打分用简化 BM25（词频饱和 + 长度归一化 + 词长权重）。
 */
class KeywordScorer {

    /** 把文本切成检索用的词。 */
    fun tokenize(text: String): List<String> {
        val terms = mutableListOf<String>()

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

        Regex("[A-Za-z0-9]{2,}").findAll(text).forEach {
            terms.add(it.value.lowercase())
        }

        return terms.distinct()
    }

    /** 单个片段的相关性得分。 */
    fun score(queryTerms: List<String>, chunk: String): Double {
        if (queryTerms.isEmpty() || chunk.isBlank()) return 0.0
        val lower = chunk.lowercase()
        val lengthFactor = lower.length / 500.0
        var total = 0.0

        for (term in queryTerms) {
            var count = 0
            var index = lower.indexOf(term)
            while (index >= 0) {
                count++
                index = lower.indexOf(term, index + term.length)
            }
            if (count > 0) {
                val tf = count.toDouble()
                val weight = (term.length + 1).toDouble()
                // 简化 BM25：tf 饱和 + 文档长度归一化
                total += weight * tf / (tf + 1.2 + 0.75 * lengthFactor)
            }
        }
        return total
    }

    /** 从多个片段中挑出与查询最相关的 topK 个。 */
    fun rank(
        query: String,
        chunks: List<Pair<String, String>>,
        topK: Int,
    ): List<String> {
        val terms = tokenize(query)
        if (terms.isEmpty()) return emptyList()
        return chunks
            .map { (_, content) -> content to score(terms, content) }
            .filter { it.second > 0.0 }
            .sortedByDescending { it.second }
            .take(topK)
            .map { it.first }
    }
}
