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
	fun averageColloquial() = timeColloquialPrecisely(average)
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
		if (count > 0) {
			(total.toDouble() / count.toDouble() / 1000.0).roundToLong()
		} else {
			0
		}
	)
}

private fun timeColloquialPrecisely(seconds: Long): String = when (seconds) {
	in 0..59 -> String.format("%ds", seconds)
	60L -> "1m"
	in 61..3599 -> String.format(
		"%dm %ds",
		(seconds / 60) % 60,
		seconds % 60
	)
	3600L -> "1h"
	else -> String.format(
		"%dh %dm %ds",
		seconds / 3600,
		(seconds / 60) % 60,
		seconds % 60
	)
}

private fun timeColloquial(seconds: Long): String = when (seconds) {
	in 0..59 -> String.format("%ds", seconds)
	in 60..3599 -> String.format("%dm", (seconds / 60) % 60)
	in 3600..3660 -> "1h"
	else -> String.format(
		"%dh %dm",
		seconds / 3600,
		(seconds / 60) % 60
	)
}
