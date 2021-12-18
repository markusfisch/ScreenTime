package de.markusfisch.android.screentime.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import de.markusfisch.android.screentime.service.startTrackerService

const val UPDATE_NOTIFICATION = "update_notification"
const val SCREEN_STATE = "screen_state"
const val TIMESTAMP = "timestamp"
const val BATTERY_LEVEL = "battery_level"

class EventReceiver : BroadcastReceiver() {
	override fun onReceive(context: Context, intent: Intent?) {
		when (intent?.action) {
			Intent.ACTION_SCREEN_ON -> context.sendNotificationIntent()
			Intent.ACTION_SCREEN_OFF -> context.sendScreenEventIntent(false)
			Intent.ACTION_USER_PRESENT -> context.sendScreenEventIntent(true)
			Intent.ACTION_BOOT_COMPLETED,
			Intent.ACTION_PACKAGE_REPLACED,
			Intent.ACTION_MY_PACKAGE_REPLACED -> context.startTrackerService()
			else -> return
		}
	}
}

private fun Context.sendNotificationIntent() {
	startTrackerService { intent ->
		intent.putExtra(UPDATE_NOTIFICATION, true)
	}
}

private fun Context.sendScreenEventIntent(state: Boolean) {
	startTrackerService { intent ->
		intent.putExtra(SCREEN_STATE, state)
		intent.putExtra(TIMESTAMP, System.currentTimeMillis())
		intent.putExtra(BATTERY_LEVEL, getBatteryLevel())
	}
}

private fun Context.getBatteryLevel(): Float {
	registerReceiver(
		null,
		IntentFilter(Intent.ACTION_BATTERY_CHANGED)
	)?.apply {
		val level = getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
		val scale = getIntExtra(BatteryManager.EXTRA_SCALE, -1)
		return level.toFloat() / scale.toFloat()
	}
	return 0f
}
