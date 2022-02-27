package de.markusfisch.android.screentime.data

import java.util.*

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

fun timeRangeColloquial(seconds: Long): String = when (seconds) {
	0L -> "0"
	in 1..59 -> "< 1m"
	in 60..3599 -> String.format("%dm", (seconds / 60) % 60)
	in 3600..3660 -> "1h"
	else -> String.format(
		"%dh %dm",
		seconds / 3600,
		(seconds / 60) % 60
	)
}
