package de.markusfisch.android.screentime.database

import android.app.Activity
import android.app.Instrumentation
import android.content.ContextWrapper
import android.database.sqlite.SQLiteDatabase
import android.os.Bundle
import de.markusfisch.android.screentime.R
import de.markusfisch.android.screentime.graphics.checkChartBuffers
import java.io.File
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

// Uses platform instrumentation so database regression tests need no extra
// runtime libraries. All fixtures live in a separate debug cache directory.
class DatabaseImportTest : Instrumentation() {
	private val directory by lazy {
		File(targetContext.cacheDir, "import-tests").apply { mkdirs() }
	}
	private val storage by lazy {
		object : ContextWrapper(targetContext) {
			override fun getDatabasePath(name: String) = File(directory, name)
			override fun getFilesDir() = directory
		}
	}

	override fun onCreate(arguments: Bundle?) {
		super.onCreate(arguments)
		start()
	}

	override fun onStart() {
		check(context.packageName.endsWith(".debug.test"))
		check(targetContext.packageName == "de.markusfisch.android.screentime.debug")
		directory.listFiles()?.forEach { it.delete() }
		val database = Database(storage)
		val source = File(storage.filesDir, "fixture.db")
		val tests = listOf(
			"currentFormat" to {
				fixture(source, "_id INTEGER, _from INTEGER, _duration INTEGER", "1, 1000, 500")
				check(database.importDatabase(storage, source.name) == null)
				check(total(database) == 500L)
			},
			"legacyFormatAndDuplicateImport" to {
				fixture(source, "_id INTEGER, _from INTEGER, _to INTEGER", "1, 2000, 2400")
				check(database.importDatabase(storage, source.name) == null)
				check(database.importDatabase(storage, source.name) == null)
				check(total(database) == 900L)
			},
			"growingSessionAndShorterBackup" to {
				fixture(source, "_id INTEGER, _from INTEGER, _duration INTEGER", "1, 1000, 700")
				check(database.importDatabase(storage, source.name) == null)
				check(total(database) == 1100L)
				fixture(source, "_id INTEGER, _from INTEGER, _duration INTEGER", "1, 1000, 100")
				check(database.importDatabase(storage, source.name) == null)
				check(total(database) == 1100L)
			},
			"mergeExistingDuplicates" to {
				SQLiteDatabase.openDatabase(
					storage.getDatabasePath(Database.FILE_NAME).path, null, SQLiteDatabase.OPEN_READWRITE
				).use {
					it.execSQL("INSERT INTO screen_events (_from, _duration) VALUES (1000, 600)")
				}
				fixture(source, "_id INTEGER, _from INTEGER, _duration INTEGER", "1, 1000, 700")
				check(database.importDatabase(storage, source.name) == null)
				check(total(database) == 1100L)
			},
			"missingStart" to {
				fixture(source, "_id INTEGER, _duration INTEGER", "1, 300")
				checkInvalid(database, source)
			},
			"missingDuration" to {
				fixture(source, "_id INTEGER, _from INTEGER", "1, 3000")
				checkInvalid(database, source)
			},
			"emptyInvalidSchema" to {
				fixture(source, "_id INTEGER, _from INTEGER", null)
				checkInvalid(database, source)
			},
			"missingTable" to {
				source.delete()
				SQLiteDatabase.openOrCreateDatabase(source, null).close()
				checkInvalid(database, source)
			},
			"nonSqliteFile" to {
				source.writeText("not a database")
				check(database.importDatabase(storage, source.name) != null)
				check(total(database) == 1100L)
			},
			"overlappingAndAdjacentIntervals" to {
				fixture(source, "_id INTEGER, _from INTEGER, _duration INTEGER", "1, 3000, 2000")
				SQLiteDatabase.openDatabase(source.path, null, SQLiteDatabase.OPEN_READWRITE).use {
					it.execSQL("INSERT INTO screen_events VALUES (2, 4000, 2000), (3, 4500, 100), (4, 6000, 1000)")
				}
				check(database.importDatabase(storage, source.name) == null)
				check(total(database) == 5100L)
				val intervals = mutableListOf<Pair<Long, Long>>()
				database.forEachRecordBetween(3500L, 5500L) { start, duration ->
					intervals.add(start to duration)
				}
				check(intervals == listOf(3500L to 2000L))
				intervals.clear()
				database.forEachRecordBetween(7000L, 7000L) { start, duration ->
					intervals.add(start to duration)
				}
				check(intervals.isEmpty())
			},
			"invalidValuesRollbackWholeImport" to {
				for ((column, value) in listOf(
					"_duration" to "9223372036854775807",
					"_duration" to "-1",
					"_duration" to "1.5",
					"_duration" to "'invalid'",
					"_to" to "-9223372036854775808"
				)) {
					val validTime = if (column == "_to") "9100" else "100"
					fixture(source, "_id INTEGER, _from INTEGER, $column", "1, 9000, $validTime")
					SQLiteDatabase.openDatabase(source.path, null, SQLiteDatabase.OPEN_READWRITE).use {
						it.execSQL("INSERT INTO screen_events VALUES (2, 10000, $value)")
					}
					check(database.importDatabase(storage, source.name) ==
						storage.getString(R.string.import_failed_invalid_database))
					check(total(database) == 5100L)
				}
			},
			"unfinishedRecordsAreSkipped" to {
				fixture(source, "_id INTEGER, _from INTEGER, _duration INTEGER", "1, 9000, NULL")
				check(database.importDatabase(storage, source.name) == null)
				fixture(source, "_id INTEGER, _from INTEGER, _to INTEGER", "1, 9000, NULL")
				check(database.importDatabase(storage, source.name) == null)
				check(total(database) == 5100L)
			},
			"invalidStoredDurationIsIgnored" to {
				SQLiteDatabase.openDatabase(
					storage.getDatabasePath(Database.FILE_NAME).path, null, SQLiteDatabase.OPEN_READWRITE
				).use {
					it.execSQL("INSERT INTO screen_events (_from, _duration) VALUES (8000, 9223372036854775807)")
					check(total(database) == 5100L)
					it.execSQL("DELETE FROM screen_events WHERE _from = 8000")
				}
			},
			"consistentSnapshotDuringWrites" to {
				checkSnapshot(database)
			},
			"discardedFramesPreserveDisplayedBitmaps" to {
				checkChartBuffers()
			}

		)
		var failures = 0
		for ((index, test) in tests.withIndex()) {
			val status = Bundle().apply {
				putString("class", DatabaseImportTest::class.java.name)
				putString("test", test.first)
				putInt("numtests", tests.size)
				putInt("current", index + 1)
			}
			sendStatus(1, status)
			try {
				test.second()
				sendStatus(0, status)
			} catch (failure: Throwable) {
				failures++
				status.putString("stack", failure.stackTraceToString())
				sendStatus(-2, status)
			}
		}
		source.delete()
		finish(Activity.RESULT_OK, Bundle().apply {
			putString("stream", "Import tests: ${tests.size}, failures: $failures")
		})
	}

