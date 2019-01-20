package de.markusfisch.android.screentime.receiver

import de.markusfisch.android.screentime.service.startTrackerService

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

const val UPDATE_NOTIFICATION = "update_notification"
const val SCREEN_STATE = "screen_state"
const val TIMESTAMP = "timestamp"
const val BATTERY_LEVEL = "battery_level"

class EventReceiver : BroadcastReceiver() {
	override fun onReceive(context: Context, intent: Intent?) {
		when (intent?.action) {
			// *BOOT_COMPLETED doesn't need no handling because
			// the service is started in ScreenTimeTrackerApp
			Intent.ACTION_SCREEN_ON -> sendNotificationIntent(context)
			Intent.ACTION_SCREEN_OFF -> sendStateIntent(context, false)
			Intent.ACTION_USER_PRESENT -> sendStateIntent(context, true)
			else -> return
		}
	}

	private fun sendNotificationIntent(context: Context) {
		startTrackerService(context) { intent ->
			intent.putExtra(UPDATE_NOTIFICATION, true)
		}
	}

	private fun sendStateIntent(context: Context, state: Boolean) {
		startTrackerService(context) { intent ->
			intent.putExtra(SCREEN_STATE, state)
			intent.putExtra(TIMESTAMP, System.currentTimeMillis())
			intent.putExtra(BATTERY_LEVEL, getBatteryLevel(context))
		}
	}
}

private fun getBatteryLevel(context: Context): Float {
	val status = context.registerReceiver(
		null,
		IntentFilter(Intent.ACTION_BATTERY_CHANGED)
	)
	return if (status != null) {
		val level = status.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
		val scale = status.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
		return level.toFloat() / scale.toFloat()
	} else {
		0f
	}
}
