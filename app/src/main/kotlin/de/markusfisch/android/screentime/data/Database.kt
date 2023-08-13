package de.markusfisch.android.screentime.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import de.markusfisch.android.screentime.app.prefs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

class Database(context: Context) {
	private val db: SQLiteDatabase = OpenHelper(context).writableDatabase

	fun getAvailableHistoryInDays(
		now: Long = System.currentTimeMillis()
	): Int {
		val earliestTimestamp = db.getEarliestTimestamp()
		if (earliestTimestamp > -1) {
			val msBetween = prefs.dayStart(now) - prefs.dayStart(earliestTimestamp)
			return ceil(msBetween / 86400000.0).toInt()
		}
		return 0
	}

	fun summarizeDay(timestamp: Long): Long {
		var total = 0L
		forEachRecordOfDay(timestamp) { _, duration ->
			total += duration
		}
		return total
	}

	private fun forEachRecordOfDay(
		timestamp: Long,
		callback: (start: Long, duration: Long) -> Unit
	) {
		val dayStart = prefs.dayStart(timestamp)
		val dayEnd = dayStart + DAY_IN_MS
		return forEachRecordBetween(dayStart, dayEnd, callback)
	}

	fun forEachRecordBetween(
		from: Long,
		to: Long,
		callback: (start: Long, duration: Long) -> Unit
	) {
		db.rawQuery(
			"""SELECT * FROM $EVENTS
				WHERE $EVENTS_TO >= $from
					AND $EVENTS_FROM <= $to
				ORDER BY $EVENTS_FROM""",
			null
		).use {
			val fromIndex = it.getColumnIndex(EVENTS_FROM)
			val toIndex = it.getColumnIndex(EVENTS_TO)
			if (it.moveToFirst()) {
				do {
					val start = max(from, it.getLong(fromIndex))
					val stop = min(to, it.getLong(toIndex))
					callback(start, stop - start)
				} while (it.moveToNext())
			}
		}
	}

	fun insertEvent(from: Long): Long = db.insert(
		EVENTS,
		null,
		ContentValues().apply {
			put(EVENTS_FROM, from)
		}
	)

	fun updateEvent(id: Long, to: Long): Int = db.update(
		EVENTS,
		ContentValues().apply {
			put(EVENTS_TO, to)
		},
		"$EVENTS_ID = ?",
		arrayOf(id.toString())
	)

	private class OpenHelper(context: Context) :
		SQLiteOpenHelper(context, "events.db", null, 3) {
		override fun onCreate(db: SQLiteDatabase) {
			db.createEvents()
		}

		override fun onUpgrade(
			db: SQLiteDatabase,
			oldVersion: Int,
			newVersion: Int
		) {
			if (oldVersion < 2) {
				db.addBatteryColumn()
			}
			if (oldVersion < 3) {
				db.createEvents()
				db.migrateTo3()
			}
		}
	}

	companion object {
		private const val EVENTS = "screen_events"
		private const val EVENTS_ID = "_id"
		private const val EVENTS_FROM = "_from"
		private const val EVENTS_TO = "_to"

		private const val LEGACY_EVENTS = "events"
		private const val LEGACY_EVENTS_TIMESTAMP = "_timestamp"
		private const val LEGACY_EVENTS_NAME = "name"
		private const val LEGACY_EVENTS_BATTERY = "battery"

		private fun SQLiteDatabase.addBatteryColumn() = execSQL(
			"""ALTER TABLE $LEGACY_EVENTS
				ADD COLUMN $LEGACY_EVENTS_BATTERY REAL""".trimMargin()
		)

		private fun SQLiteDatabase.migrateTo3() {
			var start = 0L
			rawQuery(
				"""SELECT * FROM (SELECT
					$LEGACY_EVENTS_TIMESTAMP,
					$LEGACY_EVENTS_NAME
					FROM $LEGACY_EVENTS
					ORDER BY $LEGACY_EVENTS_TIMESTAMP)""".trimMargin(),
				null
			)?.use {
				if (it.moveToFirst()) {
					do {
						val ts = it.getLong(0)
						when (it.getString(1)) {
							"screen_on" -> start = ts
							"screen_off" -> if (start > 0L) {
								insert(
									EVENTS,
									null,
									ContentValues().apply {
										put(EVENTS_FROM, start)
										put(EVENTS_TO, ts)
									}
								)
								start = 0L
							}
						}
					} while (it.moveToNext())
				}
			}
			execSQL("DROP TABLE IF EXISTS $LEGACY_EVENTS".trimMargin())
		}

		private fun SQLiteDatabase.createEvents() {
			execSQL("DROP TABLE IF EXISTS $EVENTS".trimMargin())
			execSQL(
				"""CREATE TABLE $EVENTS (
							$EVENTS_ID INTEGER PRIMARY KEY AUTOINCREMENT,
							$EVENTS_FROM TIMESTAMP,
							$EVENTS_TO TIMESTAMP)""".trimMargin()
			)
		}

		private fun SQLiteDatabase.getEarliestTimestamp(): Long {
			rawQuery(
				"""SELECT $EVENTS_FROM
					FROM $EVENTS
					ORDER BY $EVENTS_FROM
					LIMIT 1""".trimMargin(),
				null
			)?.use {
				if (it.moveToFirst()) {
					return it.getLong(0)
				}
			}
			return -1L
		}
	}
}
