package de.markusfisch.android.screentime.database

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.floor

const val DAY_IN_MS = 86400000L

fun startOfDay(
	timestamp: Long,
	modify: ((Calendar) -> Unit)? = null
): Long = Calendar.getInstance().run {
	timeInMillis = timestamp
	set(Calendar.HOUR_OF_DAY, 0)
	set(Calendar.MINUTE, 0)
	set(Calendar.SECOND, 0)
	set(Calendar.MILLISECOND, 0)
	modify?.invoke(this)
	return timeInMillis
}

fun dayStart(timestamp: Long, hour: Int, days: Int = 0): Long = startOfDay(timestamp) { cal ->
	cal.set(Calendar.HOUR_OF_DAY, hour)
	if (cal.timeInMillis > timestamp) {
		cal.add(Calendar.DAY_OF_MONTH, -1)
	}
	cal.add(Calendar.DAY_OF_MONTH, days)
	cal.set(Calendar.HOUR_OF_DAY, hour)
}

fun daysBetween(from: Long, to: Long): Int {
	val zone = TimeZone.getDefault()
	fun dayNumber(timestamp: Long) = floor(
		(timestamp + zone.getOffset(timestamp)).toDouble() / DAY_IN_MS
	).toLong()
	return (dayNumber(to) - dayNumber(from)).toInt()
}

// Map elapsed intervals to local clock hours. Split at each hour so skipped
// or repeated hours at daylight-saving transitions do not shift later usage.
fun forEachClockInterval(
	from: Long,
	to: Long,
	hourOffset: Int = 0,
	callback: (start: Long, duration: Long) -> Unit
) {
	val cal = Calendar.getInstance()
	var cursor = from
	while (cursor < to) {
		cal.timeInMillis = cursor
		val time = cal.get(Calendar.HOUR_OF_DAY) * 3600000L +
			cal.get(Calendar.MINUTE) * 60000L +
			cal.get(Calendar.SECOND) * 1000L + cal.get(Calendar.MILLISECOND)
		val duration = minOf(to - cursor, 3600000L - time % 3600000L)
		val start = (time - hourOffset * 3600000L + DAY_IN_MS) % DAY_IN_MS
		callback(start, duration)
		cursor += duration
	}
}

fun timeRangeColloquial(seconds: Long): String = when (seconds) {
	0L -> "0"
	in 1..59 -> "< 1m"
	in 60..3599 -> String.format(
		Locale.getDefault(),
		"%dm",
		(seconds / 60) % 60
	)

	in 3600..3659 -> "1h"
	else -> String.format(
		Locale.getDefault(),
		"%dh %dm",
		seconds / 3600,
		(seconds / 60) % 60
	)
}
