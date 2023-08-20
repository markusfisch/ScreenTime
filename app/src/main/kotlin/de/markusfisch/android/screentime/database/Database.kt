package de.markusfisch.android.screentime.database

import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.SQLException
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import de.markusfisch.android.screentime.R
import de.markusfisch.android.screentime.app.prefs
import java.io.File
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

	fun importDatabase(context: Context, fileName: String): String? {
		return db.importDatabase(context, fileName)
	}

	private class OpenHelper(context: Context) :
		SQLiteOpenHelper(context, FILE_NAME, null, 3) {
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
		const val FILE_NAME = "events.db"

		private const val EVENTS = "screen_events"
		private const val EVENTS_ID = "_id"
		private const val EVENTS_FROM = "_from"
		private const val EVENTS_TO = "_to"

		private const val LEGACY_EVENTS = "events"
		private const val LEGACY_EVENTS_TIMESTAMP = "_timestamp"
		private const val LEGACY_EVENTS_NAME = "name"
		private const val LEGACY_EVENTS_BATTERY = "battery"

		private fun SQLiteDatabase.createEvents() {
			execSQL("DROP TABLE IF EXISTS $EVENTS".trimMargin())
			execSQL(
				"""CREATE TABLE $EVENTS (
							$EVENTS_ID INTEGER PRIMARY KEY AUTOINCREMENT,
							$EVENTS_FROM TIMESTAMP,
							$EVENTS_TO TIMESTAMP)""".trimMargin()
			)
		}

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

		private fun SQLiteDatabase.importDatabase(
			context: Context,
			fileName: String
		): String? {
			var edb: SQLiteDatabase? = null
			return try {
				edb = ImportHelper(
					ExternalDatabaseContext(context),
					fileName
				).readableDatabase
				beginTransaction()
				if (importEventsFrom(edb)) {
					setTransactionSuccessful()
					null
				} else {
					context.getString(R.string.import_failed_unknown)
				}
			} catch (e: SQLException) {
				e.message
			} finally {
				if (inTransaction()) {
					endTransaction()
				}
				edb?.close()
			}
		}

		private fun SQLiteDatabase.importEventsFrom(
			src: SQLiteDatabase
		): Boolean {
			val cursor = src.rawQuery(
				"""SELECT *
				FROM $EVENTS
				ORDER BY $EVENTS_ID""".trimIndent(),
				null
			) ?: return false
			val idIndex = cursor.getColumnIndex(EVENTS_ID)
			val fromIndex = cursor.getColumnIndex(EVENTS_FROM)
			val toIndex = cursor.getColumnIndex(EVENTS_TO)
			var success = true
			if (cursor.moveToFirst()) {
				do {
					val srcId = cursor.getLong(idIndex)
					val from = cursor.getLong(fromIndex)
					val to = cursor.getLong(toIndex)
					if (srcId < 1L || from < 1L || to < 1L ||
						eventExists(from, to)
					) {
						continue
					}
					if (insert(
							EVENTS,
							null,
							ContentValues().apply {
								put(EVENTS_FROM, from)
								put(EVENTS_TO, to)
							}
						) < 1
					) {
						success = false
						break
					}

				} while (cursor.moveToNext())
			}
			cursor.close()
			return success
		}

		private fun SQLiteDatabase.eventExists(from: Long, to: Long): Boolean {
			val cursor = rawQuery(
				"""SELECT $EVENTS_ID
					FROM $EVENTS
					WHERE $EVENTS_FROM = ?
						AND $EVENTS_TO = ?
					LIMIT 1""".trimMargin(),
				arrayOf(from.toString(), to.toString())
			) ?: return false
			val exists = cursor.moveToFirst() && cursor.count > 0
			cursor.close()
			return exists
		}
	}
}

private class ImportHelper constructor(context: Context, path: String) :
	SQLiteOpenHelper(context, path, null, 1) {
	override fun onCreate(db: SQLiteDatabase) {
		// Do nothing.
	}

	override fun onDowngrade(
		db: SQLiteDatabase,
		oldVersion: Int,
		newVersion: Int
	) {
		// Do nothing, but without that method we cannot open
		// different versions.
	}

	override fun onUpgrade(
		db: SQLiteDatabase,
		oldVersion: Int,
		newVersion: Int
	) {
		// Do nothing, but without that method we cannot open
		// different versions.
	}
}

// Somehow it's required to use this ContextWrapper to access the
// tables in an external database. Without this, the database will
// only contain the table "android_metadata".
private class ExternalDatabaseContext(base: Context?) : ContextWrapper(base) {
	override fun getDatabasePath(name: String) = File(filesDir, name)

	override fun openOrCreateDatabase(
		name: String,
		mode: Int,
		factory: SQLiteDatabase.CursorFactory,
		errorHandler: DatabaseErrorHandler?
	): SQLiteDatabase = openOrCreateDatabase(name, mode, factory)

	override fun openOrCreateDatabase(
		name: String,
		mode: Int,
		factory: SQLiteDatabase.CursorFactory
	): SQLiteDatabase = SQLiteDatabase.openOrCreateDatabase(
		getDatabasePath(name), null
	)
}
