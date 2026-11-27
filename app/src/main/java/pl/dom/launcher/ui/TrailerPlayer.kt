package pl.dom.launcher.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.tv.material3.Button
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import kotlinx.coroutines.delay

/**
 * Zwiastun z YouTube odtwarzany wewnątrz launchera (oficjalny odtwarzacz YouTube w WebView).
 * Pilot: OK = pauza/wznów, ◀ ▶ = 10 s do tyłu/do przodu, Wstecz = zamknij.
 * Gdy autor filmu nie pozwala na odtwarzanie poza YouTube – proponujemy aplikację YouTube.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TrailerPlayer(videoKey: String, title: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val focus = remember { FocusRequester() }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var error by remember { mutableStateOf<Int?>(null) }
    var loading by remember { mutableStateOf(true) }
    var paused by remember { mutableStateOf(false) }
    var hintTick by remember { mutableIntStateOf(0) }
    var hintVisible by remember { mutableStateOf(true) }

    BackHandler(onBack = onClose)
    LaunchedEffect(hintTick) {
        hintVisible = true
        delay(3500)
        hintVisible = false
    }
    LaunchedEffect(Unit) {
        delay(100)
        runCatching { focus.requestFocus() }
    }
    // Zwiastun to nie „brak pilota” – nie włączaj trybu spoczynku w trakcie.
    LaunchedEffect(Unit) {
        while (true) {
            pl.dom.launcher.service.Interaction.touch()
            delay(30_000)
        }
    }
    // Gdyby odtwarzacz w ogóle nie ruszył (brak sieci, zablokowany WebView) – po 20 s pokaż wyjście.
    LaunchedEffect(Unit) {
        delay(20_000)
        if (loading && error == null) error = -1
    }

    fun js(code: String) = webView?.evaluateJavascript(code, null)

    Box(
        // Bez tła: film z WebView wyświetla się pod oknem aplikacji, więc wszystko nad nim musi być przezroczyste.
        Modifier
            .fillMaxSize()
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (error != null) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.MediaPlayPause, Key.Spacebar -> {
                        js("toggle()"); paused = !paused; hintTick++; true
                    }
                    Key.MediaPlay -> { js("player.playVideo()"); paused = false; true }
                    Key.MediaPause -> { js("player.pauseVideo()"); paused = true; hintTick++; true }
                    Key.DirectionLeft, Key.MediaRewind -> { js("seek(-10)"); hintTick++; true }
                    Key.DirectionRight, Key.MediaFastForward -> { js("seek(10)"); hintTick++; true }
                    Key.DirectionUp, Key.DirectionDown -> { hintTick++; true }
                    else -> false
                }
            },
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    isFocusable = false
                    isFocusableInTouchMode = false
                    setBackgroundColor(AndroidColor.TRANSPARENT)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    webChromeClient = WebChromeClient()
                    val main = Handler(Looper.getMainLooper())
                    addJavascriptInterface(
                        object {
                            @JavascriptInterface fun state(s: Int) = main.post {
                                if (s == 1 || s == 3) loading = false
                                if (s == 0) onClose()
                            }
                            @JavascriptInterface fun error(code: Int) = main.post { error = code }
                        },
                        "Dom",
                    )
                    loadDataWithBaseURL(PLAYER_ORIGIN, playerHtml(videoKey), "text/html", "utf-8", null)
                    webView = this
                }
            },
        )

        if (loading && error == null) {
            Text("Wczytuję zwiastun…", fontSize = 12.sp, color = Palette.Muted, modifier = Modifier.align(Alignment.Center))
        }

        AnimatedVisibility(
            visible = hintVisible && error == null,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp),
        ) {
            Text(
                (if (paused) "Pauza · " else "") + "$title · OK pauza · ◀ ▶ 10 s · Wstecz zamyka",
                fontSize = 10.sp,
                color = Palette.Soft,
                modifier = Modifier.background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }

        error?.let { code ->
            TrailerError(
                code = code,
                onYoutube = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$videoKey"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(intent) }
                    onClose()
                },
                onClose = onClose,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webView?.apply {
                loadUrl("about:blank")
                removeJavascriptInterface("Dom")
                destroy()
            }
            webView = null
        }
    }
}

@Composable
private fun TrailerError(code: Int, onYoutube: () -> Unit, onClose: () -> Unit, modifier: Modifier) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(50)
        runCatching { first.requestFocus() }
    }
    Column(
        modifier.width(420.dp).background(Palette.Card, RoundedCornerShape(12.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            when (code) {
                101, 150, 152, 153 -> "Autor tego zwiastuna nie pozwala odtwarzać go poza YouTube."
                -1 -> "Zwiastun się nie wczytał. Sprawdź połączenie z internetem."
                else -> "Nie udało się odtworzyć zwiastuna (błąd $code)."
            },
            fontSize = 12.sp, color = Palette.Text,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onYoutube, modifier = Modifier.focusRequester(first)) { Text("Otwórz w YouTube", fontSize = 11.sp) }
            OutlinedButton(onClick = onClose) { Text("Zamknij", fontSize = 11.sp) }
        }
    }
}

/** YouTube wymaga strony-„właściciela” odtwarzacza; bez niej część filmów kończy się błędem 152/153. */
private const val PLAYER_ORIGIN = "https://bepropl.github.io"

private fun playerHtml(key: String): String = """
<!DOCTYPE html>
<html><head>
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="referrer" content="strict-origin-when-cross-origin">
<style>html,body{margin:0;height:100%;background:transparent;overflow:hidden}#p{position:absolute;top:0;left:0;width:100%;height:100%}</style>
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
    playerVars: { autoplay: 1, controls: 0, rel: 0, modestbranding: 1, playsinline: 1, iv_load_policy: 3, fs: 0, disablekb: 1, hl: 'pl', origin: '__ORIGIN__' },
    events: {
      onReady: function (e) { e.target.playVideo(); },
      onStateChange: function (e) { Dom.state(e.data); },
      onError: function (e) { Dom.error(e.data); }
    }
  });
}
function toggle() { if (!player) return; if (player.getPlayerState() == 1) player.pauseVideo(); else player.playVideo(); }
function seek(d) { if (!player) return; player.seekTo(Math.max(0, player.getCurrentTime() + d), true); }
</script>
</body></html>
""".trimIndent().replace("__KEY__", key.filter { it.isLetterOrDigit() || it == '-' || it == '_' }).replace("__ORIGIN__", PLAYER_ORIGIN)
