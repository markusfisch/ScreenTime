package de.markusfisch.android.screentime.os

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageClockTest {
	private val previous = UsageClock(10000000L, 1000000L, 7)

	@Test fun resumeAfterServiceInterruption() {
		assertTrue(UsageClock(10060000L, 1060000L, 7).canReplayFrom(previous))
	}

	@Test fun rejectForwardAndBackwardClockChanges() {
		assertFalse(UsageClock(13660000L, 1060000L, 7).canReplayFrom(previous))
		assertFalse(UsageClock(6460000L, 1060000L, 7).canReplayFrom(previous))
	}

	@Test fun rejectRebootEvenWhenUptimeHasCaughtUp() {
		assertFalse(UsageClock(10060000L, 1060000L, 8).canReplayFrom(previous))
		assertFalse(UsageClock(10060000L, 60000L, 7).canReplayFrom(previous))
	}

	@Test fun rejectLegacyAndExpiredCheckpoints() {
		assertFalse(previous.canReplayFrom(UsageClock(10000000L, -1L, -1)))
		assertFalse(UsageClock(100000000L, 91000000L, 7).canReplayFrom(previous))
	}
}
