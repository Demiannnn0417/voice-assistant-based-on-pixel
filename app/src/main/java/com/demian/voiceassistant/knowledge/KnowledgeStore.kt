package com.demian.voiceassistant.knowledge

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * 本地知识库存储。用系统自带 SQLite，不引入 Room 等依赖。
 *
 * 导入的文件会被切成若干「片段(chunk)」存进来，检索时按相关性取回。
 */
class KnowledgeStore(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                source TEXT NOT NULL,
                content TEXT NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE")
        onCreate(db)
    }

    /** 批量写入片段。 */
    fun addChunks(source: String, chunks: List<String>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            chunks.forEach { chunk ->
                val values = ContentValues().apply {
                    put("source", source)
                    put("content", chunk)
                }
                db.insert(TABLE, null, values)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** 取出全部片段（source, content）。 */
    fun allChunks(): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        readableDatabase.rawQuery(
            "SELECT source, content FROM $TABLE", null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result.add(cursor.getString(0) to cursor.getString(1))
            }
        }
        return result
    }

    /** 片段总数。 */
    fun chunkCount(): Int {
        readableDatabase.rawQuery("SELECT COUNT(*) FROM $TABLE", null).use { cursor ->
            if (cursor.moveToFirst()) return cursor.getInt(0)
        }
        return 0
    }

    /** 按来源文件分组，返回 (文件名, 片段数) 列表。 */
    fun sources(): List<Pair<String, Int>> {
        val result = mutableListOf<Pair<String, Int>>()
        readableDatabase.rawQuery(
            "SELECT source, COUNT(*) FROM $TABLE GROUP BY source ORDER BY source", null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result.add(cursor.getString(0) to cursor.getInt(1))
            }
        }
        return result
    }

    /** 清空知识库。 */
    fun clear() {
        writableDatabase.execSQL("DELETE FROM $TABLE")
    }

    companion object {
        private const val DB_NAME = "knowledge.db"
        private const val DB_VERSION = 1
        private const val TABLE = "chunks"
    }
}
