package de.markusfisch.android.screentime.os

import kotlin.math.abs

internal data class UsageClock(
	val timestamp: Long,
	val elapsed: Long,
	val bootCount: Int
) {
	fun canReplayFrom(previous: UsageClock): Boolean {
		val wallDelta = timestamp - previous.timestamp
		val elapsedDelta = elapsed - previous.elapsed
		return previous.elapsed >= 0L && bootCount == previous.bootCount &&
			elapsedDelta >= 0L && wallDelta in 0L..86400000L &&
			abs(wallDelta - elapsedDelta) <= 1000L
	}
}
