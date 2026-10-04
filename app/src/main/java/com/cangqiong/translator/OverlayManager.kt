package com.cangqiong.translator

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView

data class TranslatedBlock(val text: String, val rect: Rect)

class OverlayManager(private val ctx: Context) {

    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val views = mutableListOf<TextView>()

    fun show(items: List<TranslatedBlock>) {
        clear()
        items.forEach { item ->
            val tv = TextView(ctx).apply {
                text = item.text
                textSize = 12f
                setTextColor(0xFF000000.toInt())
                setBackgroundColor(0xE6FFFFFF.toInt())
                setPadding(12, 6, 12, 6)
            }
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = item.rect.left
                y = item.rect.top
            }
            runCatching {
                wm.addView(tv, params)
                views.add(tv)
            }
        }
    }

    fun clear() {
        views.forEach { runCatching { wm.removeView(it) } }
        views.clear()
    }
}
