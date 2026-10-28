package de.markusfisch.android.screentime.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecordTest {
	@Test fun calculatesValidEnd() {
		assertEquals(1500L, validRecordEnd(1000L, 500L))
		assertEquals(Long.MAX_VALUE, validRecordEnd(Long.MAX_VALUE - 1L, 1L))
	}

	@Test fun rejectsOverflowingDuration() {
		assertNull(validRecordEnd(1700000000000L, Long.MAX_VALUE))
		assertNull(validRecordEnd(Long.MAX_VALUE, 1L))
	}

	@Test fun rejectsNegativeAndEmptyRecords() {
		assertNull(validRecordEnd(-1L, 1L))
		assertNull(validRecordEnd(Long.MIN_VALUE, Long.MAX_VALUE))
		assertNull(validRecordEnd(1000L, 0L))
		assertNull(validRecordEnd(1000L, -1L))
	}
}
