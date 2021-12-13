package de.markusfisch.android.screentime.data

import de.markusfisch.android.screentime.app.db
import kotlin.math.max

data class Summary(
	val total: Long,
	val count: Int,
	val start: Long
) {
	fun currently(now: Long) = total + max(0, now - start)
	fun currentlyInSeconds(now: Long) = currently(now) / 1000L
	fun currentlyColloquial(now: Long) = timeRangeColloquial(
		currentlyInSeconds(now)
	)
}

fun summarizeDay(timestamp: Long = System.currentTimeMillis()): Summary {
	var total = 0L
	var count = 0
	val lastStart = db.forEachRecordOfDay(timestamp) { _, duration ->
		total += duration
		++count
	}
	return Summary(
		total,
		count,
		if (lastStart > 0L) {
			lastStart
		} else {
			System.currentTimeMillis()
		}
	)
}
