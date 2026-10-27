package de.markusfisch.android.screentime.graphics

import org.junit.Assert.assertEquals
import org.junit.Test

class DayIntervalsTest {
	private fun split(days: LongArray, from: Long, to: Long): List<Triple<Int, Long, Long>> {
		val intervals = mutableListOf<Triple<Int, Long, Long>>()
		forEachDayInterval(days, from, to) { day, start, end ->
			intervals.add(Triple(day, start, end))
		}
		return intervals
	}

	@Test fun sessionSpanningFourDaysReachesEveryRow() {
		assertEquals(
			listOf(Triple(0, 50L, 100L), Triple(1, 0L, 100L),
				Triple(2, 0L, 100L), Triple(3, 0L, 50L)),
			split(longArrayOf(0, 100, 200, 300, 400), 50, 350)
		)
	}

	@Test fun midnightBelongsToFollowingDay() {
		assertEquals(listOf(Triple(1, 0L, 50L)), split(longArrayOf(0, 100, 200), 100, 150))
		assertEquals(listOf(Triple(0, 50L, 100L)), split(longArrayOf(0, 100, 200), 50, 100))
	}

	@Test fun clipsToVisibleDaysAndSkipsEmptyIntervals() {
		val days = longArrayOf(100, 200, 300)
		assertEquals(listOf(Triple(0, 0L, 100L), Triple(1, 0L, 100L)), split(days, 0, 400))
		assertEquals(emptyList<Triple<Int, Long, Long>>(), split(days, 300, 300))
		assertEquals(emptyList<Triple<Int, Long, Long>>(), split(days, 300, 400))
	}

	@Test fun preservesShortAndLongDayDurations() {
		val hour = 3600000L
		val intervals = split(longArrayOf(0, 23 * hour, 48 * hour), 22 * hour, 48 * hour)
		assertEquals(listOf(Triple(0, 22 * hour, 23 * hour), Triple(1, 0L, 25 * hour)), intervals)
		assertEquals(26 * hour, intervals.sumOf { it.third - it.second })
	}
}
