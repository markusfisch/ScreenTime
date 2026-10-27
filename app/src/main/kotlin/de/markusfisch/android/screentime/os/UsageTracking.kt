package de.markusfisch.android.screentime.os

import android.app.AppOpsManager
import android.app.KeyguardManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.provider.Settings
import de.markusfisch.android.screentime.app.db

fun Context.hasUsageAccess(): Boolean =
	Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
		(getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager)
			.checkOpNoThrow(
				AppOpsManager.OPSTR_GET_USAGE_STATS,
				Process.myUid(), packageName
			) == AppOpsManager.MODE_ALLOWED

// Replay system events so service interruptions do not lose screen sessions.
// App foreground durations cannot be summed to obtain device screen time.
class UsageTracking(private val context: Context) {
	private val state = context.getSharedPreferences("usage_tracking", Context.MODE_PRIVATE)

	fun update(now: Long, interactive: Boolean): Boolean {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || !context.hasUsageAccess()) {
			state.edit().clear().apply()
			return false
		}
		val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
		val clock = UsageClock(
			now, SystemClock.elapsedRealtime(),
			Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
		)
		val previous = UsageClock(
			state.getLong("timestamp", now), state.getLong("elapsed", -1L),
			state.getInt("boot_count", -1)
		)
		val canReplay = clock.canReplayFrom(previous)
		// Keep the last confirmed duration when the time base changes.
		// Replaying across a reboot or clock adjustment would invent usage.
		val from = if (canReplay) previous.timestamp else now
		val events = try {
			manager.queryEvents(from.coerceAtMost(now), now)
		} catch (_: SecurityException) {
			null
		} ?: run {
			state.edit().clear().apply()
			return false
		}
		var start = if (canReplay) state.getLong("start", 0L) else 0L
		var id = if (canReplay) state.getLong("id", 0L) else 0L
		val unlocked = !(context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager)
			.isKeyguardLocked
		var screenOn = if (canReplay) state.getBoolean("screen_on", interactive) else interactive
		var keyguardHidden = if (canReplay) state.getBoolean("keyguard_hidden", unlocked) else unlocked
		val event = UsageEvents.Event()
		while (events.hasNextEvent()) {
			events.getNextEvent(event)
			when (event.eventType) {
				UsageEvents.Event.SCREEN_INTERACTIVE -> screenOn = true
				UsageEvents.Event.SCREEN_NON_INTERACTIVE -> screenOn = false
				UsageEvents.Event.KEYGUARD_HIDDEN -> keyguardHidden = true
				UsageEvents.Event.KEYGUARD_SHOWN -> keyguardHidden = false
				UsageEvents.Event.DEVICE_SHUTDOWN -> screenOn = false
				else -> continue
			}
			if (screenOn && keyguardHidden) {
				if (id == 0L) {
					start = event.timeStamp
					id = db.findOrInsertEvent(start)
				}
			} else {
				if (id > 0L) {
					db.updateEvent(id, (event.timeStamp - start).coerceAtLeast(0L))
				}
				id = 0L
				start = 0L
			}
		}
		if (id == 0L && interactive && unlocked) {
			start = now
			id = db.findOrInsertEvent(start)
		}
		if (id > 0L) {
			db.updateEvent(id, (now - start).coerceAtLeast(0L))
		}
		state.edit().putLong("timestamp", now).putLong("start", start)
			.putLong("elapsed", clock.elapsed).putInt("boot_count", clock.bootCount)
			.putLong("id", id).putBoolean("screen_on", screenOn)
			.putBoolean("keyguard_hidden", keyguardHidden).apply()
		return true
	}
}
