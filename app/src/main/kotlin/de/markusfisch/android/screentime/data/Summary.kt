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

private fun timeRangeColloquial(seconds: Long): String = when (seconds) {
	in 0..59 -> "< 1m"
	in 60..3599 -> String.format("%dm", (seconds / 60) % 60)
	in 3600..3660 -> "1h"
	else -> String.format(
		"%dh %dm",
		seconds / 3600,
		(seconds / 60) % 60
	)
}