	private fun checkSnapshot(database: Database) {
		val path = storage.getDatabasePath(Database.FILE_NAME).path
		SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READWRITE).use { source ->
			source.beginTransaction()
			try {
				for (index in 0..<1000) {
					source.execSQL("INSERT INTO screen_events (_from, _duration) VALUES (?, 100)",
						arrayOf(100000L + index * 1000L))
				}
				source.setTransactionSuccessful()
			} finally {
				source.endTransaction()
			}
		}
		val failure = AtomicReference<Throwable?>()
		val writer = thread {
			try {
				SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READWRITE).use { source ->
					for (index in 0..<20) {
						source.execSQL("UPDATE screen_events SET _duration = ? WHERE _from >= 100000",
							arrayOf(if (index % 2 == 0) 100 else 200))
					}
				}
			} catch (error: Throwable) {
				failure.set(error)
			}
		}
		try {
			repeat(3) {
				val output = ByteArrayOutputStream()
				database.writeSnapshot(storage, output)
				val file = File(directory, "snapshot.db")
				file.writeBytes(output.toByteArray())
				SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { snapshot ->
					check(snapshot.version == 4)
					snapshot.rawQuery("PRAGMA integrity_check", null).use {
						check(it.moveToFirst() && it.getString(0) == "ok")
					}
					snapshot.rawQuery(
						"SELECT COUNT(*), MIN(_duration), MAX(_duration) FROM screen_events WHERE _from >= 100000",
						null
					).use {
						check(it.moveToFirst() && it.getInt(0) == 1000)
						check(it.getLong(1) == it.getLong(2))
						check(it.getLong(1) in listOf(100L, 200L))
					}
				}
				file.delete()
			}
		} finally {
			writer.join()
		}
		check(failure.get() == null) { failure.get().toString() }
	}

	private fun fixture(file: File, columns: String, values: String?) {
		file.delete()
		SQLiteDatabase.openOrCreateDatabase(file, null).use {
			it.execSQL("CREATE TABLE screen_events ($columns)")
			if (values != null) {
				it.execSQL("INSERT INTO screen_events VALUES ($values)")
			}
		}
	}

	private fun checkInvalid(database: Database, source: File) {
		check(database.importDatabase(storage, source.name) ==
			storage.getString(R.string.import_failed_invalid_database))
		check(total(database) == 1100L)
	}

	private fun total(database: Database): Long {
		var total = 0L
		database.forEachRecordBetween(0L, 10000L) { _, duration -> total += duration }
		return total
	}
}
