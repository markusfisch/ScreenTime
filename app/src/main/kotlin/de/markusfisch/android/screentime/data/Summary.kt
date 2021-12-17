package de.markusfisch.android.screentime.data

import de.markusfisch.android.screentime.app.db

data class Summary(
	val total: Long,
	val ongoingSince: Long
)

fun summarizeDay(timestamp: Long = System.currentTimeMillis()): Summary {
	var total = 0L
	val ongoingSince = db.forEachRecordOfDay(timestamp) { _, duration ->
		total += duration
	}
	return Summary(total, ongoingSince)
}
