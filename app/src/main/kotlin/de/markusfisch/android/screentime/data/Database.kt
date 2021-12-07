package de.markusfisch.android.screentime.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.*
import kotlin.math.max
import kotlin.math.min

class Database {
	private lateinit var db: SQLiteDatabase

	fun open(context: Context) {
		db = OpenHelper(context).writableDatabase
	}

	fun forEachRecordOfDay(
		timestamp: Long,
		callback: (start: Long, duration: Long) -> Unit
	): Long {
		val startOfDay = startOfDay(timestamp)
		val endOfDay = endOfDay(timestamp)
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

private fun startOfDay(timestamp: Long): Long = Calendar.getInstance().run {
	timeInMillis = timestamp
	set(Calendar.HOUR_OF_DAY, 0)
	set(Calendar.MINUTE, 0)
	set(Calendar.SECOND, 0)
	return timeInMillis
}

private fun endOfDay(timestamp: Long): Long = Calendar.getInstance().run {
	timeInMillis = timestamp
	set(Calendar.HOUR_OF_DAY, 23)
	set(Calendar.MINUTE, 59)
	set(Calendar.SECOND, 59)
	return timeInMillis
}

private fun SQLiteDatabase.getRecordsBetween(
	start: Long,
	end: Long
): Cursor = rawQuery(
	"""
		SELECT * FROM (SELECT
			${Database.EVENTS_TIMESTAMP},
			${Database.EVENTS_NAME}
			FROM ${Database.EVENTS}
			WHERE ${Database.EVENTS_TIMESTAMP} < $start
			ORDER BY ${Database.EVENTS_TIMESTAMP} DESC
			LIMIT 1)
		UNION
		SELECT * FROM (SELECT
			${Database.EVENTS_TIMESTAMP},
			${Database.EVENTS_NAME}
			FROM ${Database.EVENTS}
			WHERE ${Database.EVENTS_TIMESTAMP}
				BETWEEN $start
				AND $end
			ORDER BY ${Database.EVENTS_TIMESTAMP})
		UNION
		SELECT * FROM (SELECT
			${Database.EVENTS_TIMESTAMP},
			${Database.EVENTS_NAME}
			FROM ${Database.EVENTS}
			WHERE ${Database.EVENTS_TIMESTAMP} > $end
			ORDER BY ${Database.EVENTS_TIMESTAMP}
			LIMIT 1)
		""",
	null
)
