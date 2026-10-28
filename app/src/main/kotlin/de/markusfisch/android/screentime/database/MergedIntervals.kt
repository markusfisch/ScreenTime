package de.markusfisch.android.screentime.database

// Feed records in start order, then finish to emit the last interval.
internal class MergedIntervals(
	private val from: Long,
	private val to: Long,
	private val callback: (start: Long, duration: Long) -> Unit
) {
	private var start = from
	private var end = from

	fun add(recordStart: Long, recordEnd: Long) {
		val clippedStart = maxOf(from, recordStart)
		val clippedEnd = minOf(to, recordEnd)
		if (clippedStart >= clippedEnd) {
			return
		}
		if (clippedStart > end) {
			finish()
			start = clippedStart
		}
		end = maxOf(end, clippedEnd)
	}

	fun finish() {
		if (end > start) {
			callback(start, end - start)
		}
	}
}
