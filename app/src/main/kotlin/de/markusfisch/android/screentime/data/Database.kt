package de.markusfisch.android.screentime.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.SQLException
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Stats(val time: Long, val count: Int)

class Database {
	private lateinit var db: SQLiteDatabase

	fun open(context: Context) {
		db = OpenHelper(context).writableDatabase
	}

	fun getStatsOfDay(timestamp: Long): Stats {
		val date = Date(timestamp);
		val from = SimpleDateFormat(
			"yyyy-MM-dd 00:00:00",
			Locale.US
		).format(date)
		val to = SimpleDateFormat(
			"yyyy-MM-dd 23:59:59",
			Locale.US
		).format(date)
		val cursor = db.rawQuery(
			"""SELECT
				SUM(strftime('%s', $TIMES_TO) - strftime('%s', $TIMES_FROM)),
				COUNT(*)
				FROM $TIMES
				WHERE $TIMES_TO BETWEEN '$from' AND '$to'""",
			null
		)
		var time = 0L
		var count = 0
		if (cursor.moveToFirst()) {
			time = cursor.getLong(0)
			count = cursor.getInt(1)
		}
		cursor.close()
		return Stats(time, count)
	}

	fun getTimes(): Cursor {
		return db.rawQuery(
			"""SELECT
				d.$TIMES_ID,
				strftime('%s', d.$TIMES_FROM, 'utc')
					AS $TIMES_FROM,
				strftime('%s', d.$TIMES_TO, 'utc')
					AS $TIMES_TO
				FROM $TIMES AS d
				ORDER BY d.$TIMES_TO DESC""", null
		)
	}

	fun insertTime(from: Long, to: Long): Long {
		val cv = ContentValues()
		cv.put(TIMES_FROM, getDateTimeString(from))
		cv.put(TIMES_TO, getDateTimeString(to))
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
					$TIMES_FROM DATETIME,
					$TIMES_TO DATETIME)"""
			)
		}

		private fun getDateTimeString(timestamp: Long): String {
			return SimpleDateFormat(
				"yyyy-MM-dd HH:mm:ss",
				Locale.US
			).format(Date(timestamp))
		}
	}
}
