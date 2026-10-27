package de.markusfisch.android.screentime.graphics

internal fun forEachDayInterval(
	dayStarts: LongArray,
	from: Long,
	to: Long,
	callback: (day: Int, start: Long, end: Long) -> Unit
) {
	var day = 0
	while (day < dayStarts.lastIndex && dayStarts[day + 1] <= from) {
		day++
	}
	var start = maxOf(from, dayStarts.first())
	val end = minOf(to, dayStarts.last())
	while (start < end && day < dayStarts.lastIndex) {
		val stop = minOf(end, dayStarts[day + 1])
		callback(day, start - dayStarts[day], stop - dayStarts[day])
		start = stop
		day++
	}
}
