package com.demian.voiceassistant.wake

import android.content.Context
import net.sourceforge.pinyin4j.PinyinHelper
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType

/**
 * 把中文唤醒词转成 sherpa-onnx KWS 需要的拼音 token 串。
 *
 * 模型词表是「声母 + 韵母」形式（见 assets/…/tokens.txt），例如
 * 「你好小楠」→ `n ǐ h ǎo x iǎo n án`。
 *
 * 注意：pinyin4j 的 WITH_TONE_MARK 用的是 breve(˘)，与词表的 caron(ˇ) 不符，
 * 所以我们取**带数字声调**的拼音（如 ni3），再自己套用正确的声调符号。
 */
object PinyinKeywords {

    sealed interface Result {
        data class Ok(val keywords: String) : Result
        data class Invalid(val message: String) : Result
    }

    /** 声母表（先匹配两字声母 zh/ch/sh）。 */
    private val INITIALS = listOf(
        "zh", "ch", "sh",
        "b", "p", "m", "f", "d", "t", "n", "l", "g", "k", "h",
        "j", "q", "x", "r", "z", "c", "s", "y", "w",
    )

    /** 取「数字声调」形式的拼音，避免 diacritic 不一致问题。 */
    private val format = HanyuPinyinOutputFormat().apply {
        caseType = HanyuPinyinCaseType.LOWERCASE
        toneType = HanyuPinyinToneType.WITH_TONE_NUMBER
        vCharType = HanyuPinyinVCharType.WITH_U_UNICODE
    }

    /** 每个元音的 5 种写法：[轻声, 一声, 二声, 三声, 四声]。 */
    private val TONE_TABLE = mapOf(
        'a' to "aāáǎà",
        'e' to "eēéěè",
        'i' to "iīíǐì",
        'o' to "oōóǒò",
        'u' to "uūúǔù",
        'ü' to "üǖǘǚǜ",
    )

    /** 读取模型词表（tokens.txt）。 */
    fun vocab(context: Context): Set<String> {
        val text = context.assets.open("${WakeWordDetector.MODEL_DIR}/tokens.txt")
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
        return text.lineSequence()
            .mapNotNull { line -> line.trim().substringBefore(' ').takeIf { it.isNotBlank() } }
            .toSet()
    }

    /** 生成 keywords 行；失败时给出原因。 */
    fun toKeywords(word: String, vocab: Set<String>): Result {
        val clean = word.trim()
        if (clean.isBlank()) return Result.Invalid("唤醒词不能为空")

        val tokens = mutableListOf<String>()
        for (ch in clean) {
            if (ch.isWhitespace()) continue

            if (ch.code !in 0x4E00..0x9FFF) {
                if (ch.toString() in vocab) {
                    tokens.add(ch.toString())
                    continue
                }
                return Result.Invalid("暂不支持字符「$ch」，请只用中文")
            }

            val pinyin = pinyinOf(ch)
                ?: return Result.Invalid("无法识别「$ch」，请换一个名字")
            for (part in splitSyllable(pinyin)) {
                if (part !in vocab) {
                    return Result.Invalid("「$ch」的读音 $part 不在模型词表里，换个名字试试")
                }
                tokens.add(part)
            }
        }

        if (tokens.isEmpty()) return Result.Invalid("唤醒词不能为空")
        return Result.Ok(tokens.joinToString(" ") + " @" + clean)
    }

    /** 生成 keywords 行；失败时退回默认唤醒词。 */
    fun keywordsOrDefault(context: Context, word: String): String {
        val vocab = runCatching { vocab(context) }.getOrDefault(emptySet())
        return when (val r = toKeywords(word, vocab)) {
            is Result.Ok -> r.keywords
            is Result.Invalid -> defaultKeywords(context)
        }
    }

    /** 读取 assets 里的默认 keywords.txt（默认唤醒词）。 */
    fun defaultKeywords(context: Context): String =
        context.assets.open("${WakeWordDetector.MODEL_DIR}/keywords.txt")
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
            .lineSequence().firstOrNull { it.isNotBlank() }?.trim()
            ?: "n ǐ h ǎo x iǎo án @你好小楠"

    private fun pinyinOf(ch: Char): String? = try {
        PinyinHelper.toHanyuPinyinStringArray(ch, format)?.firstOrNull()?.let { toMarked(it) }
    } catch (_: Exception) {
        null
    }

    /** 把 `ni3` 这类带数字声调的拼音转成 `nǐ`。 */
    private fun toMarked(numbered: String): String {
        if (numbered.isEmpty()) return numbered
        val last = numbered.last()
        val tone = if (last.isDigit()) last - '0' else 0
        val base = if (tone in 1..4) numbered.dropLast(1) else numbered
        if (tone !in 1..4) return base

        // 标调规则：有 a 标 a；否则 o / e；否则最后一个 i/u/ü
        var idx = base.indexOf('a')
        if (idx < 0) idx = base.indexOf('o')
        if (idx < 0) idx = base.indexOf('e')
        if (idx < 0) {
            for (i in base.indices.reversed()) {
                val c = base[i]
                if (c == 'i' || c == 'u' || c == 'ü') {
                    idx = i
                    break
                }
            }
        }
        if (idx < 0) return base
        return base.substring(0, idx) + mark(base[idx], tone) + base.substring(idx + 1)
    }

    private fun mark(vowel: Char, tone: Int): String {
        val table = TONE_TABLE[vowel] ?: return vowel.toString()
        return table.getOrNull(tone)?.toString() ?: vowel.toString()
    }

    private fun splitSyllable(pinyin: String): List<String> {
        for (initial in INITIALS) {
            if (pinyin.startsWith(initial)) {
                val rest = pinyin.substring(initial.length)
                return if (rest.isNotEmpty()) listOf(initial, rest) else listOf(pinyin)
            }
        }
        return listOf(pinyin)
    }
}
