package de.markusfisch.android.screentime.os

import android.app.AppOpsManager
import android.app.KeyguardManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
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
		val from = state.getLong("timestamp", now)
		val events = try {
			manager.queryEvents(from.coerceAtMost(now), now)
		} catch (_: SecurityException) {
			null
		} ?: run {
			state.edit().clear().apply()
			return false
		}
		var start = state.getLong("start", 0L)
		var id = state.getLong("id", 0L)
		// System events are retained for only a few days. Do not extend a
		// session across an interval whose closing event may have expired.
		if (now < from || now - from > 86400000L) {
			start = 0L
			id = 0L
		}
		val unlocked = !(context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager)
			.isKeyguardLocked
		var screenOn = state.getBoolean("screen_on", interactive)
		var keyguardHidden = state.getBoolean("keyguard_hidden", unlocked)
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
			.putLong("id", id).putBoolean("screen_on", screenOn)
			.putBoolean("keyguard_hidden", keyguardHidden).apply()
		return true
	}
}
