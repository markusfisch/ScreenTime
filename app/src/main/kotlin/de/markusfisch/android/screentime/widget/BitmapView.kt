package de.markusfisch.android.screentime.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View

class BitmapView : View {
	private val bitmapRect = Rect()

	private var bitmap: Bitmap? = null

	constructor(context: Context, attrs: AttributeSet, defStyle: Int) :
			super(context, attrs, defStyle)

	constructor(context: Context, attrs: AttributeSet) :
			this(context, attrs, 0)

	fun setBitmap(bitmap: Bitmap) {
		if (layoutParams.height != bitmap.height) {
			layoutParams = layoutParams.also { it.height = bitmap.height }
		}
		this.bitmap = bitmap
		bitmapRect.set(0, 0, bitmap.width, bitmap.height)
		invalidate()
	}

	override fun onDraw(canvas: Canvas) {
		super.onDraw(canvas)
		canvas.drawColor(0)
		bitmap?.let {
			canvas.drawBitmap(it, bitmapRect, bitmapRect, null)
		}
	}
}