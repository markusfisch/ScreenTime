package de.markusfisch.android.screentime.data

import java.util.*

fun startOfDay(timestamp: Long): Long = Calendar.getInstance().run {
	timeInMillis = timestamp
	set(Calendar.HOUR_OF_DAY, 0)
	set(Calendar.MINUTE, 0)
	set(Calendar.SECOND, 0)
	return timeInMillis
}

fun endOfDay(timestamp: Long): Long = Calendar.getInstance().run {
	timeInMillis = timestamp
	set(Calendar.HOUR_OF_DAY, 23)
	set(Calendar.MINUTE, 59)
	set(Calendar.SECOND, 59)
	return timeInMillis
}
