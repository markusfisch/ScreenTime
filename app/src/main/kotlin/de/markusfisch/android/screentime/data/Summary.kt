package de.markusfisch.android.screentime.data

import de.markusfisch.android.screentime.app.db
import kotlin.math.max
import kotlin.math.roundToLong

data class Summary(
	val total: Long,
	val count: Int,
	val start: Long,
	val average: Long
) {
	fun currently(now: Long) = total + max(0, now - start)
	fun currentlyInSeconds(now: Long) = currently(now) / 1000L
	fun currentlyColloquial(now: Long) = timeColloquial(currentlyInSeconds(now))
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
		},
		(total.toDouble() / max(1.0, count.toDouble()) / 1000.0).roundToLong()
	)
}

fun timeColloquial(seconds: Long): String = when (seconds) {
	in 0..59 -> String.format("%ds", seconds)
	in 60..3599 -> String.format("%dm", (seconds / 60) % 60)
	in 3600..3660 -> "1h"
	else -> String.format(
		"%dh %dm",
		seconds / 3600,
		(seconds / 60) % 60
	)
}
