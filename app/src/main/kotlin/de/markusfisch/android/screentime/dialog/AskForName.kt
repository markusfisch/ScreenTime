package de.markusfisch.android.screentime.dialog

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.widget.EditText
import de.markusfisch.android.screentime.R

fun Context.askForName(
	titleId: Int,
	preset: String,
	callback: (String) -> Any
) {
	// Dialogs don't have a parent layout.
	@SuppressLint("InflateParams")
	val view = LayoutInflater.from(this).inflate(
		R.layout.dialog_save_as, null
	)
	val nameView = view.findViewById<EditText>(R.id.name)
	nameView.setText(preset)
	AlertDialog.Builder(this)
		.setTitle(titleId)
		.setView(view)
		.setPositiveButton(android.R.string.ok) { _, _ ->
			callback(nameView.text.toString())
		}
		.setNegativeButton(android.R.string.cancel) { _, _ -> }
		.show()
}
