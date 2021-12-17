package de.markusfisch.android.screentime.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

class Database {
	private lateinit var db: SQLiteDatabase

	fun open(context: Context) {
		db = OpenHelper(context).writableDatabase
	}

	fun getAvailableHistoryInDays(now: Long): Int {
		val earliestTimestamp = db.getEarliestTimestamp()
		if (earliestTimestamp > -1) {
			val msBetween = endOfDay(now) - startOfDay(earliestTimestamp)
			return ceil(msBetween / 86400000.0).toInt()
		}
		return 0
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
							callback(start, min(to, ts) - start)
							start = 0L
						}
					}
				} while (it.moveToNext())
			}
		}
		// Add currently running session.
		val now = System.currentTimeMillis()
		if (start > 0L && now < to) {
			callback(start, now - start)
		}
		return start
	}

	fun insertScreenEvent(
		timestamp: Long,
		screenOn: Boolean,
		battery: Float
	): Long = insertEvent(
		timestamp,
		if (screenOn) {
			EVENT_SCREEN_ON
		} else {
			EVENT_SCREEN_OFF
		},
		battery
	)

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
			db.createEvents()
		}

		override fun onUpgrade(
			db: SQLiteDatabase,
			oldVersion: Int,
			newVersion: Int
		) {
			if (oldVersion < 2) {
				db.addBatteryLevel()
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
	}
}

private fun SQLiteDatabase.createEvents() {
	execSQL("DROP TABLE IF EXISTS ${Database.EVENTS}".trimMargin())
	execSQL(
		"""CREATE TABLE ${Database.EVENTS} (
					${Database.EVENTS_ID} INTEGER PRIMARY KEY AUTOINCREMENT,
					${Database.EVENTS_TIMESTAMP} TIMESTAMP,
					${Database.EVENTS_NAME} TEXT NOT NULL,
					${Database.EVENTS_BATTERY} REAL)""".trimMargin()
	)
}

private fun SQLiteDatabase.addBatteryLevel() = execSQL(
	"""ALTER TABLE ${Database.EVENTS}
		 ADD COLUMN ${Database.EVENTS_BATTERY} REAL""".trimMargin()
)

private fun SQLiteDatabase.getEarliestTimestamp(): Long {
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
