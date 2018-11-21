package de.markusfisch.android.screentime.data

import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.preference.PreferenceManager

import java.util.Calendar

data class Stats(
	val millisecs: Long,
	val count: Int,
	val start: Long,
	val average: Int
) {
	fun duration(now: Long) = millisecs + now - start
	fun durationInSeconds(now: Long) = duration(now) / 1000L
}

class Database {
	private lateinit var preferences: SharedPreferences
	private lateinit var db: SQLiteDatabase

	fun open(context: Context) {
		preferences = PreferenceManager.getDefaultSharedPreferences(context)
		db = OpenHelper(context).writableDatabase
	}

	fun getStatsOfDay(timestamp: Long): Stats {
		val startOfDay = getStartOfDay(timestamp)
		val endOfDay = getEndOfDay(timestamp)
		val cursor = db.rawQuery("""
			SELECT * FROM (SELECT
				$EVENTS_TIMESTAMP,
				$EVENTS_NAME
				FROM $EVENTS
				WHERE $EVENTS_TIMESTAMP < $startOfDay
				LIMIT 1)
			UNION
			SELECT
				$EVENTS_TIMESTAMP,
				$EVENTS_NAME
				FROM $EVENTS
				WHERE $EVENTS_TIMESTAMP
					BETWEEN $startOfDay
					AND $endOfDay
			UNION
			SELECT * FROM (SELECT
				$EVENTS_TIMESTAMP,
				$EVENTS_NAME
				FROM $EVENTS
				WHERE $EVENTS_TIMESTAMP > $endOfDay
				LIMIT 1)
			""",
			null
		)
		var millisecs = 0L
		var count = 0
		var start = 0L
		if (cursor.moveToFirst()) {
			do {
				val ts = cursor.getLong(0)
				when (cursor.getString(1)) {
					EVENT_SCREEN_ON -> if (ts < endOfDay) {
						start = Math.max(startOfDay, ts)
					}
					EVENT_SCREEN_OFF -> if (start > 0L) {
						val ms = Math.min(endOfDay, ts) - start
						millisecs += ms
						++count
						start = 0L
					}
					else -> throw IllegalArgumentException(
						"Unknown event: ${cursor.getString(1)}"
					)
				}
			} while (cursor.moveToNext())
		}
		cursor.close()
		return Stats(
			millisecs,
			count,
			if (start > 0L) {
				start
			} else {
				System.currentTimeMillis()
			},
			Math.round(millisecs.toFloat() / count.toFloat() / 1000f)
		)
	}

	fun insertEvent(timestamp: Long, name: String): Long {
		val cv = ContentValues()
		cv.put(EVENTS_TIMESTAMP, timestamp)
		cv.put(EVENTS_NAME, name)
		return db.insert(EVENTS, null, cv)
	}

	fun deleteEvent(id: Long) {
		db.delete(EVENTS, "$EVENTS_ID = ?", arrayOf("$id"))
	}

	private class OpenHelper(context: Context) :
		SQLiteOpenHelper(context, "events.db", null, 1) {
		override fun onCreate(db: SQLiteDatabase) {
			createEvents(db)
		}

		override fun onUpgrade(
			db: SQLiteDatabase,
			oldVersion: Int,
			newVersion: Int
		) {
		}
	}

	companion object {
		const val EVENT_SCREEN_ON = "screen_on"
		const val EVENT_SCREEN_OFF = "screen_off"

		const val EVENTS = "events"
		const val EVENTS_ID = "_id"
		const val EVENTS_TIMESTAMP = "_timestamp"
		const val EVENTS_NAME = "name"

		private fun createEvents(db: SQLiteDatabase) {
			db.execSQL("DROP TABLE IF EXISTS $EVENTS")
			db.execSQL(
				"""CREATE TABLE $EVENTS (
					$EVENTS_ID INTEGER PRIMARY KEY AUTOINCREMENT,
					$EVENTS_TIMESTAMP TIMESTAMP,
					$EVENTS_NAME TEXT NOT NULL)"""
			)
		}
	}
}

fun getStartOfDay(timestamp: Long): Long {
	val cal = Calendar.getInstance()
	cal.timeInMillis = timestamp
	cal.set(Calendar.HOUR_OF_DAY, 0)
	cal.set(Calendar.MINUTE, 0)
	cal.set(Calendar.SECOND, 0)
	return cal.timeInMillis
}

fun getEndOfDay(timestamp: Long): Long {
	val cal = Calendar.getInstance()
	cal.timeInMillis = timestamp
	cal.set(Calendar.HOUR_OF_DAY, 23)
	cal.set(Calendar.MINUTE, 59)
	cal.set(Calendar.SECOND, 59)
	return cal.timeInMillis
}
