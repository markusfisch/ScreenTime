package de.markusfisch.android.screentime.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlin.math.max
import kotlin.math.min

class Database {
	var availableHistoryInDays: Int = 0
		private set

	private lateinit var db: SQLiteDatabase

	fun open(context: Context) {
		db = OpenHelper(context).writableDatabase
		val now = System.currentTimeMillis()
		val earliestTimestamp = db.getEariestTimestamp()
		if (earliestTimestamp < 0L) {
			// Insert an initial screen on event if the database is
			// empty because we can only find an empty database if
			// the user has started this app for the first time.
			insertScreenEvent(now, true, 1f)
		}
		val msBetween = endOfDay(now) - endOfDay(earliestTimestamp)
		availableHistoryInDays = (msBetween / 86400000L).toInt()
	}

	fun forEachRecordOfDay(
		timestamp: Long,
		callback: (start: Long, duration: Long) -> Unit
	): Long = forEachRecordBetween(
		startOfDay(timestamp),
		endOfDay(timestamp),
		callback
	)

	fun forEachRecordBetween(
		from: Long,
		to: Long,
		callback: (start: Long, duration: Long) -> Unit
	): Long {
		var start = 0L
		db.getRecordsBetween(from, to).use {
			if (it.moveToFirst()) {
				do {
					val ts = it.getLong(0)
					when (it.getString(1)) {
						EVENT_SCREEN_ON -> if (ts < to) {
							start = max(from, ts)
						}
						EVENT_SCREEN_OFF -> if (start > 0L) {
							callback(
								start - from,
								min(to, ts) - start
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
			db.execSQL("DROP TABLE IF EXISTS $EVENTS".trimMargin())
			db.execSQL(
				"""CREATE TABLE $EVENTS (
					$EVENTS_ID INTEGER PRIMARY KEY AUTOINCREMENT,
					$EVENTS_TIMESTAMP TIMESTAMP,
					$EVENTS_NAME TEXT NOT NULL,
					$EVENTS_BATTERY REAL)""".trimMargin()
			)
		}

		private fun addBatteryLevel(db: SQLiteDatabase) {
			db.execSQL(
				"""ALTER TABLE $EVENTS
					 ADD COLUMN $EVENTS_BATTERY REAL""".trimMargin()
			)
		}
	}
}

private fun SQLiteDatabase.getRecordsBetween(
	from: Long,
	to: Long
): Cursor = rawQuery(
	"""SELECT * FROM (SELECT
			${Database.EVENTS_TIMESTAMP},
			${Database.EVENTS_NAME}
			FROM ${Database.EVENTS}
			WHERE ${Database.EVENTS_TIMESTAMP} < $from
			ORDER BY ${Database.EVENTS_TIMESTAMP} DESC
			LIMIT 1)
		UNION
		SELECT * FROM (SELECT
			${Database.EVENTS_TIMESTAMP},
			${Database.EVENTS_NAME}
			FROM ${Database.EVENTS}
			WHERE ${Database.EVENTS_TIMESTAMP}
				BETWEEN $from
				AND $to
			ORDER BY ${Database.EVENTS_TIMESTAMP})
		UNION
		SELECT * FROM (SELECT
			${Database.EVENTS_TIMESTAMP},
			${Database.EVENTS_NAME}
			FROM ${Database.EVENTS}
			WHERE ${Database.EVENTS_TIMESTAMP} > $to
			ORDER BY ${Database.EVENTS_TIMESTAMP}
			LIMIT 1)""".trimMargin(),
	null
)

private fun SQLiteDatabase.getEariestTimestamp(): Long {
	rawQuery(
		"""SELECT ${Database.EVENTS_TIMESTAMP}
			FROM ${Database.EVENTS}
			ORDER BY ${Database.EVENTS_TIMESTAMP}
			LIMIT 1""".trimMargin(),
		null
	)?.use {
		if (it.moveToFirst()) {
			return it.getLong(0)
		}
	}
	return -1L
}