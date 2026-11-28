package pl.dom.launcher.ui

import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import pl.dom.launcher.data.HouseMode
import pl.dom.launcher.house.HouseModel
import pl.dom.launcher.house.HouseRenderer
import pl.dom.launcher.house.HouseRooms

/** Jasność świateł w pokojach domku (0..1). W etapie z Home Assistantem ustawi je HA. */
object HouseLights {
    val rooms = MutableStateFlow(FloatArray(HouseRooms.COUNT))
}

/**
 * Domek z cząsteczek jako tapeta (OpenGL ES 2.0).
 * Rysuje 30 klatek na sekundę (oszczędnie 20, w trybie spoczynku 15),
 * a gdy launcher jest w tle albo ekran wyłączony – nie rysuje nic.
 */
@Composable
fun HouseWallpaper(
    modifier: Modifier = Modifier,
    dim: Float = 1f,
    mode: HouseMode = HouseMode.FULL,
    showHouse: Boolean = true,
    /** Położenie domku: (przesunięcie X, przesunięcie Y, wielkość). Zmiana jest płynna. */
    layout: Triple<Float, Float, Float> = Triple(0.5f, 0.12f, 2.0f),
    /** Poziom jasności 1–5 z ustawień. */
    brightnessLevel: Int = 3,
    /** Zmiana = jedna fala przez domek. */
    waveCount: Int = 0,
    breathing: Boolean = false,
) {
    if (mode == HouseMode.OFF) {
        Box(modifier)
        return
    }
    val context = LocalContext.current
    // Model (1,2 MB) wczytuje się w tle – start launchera się nie zacina.
    val model by produceState<HouseModel?>(null) {
        value = withContext(Dispatchers.IO) { runCatching { HouseModel.load(context.applicationContext) }.getOrNull() }
    }
    val loaded = model
    if (loaded == null) {
        Box(modifier)
        return
    }
    val renderer = remember(loaded) { HouseRenderer(loaded) }
    val lights by HouseLights.rooms.collectAsState()

    renderer.dim = dim
    renderer.showHouse = showHouse
    renderer.waveCount = waveCount
    renderer.breathing = breathing
    renderer.brightness = HouseBrightness.multiplier(brightnessLevel)
    renderer.offsetX = layout.first
    renderer.offsetY = layout.second
    renderer.scale = layout.third
    renderer.detail = if (mode == HouseMode.ECO) 0.5f else 1f
    LaunchedEffect(lights) { renderer.roomTargets = lights.copyOf() }

    val fps = when {
        dim < 0.9f -> 15
        mode == HouseMode.ECO -> 20
        else -> 30
    }
    val pacer = remember { FramePacer() }
    pacer.fps = fps

    val lifecycleOwner = LocalLifecycleOwner.current
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            GLSurfaceView(ctx).apply {
                setEGLContextClientVersion(2)
                preserveEGLContextOnPause = true
                setRenderer(renderer)
                renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
                pacer.view = this
            }
        },
    )

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> { pacer.view?.onResume(); pacer.start() }
                Lifecycle.Event.ON_PAUSE -> { pacer.stop(); pacer.view?.onPause() }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) pacer.start()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            pacer.stop()
            pacer.view = null
        }
    }
}

/** Prosi o nową klatkę z zadaną częstotliwością (zamiast ciągłych 60 kl./s). */
private class FramePacer {
    var view: GLSurfaceView? = null
    @Volatile var fps = 30
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            view?.requestRender()
            handler.postDelayed(this, 1000L / fps.coerceIn(5, 60))
        }
    }

    fun start() {
        if (running) return
        running = true
        handler.post(tick)
    }

    fun stop() {
        running = false
        handler.removeCallbacks(tick)
    }
}

/** Poziomy jasności domku (ustawienia launchera). */
object HouseBrightness {
    fun multiplier(level: Int): Float = when (level.coerceIn(1, 5)) {
        1 -> 0.7f
        2 -> 1.0f
        3 -> 1.3f
        4 -> 1.65f
        else -> 2.0f
    }
}
