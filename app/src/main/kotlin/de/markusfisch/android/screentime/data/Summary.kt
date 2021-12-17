package de.markusfisch.android.screentime.data

import de.markusfisch.android.screentime.app.db

data class Summary(
	val total: Long,
	val count: Int
)

fun summarizeDay(timestamp: Long = System.currentTimeMillis()): Summary {
	var total = 0L
	var count = 0
	db.forEachRecordOfDay(timestamp) { _, duration ->
		total += duration
		++count
	}
	return Summary(total, count)
}
