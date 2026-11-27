package pl.dom.launcher.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import pl.dom.launcher.ha.HaNotification

/**
 * Dymek z powiadomieniem nad dowolną aplikacją (Netflix, YouTube…).
 * Okno ułatwień dostępu – nie potrzebuje zgody „wyświetlanie nad innymi aplikacjami”
 * i nie zabiera fokusu pilota.
 */
class NotificationOverlay(private val service: AccessibilityService) {
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var view: View? = null
    private val hide = Runnable { remove() }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), service.resources.displayMetrics).toInt()

    fun show(n: HaNotification) = handler.post {
        remove()
        val card = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(Color.argb(245, 0x1C, 0x15, 0x10))
                setStroke(dp(1), Color.argb(115, 0xF2, 0xA6, 0x5A))
            }
        }
        n.image?.let { bitmap ->
            card.addView(
                ImageView(service).apply {
                    setImageBitmap(bitmap)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    clipToOutline = true
                    background = GradientDrawable().apply { cornerRadius = dp(8).toFloat() }
                },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(180)).apply { bottomMargin = dp(8) },
            )
        }
        card.addView(TextView(service).apply {
            text = n.title
            setTextColor(Color.rgb(0xF4, 0xEB, 0xDD))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            paint.isFakeBoldText = true
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        })
        if (n.message.isNotBlank()) {
            card.addView(TextView(service).apply {
                text = n.message
                setTextColor(Color.rgb(0xD9, 0xC9, 0xB2))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                maxLines = 4
                ellipsize = TextUtils.TruncateAt.END
                setPadding(0, dp(4), 0, 0)
            })
        }
        val params = WindowManager.LayoutParams(
            dp(if (n.image != null) 340 else 280),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(40)
            y = dp(40)
        }
        runCatching { windowManager.addView(card, params) }.onSuccess {
            view = card
            handler.postDelayed(hide, n.seconds * 1000L)
        }
    }

    fun remove() {
        handler.removeCallbacks(hide)
        view?.let { runCatching { windowManager.removeView(it) } }
        view = null
    }
}
