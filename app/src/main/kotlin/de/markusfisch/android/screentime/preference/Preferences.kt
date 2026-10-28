package de.markusfisch.android.screentime.preference

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import de.markusfisch.android.screentime.database.dayStart
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

	var usageAccessPromptShown: Boolean
		get() = preferences.getBoolean(USAGE_ACCESS_PROMPT_SHOWN, false)
		set(value) {
			preferences.edit().putBoolean(USAGE_ACCESS_PROMPT_SHOWN, value).apply()
		}

	init {
		graphRange = preferences.getInt(GRAPH_RANGE, graphRange)
		hourOfDayChange = preferences.getInt(
			HOUR_OF_DAY_CHANGE,
			hourOfDayChange
		)
	}

	fun dayStart(timestamp: Long, days: Int = 0): Long =
		dayStart(timestamp, hourOfDayChange, days)

	private fun apply(label: String, value: Int) {
		preferences.edit().putInt(label, value).apply()
	}

	companion object {
		private const val GRAPH_RANGE = "graph_range"
		private const val USAGE_ACCESS_PROMPT_SHOWN = "usage_access_prompt_shown"
		private const val HOUR_OF_DAY_CHANGE = "hour_of_day_change"
	}
}
