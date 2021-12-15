package de.markusfisch.android.screentime.data

import de.markusfisch.android.screentime.app.db
import kotlin.math.max

data class Summary(
	val total: Long,
	val count: Int,
	val lastUpdate: Long
) {
	fun currently(now: Long) = total + max(0, now - lastUpdate)
	fun currentlyInSeconds(now: Long) = currently(now) / 1000L
	fun currentlyColloquial(now: Long) = timeRangeColloquial(
		currentlyInSeconds(now)
	)
}

fun summarizeDay(timestamp: Long = System.currentTimeMillis()): Summary {
	var total = 0L
	var count = 0
	db.forEachRecordOfDay(timestamp) { _, duration ->
		total += duration
		++count
	}
	return Summary(
		total,
		count,
		System.currentTimeMillis()
	)
}
