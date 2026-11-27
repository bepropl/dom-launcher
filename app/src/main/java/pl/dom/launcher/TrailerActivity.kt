package pl.dom.launcher

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Zwiastun z YouTube na osobnym, prostym ekranie: sam WebView z oficjalnym odtwarzaczem,
 * bez warstw interfejsu launchera nad nim (osadzony w launcherze dawał dźwięk bez obrazu).
 * Pilot: OK = pauza/wznów, ◀ ▶ = 10 s, Wstecz = powrót do podglądu tytułu.
 */
class TrailerActivity : Activity() {

    private var webView: WebView? = null
    private lateinit var hint: TextView
    private lateinit var root: FrameLayout
    private var errorShown = false
    private val main = Handler(Looper.getMainLooper())
    private val hideHint = Runnable { hint.animate().alpha(0f).setDuration(400).start() }
    private val giveUp = Runnable { if (loading && !errorShown) showError(-1) }
    private var loading = true

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val key = intent.getStringExtra(EXTRA_KEY).orEmpty().filter { it.isLetterOrDigit() || it == '-' || it == '_' }
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        if (key.isEmpty()) {
            finish()
            return
        }

        root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        val web = WebView(this).apply {
            isFocusable = false
            isFocusableInTouchMode = false
            setBackgroundColor(Color.BLACK)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            webChromeClient = WebChromeClient()
            addJavascriptInterface(Bridge(), "Dom")
        }
        webView = web
        root.addView(web, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        hint = TextView(this).apply {
            text = "$title · OK pauza · ◀ ▶ 10 s · Wstecz zamyka"
            setTextColor(Color.rgb(0xD9, 0xC9, 0xB2))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            setPadding(dp(12), dp(6), dp(12), dp(6))
            background = GradientDrawable().apply { cornerRadius = dp(8).toFloat(); setColor(Color.argb(160, 0, 0, 0)) }
        }
        root.addView(hint, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dp(28) })
        setContentView(root)

        web.loadDataWithBaseURL(PLAYER_ORIGIN, playerHtml(key), "text/html", "utf-8", null)
        showHint()
        main.postDelayed(giveUp, 20_000)
    }

    private fun showHint() {
        hint.animate().cancel()
        hint.alpha = 1f
        main.removeCallbacks(hideHint)
        main.postDelayed(hideHint, 3500)
    }

    private fun js(code: String) = webView?.evaluateJavascript(code, null)

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (errorShown) return super.dispatchKeyEvent(event)
        val handled = when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_SPACE -> { if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) js("toggle()"); true }
            KeyEvent.KEYCODE_MEDIA_PLAY -> { if (event.action == KeyEvent.ACTION_DOWN) js("player && player.playVideo()"); true }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> { if (event.action == KeyEvent.ACTION_DOWN) js("player && player.pauseVideo()"); true }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> { if (event.action == KeyEvent.ACTION_DOWN) js("seek(-10)"); true }
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { if (event.action == KeyEvent.ACTION_DOWN) js("seek(10)"); true }
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> true
            else -> false
        }
        if (handled) {
            if (event.action == KeyEvent.ACTION_DOWN) showHint()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun showError(code: Int) {
        if (errorShown) return
        errorShown = true
        hint.visibility = View.GONE
        val key = intent.getStringExtra(EXTRA_KEY).orEmpty()
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(Color.rgb(0x1C, 0x15, 0x10)) }
        }
        box.addView(TextView(this).apply {
            text = when (code) {
                101, 150, 152, 153 -> "Autor tego zwiastuna nie pozwala odtwarzać go poza YouTube."
                -1 -> "Zwiastun się nie wczytał. Sprawdź połączenie z internetem."
                else -> "Nie udało się odtworzyć zwiastuna (błąd $code)."
            }
            setTextColor(Color.rgb(0xF4, 0xEB, 0xDD))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        })
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(12), 0, 0) }
        val youtube = Button(this).apply {
            text = "Otwórz w YouTube"
            setOnClickListener {
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$key"))) }
                finish()
            }
        }
        buttons.addView(youtube)
        buttons.addView(Button(this).apply { text = "Zamknij"; setOnClickListener { finish() } })
        box.addView(buttons)
        root.addView(box, FrameLayout.LayoutParams(dp(420), FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        youtube.requestFocus()
    }

    private inner class Bridge {
        @JavascriptInterface
        fun state(s: Int) {
            main.post {
                if (s == 1 || s == 3) loading = false
                if (s == 0) finish()
            }
        }

        @JavascriptInterface
        fun error(code: Int) {
            main.post { showError(code) }
        }
    }

    override fun onPause() {
        super.onPause()
        webView?.onPause()
    }

    override fun onResume() {
        super.onResume()
        webView?.onResume()
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        webView?.apply {
            loadUrl("about:blank")
            removeJavascriptInterface("Dom")
            destroy()
        }
        webView = null
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_KEY = "key"
        private const val EXTRA_TITLE = "title"

        fun open(context: Context, key: String, title: String) {
            context.startActivity(
                Intent(context, TrailerActivity::class.java)
                    .putExtra(EXTRA_KEY, key)
                    .putExtra(EXTRA_TITLE, title)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }

        /** YouTube wymaga strony-„właściciela” odtwarzacza; bez niej część filmów kończy się błędem 152/153. */
        private const val PLAYER_ORIGIN = "https://bepropl.github.io"

        private fun playerHtml(key: String): String = """
<!DOCTYPE html>
<html><head>
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="referrer" content="strict-origin-when-cross-origin">
<style>html,body{margin:0;height:100%;background:#000;overflow:hidden}#p{position:absolute;top:0;left:0;width:100%;height:100%}</style>
</head><body>
<div id="p"></div>
<script>
var player;
var tag = document.createElement('script');
tag.src = 'https://www.youtube.com/iframe_api';
document.head.appendChild(tag);
function onYouTubeIframeAPIReady() {
  player = new YT.Player('p', {
    width: '100%', height: '100%', videoId: '__KEY__',
    host: 'https://www.youtube-nocookie.com',
    playerVars: { autoplay: 1, controls: 0, rel: 0, modestbranding: 1, playsinline: 1, iv_load_policy: 3, fs: 0, disablekb: 1, cc_load_policy: 0, origin: '__ORIGIN__' },
    events: {
      onReady: function (e) { noCaptions(e.target); e.target.playVideo(); },
      onStateChange: function (e) { if (e.data == 1) noCaptions(e.target); Dom.state(e.data); },
      onError: function (e) { Dom.error(e.data); }
    }
  });
}
// Napisy YouTube (automatyczne/tłumaczone) wyłączone – zostają tylko te wtopione w film.
function noCaptions(p) {
  try { p.setOption('captions', 'track', {}); } catch (x) {}
  try { p.unloadModule('captions'); } catch (x) {}
  try { p.unloadModule('cc'); } catch (x) {}
}
function toggle() { if (!player) return; if (player.getPlayerState() == 1) player.pauseVideo(); else player.playVideo(); }
function seek(d) { if (!player) return; player.seekTo(Math.max(0, player.getCurrentTime() + d), true); }
</script>
</body></html>
""".trimIndent().replace("__KEY__", key).replace("__ORIGIN__", PLAYER_ORIGIN)
    }
}
