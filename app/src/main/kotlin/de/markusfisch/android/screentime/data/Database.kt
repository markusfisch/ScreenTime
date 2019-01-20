package de.markusfisch.android.screentime.data

import de.markusfisch.android.screentime.BuildConfig

import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.preference.PreferenceManager

import java.util.Calendar

data class Stats(
	val total: Long,
	val count: Int,
	val start: Long,
	val average: Long
) {
	fun currently(now: Long) = total + Math.max(0, now - start)
	fun currentlyInSeconds(now: Long) = currently(now) / 1000L
	fun currentlyColloquial(now: Long) = timeColloquial(currentlyInSeconds(now))
	fun averageColloquial() = timeColloquialPrecisely(average)
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
				ORDER BY $EVENTS_TIMESTAMP DESC
				LIMIT 1)
			UNION
			SELECT * FROM (SELECT
				$EVENTS_TIMESTAMP,
				$EVENTS_NAME
				FROM $EVENTS
				WHERE $EVENTS_TIMESTAMP
					BETWEEN $startOfDay
					AND $endOfDay
				ORDER BY $EVENTS_TIMESTAMP)
			UNION
			SELECT * FROM (SELECT
				$EVENTS_TIMESTAMP,
				$EVENTS_NAME
				FROM $EVENTS
				WHERE $EVENTS_TIMESTAMP > $endOfDay
				ORDER BY $EVENTS_TIMESTAMP
				LIMIT 1)
			""",
			null
		)
		var total = 0L
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
						total += ms
						++count
						start = 0L
					}
					else -> if (BuildConfig.DEBUG) {
						android.util.Log.d(
							"TrackerService",
							"Unknown event: ${cursor.getString(1)}"
						)
					}
				}
			} while (cursor.moveToNext())
		}
		cursor.close()
		return Stats(
			total,
			count,
			if (start > 0L) {
				start
			} else {
				System.currentTimeMillis()
			},
			Math.round(total.toDouble() / count.toDouble() / 1000.0)
		)
	}

	fun insertEvent(timestamp: Long, name: String, battery: Float): Long {
		val cv = ContentValues()
		cv.put(EVENTS_TIMESTAMP, timestamp)
		cv.put(EVENTS_NAME, name)
		cv.put(EVENTS_BATTERY, battery)
		return db.insert(EVENTS, null, cv)
	}

	private class OpenHelper(context: Context) :
		SQLiteOpenHelper(context, "events.db", null, 2) {
		override fun onCreate(db: SQLiteDatabase) {
			createEvents(db)
		}

		override fun onUpgrade(
			db: SQLiteDatabase,
			oldVersion: Int,
			newVersion: Int
		) {
			if (oldVersion < 2) {
				addBatteryLevel(db)
			}
		}
	}

	companion object {
		const val EVENT_SCREEN_ON = "screen_on"
		const val EVENT_SCREEN_OFF = "screen_off"

		const val EVENTS = "events"
		const val EVENTS_ID = "_id"
		const val EVENTS_TIMESTAMP = "_timestamp"
		const val EVENTS_NAME = "name"
		const val EVENTS_BATTERY = "battery"

		private fun createEvents(db: SQLiteDatabase) {
			db.execSQL("DROP TABLE IF EXISTS $EVENTS")
			db.execSQL(
				"""CREATE TABLE $EVENTS (
					$EVENTS_ID INTEGER PRIMARY KEY AUTOINCREMENT,
					$EVENTS_TIMESTAMP TIMESTAMP,
					$EVENTS_NAME TEXT NOT NULL,
					$EVENTS_BATTERY REAL)"""
			)
		}

		private fun addBatteryLevel(db: SQLiteDatabase) {
			db.execSQL("ALTER TABLE $EVENTS ADD COLUMN $EVENTS_BATTERY REAL")
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

fun timeColloquialPrecisely(seconds: Long): String {
	return when (seconds) {
		in 0..59 -> String.format("%ds", seconds)
		60L -> "1m"
		in 61..3599 -> String.format("%dm %ds",
			(seconds / 60) % 60,
			seconds % 60
		)
		3600L -> "1h"
		else -> String.format("%dh %dm %ds",
			seconds / 3600,
			(seconds / 60) % 60,
			seconds % 60
		)
	}
}

fun timeColloquial(seconds: Long): String {
	return when (seconds) {
		in 0..59 -> String.format("%ds", seconds)
		in 60..3599 -> String.format("%dm", (seconds / 60) % 60)
		in 3600..3660 -> "1h"
		else -> String.format("%dh %dm",
			seconds / 3600,
			(seconds / 60) % 60
		)
	}
}
