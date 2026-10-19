package com.demian.voiceassistant.knowledge

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 把用户选中的文本文件导入知识库。
 *
 * 目前支持纯文本类（.txt / .md / .csv 等）。PDF/Word 需要额外解析库，暂不支持。
 */
object KnowledgeImporter {

    private const val CHUNK_SIZE = 600
    private const val OVERLAP = 80

    /** 导入一个文件，返回写入的片段数。 */
    suspend fun import(context: Context, store: KnowledgeStore, uri: Uri): Int =
        withContext(Dispatchers.IO) {
            val text = context.contentResolver.openInputStream(uri)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                .orEmpty()
            if (text.isBlank()) return@withContext 0

            val source = queryDisplayName(context, uri)
            val chunks = splitIntoChunks(text)
            store.addChunks(source, chunks)
            chunks.size
        }

    private fun queryDisplayName(context: Context, uri: Uri): String {
        var name = uri.lastPathSegment ?: "未命名"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) {
                name = cursor.getString(index) ?: name
            }
        }
        return name
    }

    /** 按段落与长度切分，块之间保留少量重叠，避免语义被切断。 */
    private fun splitIntoChunks(text: String): List<String> {
        val normalized = text.replace("\r\n", "\n")
        val paragraphs = normalized.split("\n")
        val chunks = mutableListOf<String>()
        val builder = StringBuilder()

        fun flush() {
            if (builder.isBlank()) return
            val chunk = builder.toString().trim()
            builder.clear()
            if (chunk.isEmpty()) return
            chunks.add(chunk)
            // 把尾部带到下一块，保留上下文
            if (chunk.length > OVERLAP) {
                builder.append(chunk.takeLast(OVERLAP)).append('\n')
            }
        }

        for (line in paragraphs) {
            if (builder.length + line.length + 1 > CHUNK_SIZE && builder.isNotEmpty()) {
                flush()
            }
            builder.append(line).append('\n')
        }
        flush()
        return chunks
    }
}
