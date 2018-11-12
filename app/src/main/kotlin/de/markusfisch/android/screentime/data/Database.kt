package de.markusfisch.android.screentime.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Stats(val millisecs: Long, val count: Int)

class Database {
	private lateinit var db: SQLiteDatabase

	fun open(context: Context) {
		db = OpenHelper(context).writableDatabase
	}

	fun getStatsOfDay(timestamp: Long): Stats {
		val cursor = db.rawQuery(
			"""SELECT
				SUM($TIMES_TO - $TIMES_FROM),
				COUNT(*)
				FROM $TIMES
				WHERE $TIMES_TO ${getDayBounds(timestamp)}""",
			null
		)
		var millisecs = 0L
		var count = 0
		if (cursor.moveToFirst()) {
			millisecs = cursor.getLong(0)
			count = cursor.getInt(1)
		}
		cursor.close()
		return Stats(millisecs, count)
	}

	fun getTimes(timestamp: Long): Cursor {
		return db.rawQuery(
			"""SELECT
				$TIMES_ID,
				$TIMES_FROM,
				$TIMES_TO
				FROM $TIMES
				WHERE $TIMES_TO ${getDayBounds(timestamp)}
				ORDER BY $TIMES_TO""", null
		)
	}

	fun insertTime(from: Long, to: Long): Long {
		if (to <= from) {
			return -1
		}
		val cv = ContentValues()
		cv.put(TIMES_FROM, from)
		cv.put(TIMES_TO, to)
		return db.insert(TIMES, null, cv)
	}

	fun removeTime(id: Long) {
		db.delete(TIMES, "$TIMES_ID = ?", arrayOf("$id"))
	}

	private class OpenHelper(context: Context) :
		SQLiteOpenHelper(context, "times.db", null, 1) {
		override fun onCreate(db: SQLiteDatabase) {
			createTimes(db)
		}

		override fun onUpgrade(
			db: SQLiteDatabase,
			oldVersion: Int,
			newVersion: Int
		) {
		}
	}

	companion object {
		const val TIMES = "times"
		const val TIMES_ID = "_id"
		const val TIMES_FROM = "_from"
		const val TIMES_TO = "_to"

		private fun createTimes(db: SQLiteDatabase) {
			db.execSQL("DROP TABLE IF EXISTS $TIMES")
			db.execSQL(
				"""CREATE TABLE $TIMES (
					$TIMES_ID INTEGER PRIMARY KEY AUTOINCREMENT,
					$TIMES_FROM TIMESTAMP,
					$TIMES_TO TIMESTAMP)"""
			)
		}
	}
}

fun getDayBounds(timestamp: Long): String {
	val date = getDateString(timestamp)
	return """BETWEEN strftime('%s', '$date 00:00:00') * 1000
		AND strftime('%s', '$date 23:59:59') * 1000"""
}

fun getDateString(timestamp: Long): String {
	return SimpleDateFormat(
		"yyyy-MM-dd",
		Locale.US
	).format(Date(timestamp))
}
