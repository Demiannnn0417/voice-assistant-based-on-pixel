package com.demian.voiceassistant.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.demian.voiceassistant.llm.ChatMessage

/**
 * 会话历史存储。用来给大模型提供「上一句/上下文」，也让界面能展示聊天记录。
 * 只保存 user / assistant 两类消息。
 */
class ConversationStore(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                role TEXT NOT NULL,
                content TEXT NOT NULL,
                ts INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE")
        onCreate(db)
    }

    fun add(role: String, content: String) {
        val values = ContentValues().apply {
            put("role", role)
            put("content", content)
            put("ts", System.currentTimeMillis())
        }
        writableDatabase.insert(TABLE, null, values)
    }

    /** 供大模型使用的最近历史（仅 user/assistant，按时间正序）。 */
    fun recentMessages(limit: Int): List<ChatMessage> {
        val list = ArrayList<ChatMessage>()
        readableDatabase.rawQuery(
            "SELECT role, content FROM $TABLE WHERE role IN ('user','assistant') ORDER BY id DESC LIMIT ?",
            arrayOf(limit.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(ChatMessage(role = cursor.getString(0), content = cursor.getString(1)))
            }
        }
        return list.asReversed()
    }

    /** 供界面展示的全部消息（按时间正序）。 */
    fun all(): List<ChatMessage> {
        val list = ArrayList<ChatMessage>()
        readableDatabase.rawQuery(
            "SELECT role, content FROM $TABLE WHERE role IN ('user','assistant') ORDER BY id ASC",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(ChatMessage(role = cursor.getString(0), content = cursor.getString(1)))
            }
        }
        return list
    }

    fun clear() {
        writableDatabase.execSQL("DELETE FROM $TABLE")
    }

    companion object {
        private const val DB_NAME = "conversation.db"
        private const val DB_VERSION = 1
        private const val TABLE = "messages"
    }
}
