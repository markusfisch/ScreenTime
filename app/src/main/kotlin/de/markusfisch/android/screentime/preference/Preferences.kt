package de.markusfisch.android.screentime.preference

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import de.markusfisch.android.screentime.app.prefs
import de.markusfisch.android.screentime.data.startOfDay
import java.util.Calendar
import kotlin.math.max
import kotlin.math.min

class Preferences(context: Context) {
	private val preferences: SharedPreferences =
		PreferenceManager.getDefaultSharedPreferences(context)

	var graphRange = 0
		set(value) {
			apply(GRAPH_RANGE, value)
			field = value
		}
	var hourOfDayChange = 0
		set(value) {
			val clamped = min(23, max(0, value))
			apply(HOUR_OF_DAY_CHANGE, clamped)
			field = clamped
		}

	init {
		graphRange = preferences.getInt(GRAPH_RANGE, graphRange)
		hourOfDayChange = preferences.getInt(
			HOUR_OF_DAY_CHANGE,
			hourOfDayChange
		)
	}

	fun dayStart(timestamp: Long): Long = startOfDay(timestamp) { cal ->
		cal.set(Calendar.HOUR_OF_DAY, prefs.hourOfDayChange)
		if (cal.timeInMillis > timestamp) {
			cal.add(Calendar.DAY_OF_MONTH, -1)
		}
	}

	private fun apply(label: String, value: Int) {
		preferences.edit().putInt(label, value).apply()
	}

	companion object {
		private const val GRAPH_RANGE = "graph_range"
		private const val HOUR_OF_DAY_CHANGE = "hour_of_day_change"
	}
}
