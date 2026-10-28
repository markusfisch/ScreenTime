package de.markusfisch.android.screentime.database

import org.junit.Assert.assertEquals
import org.junit.Test

class MergedIntervalsTest {
	private fun merge(from: Long, to: Long, vararg records: Pair<Long, Long>): List<Pair<Long, Long>> {
		val result = mutableListOf<Pair<Long, Long>>()
		val intervals = MergedIntervals(from, to) { start, duration -> result.add(start to duration) }
		for ((start, end) in records) {
			intervals.add(start, end)
		}
		intervals.finish()
		return result
	}

	@Test fun overlapsCountOnlyOnce() {
		assertEquals(listOf(10L to 30L), merge(0, 100, 10L to 30L, 20L to 40L))
	}

	@Test fun nestedDuplicateAndAdjacentRecordsMerge() {
		assertEquals(listOf(10L to 40L, 70L to 20L), merge(0, 100,
			10L to 30L, 10L to 30L, 20L to 40L, 25L to 26L, 40L to 50L, 70L to 90L))
	}

	@Test fun clipsToRequestedRange() {
		assertEquals(listOf(15L to 20L), merge(15, 35, 10L to 30L, 20L to 40L))
	}

	@Test fun emptyAndOutOfRangeRecordsProduceNoUsage() {
		assertEquals(emptyList<Pair<Long, Long>>(), merge(10, 20, 0L to 10L, 15L to 15L, 20L to 30L))
		assertEquals(emptyList<Pair<Long, Long>>(), merge(10, 10, 0L to 30L))
		assertEquals(emptyList<Pair<Long, Long>>(), merge(10, 20))
	}
}
