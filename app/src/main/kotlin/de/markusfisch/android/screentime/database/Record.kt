package de.markusfisch.android.screentime.database

internal fun validRecordEnd(from: Long, duration: Long): Long? {
	if (from < 0L || duration <= 0L || duration > Long.MAX_VALUE - from) {
		return null
	}
	return from + duration
}
