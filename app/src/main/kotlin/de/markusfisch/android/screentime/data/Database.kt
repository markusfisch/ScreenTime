package de.markusfisch.android.screentime.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.*
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

data class Stats(
	val total: Long,
	val count: Int,
	val start: Long,
	val average: Long
) {
	fun currently(now: Long) = total + max(0, now - start)
	fun currentlyInSeconds(now: Long) = currently(now) / 1000L
	fun currentlyColloquial(now: Long) = timeColloquial(currentlyInSeconds(now))
	fun averageColloquial() = timeColloquialPrecisely(average)
}

class Database {
	private lateinit var db: SQLiteDatabase

	fun open(context: Context) {
		db = OpenHelper(context).writableDatabase
	}

	fun getStatsOfDay(timestamp: Long): Stats {
		var total = 0L
		var count = 0
		val lastStart = forEachRowOfDay(timestamp) { _, duration ->
			total += duration
			++count
		}
		return Stats(
			total,
			count,
			if (lastStart > 0L) {
				lastStart
			} else {
				System.currentTimeMillis()
			},
			(total.toDouble() / count.toDouble() / 1000.0).roundToLong()
		)
	}

	private fun forEachRowOfDay(
		timestamp: Long,
		callback: (start: Long, duration: Long) -> Unit
	): Long {
		val startOfDay = getStartOfDay(timestamp)
		val endOfDay = getEndOfDay(timestamp)
		var start = 0L
		db.getRecordsBetween(startOfDay, endOfDay).use {
			if (it.moveToFirst()) {
				do {
					val ts = it.getLong(0)
					when (it.getString(1)) {
						EVENT_SCREEN_ON -> if (ts < endOfDay) {
							start = max(startOfDay, ts)
						}
						EVENT_SCREEN_OFF -> if (start > 0L) {
							callback(
								start - startOfDay,
								min(endOfDay, ts) - start
							)
							start = 0L
						}
					}
				} while (it.moveToNext())
			}
		}
		return start
	}

	fun insertScreenEvent(
		timestamp: Long,
		screenOn: Boolean,
		battery: Float
	) {
		insertEvent(
			timestamp,
			if (screenOn) {
				EVENT_SCREEN_ON
			} else {
				EVENT_SCREEN_OFF
			},
			battery
		)
	}

	private fun insertEvent(
		timestamp: Long,
		name: String,
		battery: Float
	): Long = db.insert(
		EVENTS,
		null,
		ContentValues().apply {
			put(EVENTS_TIMESTAMP, timestamp)
			put(EVENTS_NAME, name)
			put(EVENTS_BATTERY, battery)
		}
	)

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
		const val EVENTS = "events"
		const val EVENTS_ID = "_id"
		const val EVENTS_TIMESTAMP = "_timestamp"
		const val EVENTS_NAME = "name"
		const val EVENTS_BATTERY = "battery"

		private const val EVENT_SCREEN_ON = "screen_on"
		private const val EVENT_SCREEN_OFF = "screen_off"

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

fun getStartOfDay(timestamp: Long): Long = Calendar.getInstance().run {
	timeInMillis = timestamp
	set(Calendar.HOUR_OF_DAY, 0)
	set(Calendar.MINUTE, 0)
	set(Calendar.SECOND, 0)
	return timeInMillis
}

fun getEndOfDay(timestamp: Long): Long = Calendar.getInstance().run {
	timeInMillis = timestamp
	set(Calendar.HOUR_OF_DAY, 23)
	set(Calendar.MINUTE, 59)
	set(Calendar.SECOND, 59)
	return timeInMillis
}

fun timeColloquialPrecisely(seconds: Long): String = when (seconds) {
	in 0..59 -> String.format("%ds", seconds)
	60L -> "1m"
	in 61..3599 -> String.format(
		"%dm %ds",
		(seconds / 60) % 60,
		seconds % 60
	)
	3600L -> "1h"
	else -> String.format(
		"%dh %dm %ds",
		seconds / 3600,
		(seconds / 60) % 60,
		seconds % 60
	)
}

fun timeColloquial(seconds: Long): String = when (seconds) {
	in 0..59 -> String.format("%ds", seconds)
	in 60..3599 -> String.format("%dm", (seconds / 60) % 60)
	in 3600..3660 -> "1h"
	else -> String.format(
		"%dh %dm",
		seconds / 3600,
		(seconds / 60) % 60
	)
}

private fun SQLiteDatabase.getRecordsBetween(
	startOfDay: Long,
	endOfDay: Long
): Cursor = rawQuery(
	"""
		SELECT * FROM (SELECT
			${Database.EVENTS_TIMESTAMP},
			${Database.EVENTS_NAME}
			FROM ${Database.EVENTS}
			WHERE ${Database.EVENTS_TIMESTAMP} < $startOfDay
			ORDER BY ${Database.EVENTS_TIMESTAMP} DESC
			LIMIT 1)
		UNION
		SELECT * FROM (SELECT
			${Database.EVENTS_TIMESTAMP},
			${Database.EVENTS_NAME}
			FROM ${Database.EVENTS}
			WHERE ${Database.EVENTS_TIMESTAMP}
				BETWEEN $startOfDay
				AND $endOfDay
			ORDER BY ${Database.EVENTS_TIMESTAMP})
		UNION
		SELECT * FROM (SELECT
			${Database.EVENTS_TIMESTAMP},
			${Database.EVENTS_NAME}
			FROM ${Database.EVENTS}
			WHERE ${Database.EVENTS_TIMESTAMP} > $endOfDay
			ORDER BY ${Database.EVENTS_TIMESTAMP}
			LIMIT 1)
		""",
	null
)
