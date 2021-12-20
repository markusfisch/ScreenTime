package de.markusfisch.android.screentime.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import de.markusfisch.android.screentime.service.startTrackerService

const val ACTION = "action"
const val TIMESTAMP = "timestamp"
const val BATTERY_LEVEL = "battery_level"

class EventReceiver : BroadcastReceiver() {
	override fun onReceive(context: Context, intent: Intent?) {
		intent?.action?.let {
			context.sendIntent(it)
		}
	}
}

private fun Context.sendIntent(action: String) {
	startTrackerService { intent ->
		intent.putExtra(ACTION, action)
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
