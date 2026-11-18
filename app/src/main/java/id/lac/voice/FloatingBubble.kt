package id.lac.voice

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.TextView
import kotlin.math.abs

/**
 * Tombol melayang kecil di atas aplikasi lain (termasuk game).
 * Ketuk = nyalakan/matikan mic. Tekan lama = buka app ini. Bisa digeser.
 * Abu-abu = menunggu masuk game, hijau = mic menyala, merah = mic dimatikan.
 */
class FloatingBubble(
    private val context: Context,
    private val onTap: () -> Unit,
    private val onLongPress: () -> Unit,
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var bubbleView: TextView? = null
    private val sizePx = dp(58)
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private val params = WindowManager.LayoutParams(
        sizePx,
        sizePx,
        overlayType(),
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = dp(12)
        y = dp(120)
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    @Suppress("DEPRECATION")
    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

    /** Balikin true kalau tombol tampil (butuh izin "Tampil di atas aplikasi lain"). */
    fun show(): Boolean {
        if (bubbleView != null) return true
        if (!Settings.canDrawOverlays(context)) return false
        val view = TextView(context).apply {
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 11f
            setTypeface(typeface, Typeface.BOLD)
            alpha = 0.9f
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(GRAY)
            }
            text = "…"
        }
        view.setOnTouchListener(touchListener())
        return try {
            windowManager.addView(view, params)
            bubbleView = view
            true
        } catch (e: Exception) {
            false
        }
    }

    fun update(connected: Boolean, micOn: Boolean, nearby: Int) {
        val view = bubbleView ?: return
        val label: String
        val color: Int
        when {
            !connected -> { label = "…"; color = GRAY }
            micOn -> { label = "MIC\n$nearby"; color = GREEN }
            else -> { label = "MUTE\n$nearby"; color = RED }
        }
        view.text = label
        (view.background as? GradientDrawable)?.setColor(color)
    }

    fun hide() {
        val view = bubbleView ?: return
        try {
            windowManager.removeView(view)
        } catch (e: Exception) {
            // abaikan
        }
        bubbleView = null
    }

    private fun touchListener(): View.OnTouchListener = object : View.OnTouchListener {
        private var startX = 0
        private var startY = 0
        private var touchX = 0f
        private var touchY = 0f
        private var moved = false

        private val detector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                if (!moved) onTap()
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                if (!moved) onLongPress()
            }
        })

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            detector.onTouchEvent(event)
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    moved = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchX).toInt()
                    val dy = (event.rawY - touchY).toInt()
                    if (abs(dx) > touchSlop || abs(dy) > touchSlop) moved = true
                    if (moved) {
                        params.x = startX + dx
                        params.y = startY + dy
                        windowManager.updateViewLayout(v, params)
                    }
                }
            }
            return true
        }
    }

    companion object {
        private val GRAY = Color.parseColor("#616161")
        private val GREEN = Color.parseColor("#2E7D32")
        private val RED = Color.parseColor("#C62828")
    }
}
