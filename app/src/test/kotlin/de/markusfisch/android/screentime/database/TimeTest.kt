package de.markusfisch.android.screentime.database

import org.junit.Assert.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class TimeTest {
	private val originalZone = TimeZone.getDefault()

	@Before fun useBerlinTime() {
		TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"))
	}

	@After fun restoreTimeZone() {
		TimeZone.setDefault(originalZone)
	}

	private fun date(month: Int, day: Int, hour: Int = 0): Long =
		Calendar.getInstance().apply {
			clear()
			set(2026, month - 1, day, hour, 0)
		}.timeInMillis

	@Test fun dayBoundariesIncludeShortAndLongDays() {
		assertEquals(23 * 3600000L, dayStart(date(3, 29), 0, 1) - date(3, 29))
		assertEquals(25 * 3600000L, dayStart(date(10, 25), 0, 1) - date(10, 25))
		assertEquals(date(10, 24), dayStart(date(10, 26), 0, -2))
		assertEquals(date(10, 25, 4), dayStart(date(10, 26, 3), 4))
	}

	@Test fun historyCountsCalendarDays() {
		assertEquals(1, daysBetween(date(3, 29), date(3, 30)))
		assertEquals(1, daysBetween(date(10, 25), date(10, 26)))
	}

	@Test fun skippedHourDoesNotShiftLaterUsage() {
		val intervals = mutableListOf<Pair<Long, Long>>()
		forEachClockInterval(date(3, 29, 1), date(3, 29, 4)) { start, duration ->
			intervals.add(start to duration)
		}
		assertEquals(listOf(3600000L to 3600000L, 10800000L to 3600000L), intervals)
	}

	@Test fun repeatedHourPreservesDurationAndClockPosition() {
		val intervals = mutableListOf<Pair<Long, Long>>()
		forEachClockInterval(date(10, 25, 1), date(10, 25, 4)) { start, duration ->
			intervals.add(start to duration)
		}
		assertEquals(listOf(1L, 2L, 2L, 3L).map { it * 3600000L }, intervals.map { it.first })
		assertEquals(4 * 3600000L, intervals.sumOf { it.second })
	}

	@Test fun clockIntervalsRespectDayChangeAndMidnight() {
		val intervals = mutableListOf<Pair<Long, Long>>()
		forEachClockInterval(date(10, 26, 23), date(10, 27, 1), 4) { start, duration ->
			intervals.add(start to duration)
		}
		assertEquals(listOf(19L, 20L).map { it * 3600000L }, intervals.map { it.first })
	}
	@Test fun displaysTheFirstMinuteAfterAnHour() {
		assertEquals("1h", timeRangeColloquial(3600L))
		assertEquals("1h", timeRangeColloquial(3659L))
		val expected = String.format(Locale.getDefault(), "%dh %dm", 1, 1)
		assertEquals(expected, timeRangeColloquial(3660L))
		assertEquals(expected, timeRangeColloquial(3661L))
	}

}
