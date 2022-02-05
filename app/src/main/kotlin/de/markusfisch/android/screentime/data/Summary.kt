package de.markusfisch.android.screentime.data

import de.markusfisch.android.screentime.app.db

fun summarizeDay(timestamp: Long = System.currentTimeMillis()): Long {
	var total = 0L
	db.forEachRecordOfDay(timestamp) { _, duration ->
		total += duration
	}
	return total
}
