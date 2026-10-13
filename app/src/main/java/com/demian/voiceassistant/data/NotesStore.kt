package com.demian.voiceassistant.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** 便签存储。语音说「记一下……」时保存到这里。 */
class NotesStore(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
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

    fun add(content: String) {
        val values = ContentValues().apply {
            put("content", content)
            put("ts", System.currentTimeMillis())
        }
        writableDatabase.insert(TABLE, null, values)
    }

    fun list(): List<String> {
        val result = mutableListOf<String>()
        readableDatabase.rawQuery("SELECT content FROM $TABLE ORDER BY id ASC", null).use { cursor ->
            while (cursor.moveToNext()) result.add(cursor.getString(0))
        }
        return result
    }

    companion object {
        private const val DB_NAME = "notes.db"
        private const val DB_VERSION = 1
        private const val TABLE = "notes"
    }
}
