package pl.dom.launcher.house

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.min
import kotlin.math.sin

/**
 * Pokoje w modelu (numer = numer strefy światła w pliku house.bin).
 * W etapie z Home Assistantem każdy dostanie swoją encję światła.
 */
object HouseRooms {
    const val COUNT = 12
    val names = mapOf(
        1 to "Salon",
        2 to "Kuchnia",
        3 to "Sypialnia z balkonem",
        4 to "Pokój w lukarnie",
        5 to "Łazienka",
        6 to "Pokój od frontu",
        7 to "Sypialnia gościnna",
        8 to "Sypialnia Kacpra",
        9 to "Łazienka mała",
        10 to "Garderoba",
    )
}

/** Chmura punktów domku wczytana z assets/house.bin (generuje ją tools/house-model.js). */
class HouseModel private constructor(val data: FloatBuffer, val count: Int) {
    companion object {
        const val FLOATS_PER_POINT = 7

        @Volatile
        private var cached: HouseModel? = null

        fun load(context: Context): HouseModel = cached ?: synchronized(this) {
            cached ?: run {
                val bytes = context.assets.open("house.bin").use { it.readBytes() }
                val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                val magic = String(bytes, 0, 4, Charsets.US_ASCII)
                require(magic == "DOMH") { "Uszkodzony plik house.bin" }
                val count = buffer.getInt(4)
                val floats = ByteBuffer.allocateDirect(count * FLOATS_PER_POINT * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
                buffer.position(8)
                val source = buffer.slice().order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
                floats.put(source).position(0)
                HouseModel(floats, count).also { cached = it }
            }
        }
    }
}

/**
 * Rysuje obracający się domek z cząsteczek (ten sam shader co w makiecie).
 * Wszystkie pola ustawiane z wątku UI są @Volatile; renderer czyta je co klatkę.
 */
class HouseRenderer(private val model: HouseModel) : GLSurfaceView.Renderer {

    /** Jasność całości (1 = normalnie, ok. 0.55 = tryb spoczynku). */
    @Volatile var dim = 1f
    /** Ułamek punktów do narysowania (1 = pełna jakość, 0.5 = oszczędna). */
    @Volatile var detail = 1f
    /** Docelowa jasność świateł w pokojach 0..1, indeks = numer pokoju. */
    @Volatile var roomTargets = FloatArray(HouseRooms.COUNT)
    /** Wielkość i położenie domku na ekranie (offsetX/offsetY w jednostkach ekranu, -1..1). */
    @Volatile var scale = 2.0f
    @Volatile var offsetX = 0.5f
    @Volatile var offsetY = 0.12f
    /** Domek widać tylko na ekranie Start; na innych zakładkach zostają same cząstki. */
    @Volatile var showHouse = true
    /** Jasność domku z ustawień (mnożnik, ok. 0,7–2). */
    @Volatile var brightness = 1.3f
    /** Każda zmiana licznika puszcza jedną falę przez domek. */
    @Volatile var waveCount = 0
    /** Czy domek ma „oddychać” (raz na minutę) – nieprzeczytane powiadomienia. */
    @Volatile var breathing = false
    private var seenWave = 0
    private var waveStart = -100f
    private var breathStart = 0f
    private var uWave = 0; private var uBreath = 0

    // Aktualne położenie – płynnie dociąga do scale/offsetX/offsetY (np. przejście do wygaszacza).
    private var curScale = Float.NaN
    private var curX = 0f
    private var curY = 0f
    private var houseAlpha = 1f
    private var dustAlpha = 0.5f
    private var dustProgram = 0
    private var dustVbo = 0
    private var dustCount = 0
    private var dAttr = 0; private var dTime = 0; private var dPointScale = 0; private var dAlpha = 0

    private val rooms = FloatArray(HouseRooms.COUNT)
    private var program = 0
    private var vbo = 0
    private var width = 1
    private var height = 1
    private var angle = Math.PI.toFloat() + 0.45f
    private var lastFrameNanos = 0L
    private val startNanos = System.nanoTime()

    private var uTime = 0; private var uAng = 0; private var uPit = 0; private var uAsp = 0
    private var uPointScale = 0; private var uScale = 0; private var uOff = 0; private var uOffX = 0; private var uDim = 0; private var uRooms = 0; private var uBright = 0
    private var aPos = 0; private var aAttr = 0

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        program = buildProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        GLES20.glUseProgram(program)
        aPos = GLES20.glGetAttribLocation(program, "p")
        aAttr = GLES20.glGetAttribLocation(program, "a")
        uTime = GLES20.glGetUniformLocation(program, "t")
        uAng = GLES20.glGetUniformLocation(program, "ang")
        uPit = GLES20.glGetUniformLocation(program, "pit")
        uAsp = GLES20.glGetUniformLocation(program, "asp")
        uPointScale = GLES20.glGetUniformLocation(program, "ps")
        uScale = GLES20.glGetUniformLocation(program, "sc")
        uOff = GLES20.glGetUniformLocation(program, "off")
        uOffX = GLES20.glGetUniformLocation(program, "offx")
        uBright = GLES20.glGetUniformLocation(program, "br")
        uWave = GLES20.glGetUniformLocation(program, "wv")
        uBreath = GLES20.glGetUniformLocation(program, "bth")
        uDim = GLES20.glGetUniformLocation(program, "dim")
        uRooms = GLES20.glGetUniformLocation(program, "rm")

        val ids = IntArray(1)
        GLES20.glGenBuffers(1, ids, 0)
        vbo = ids[0]
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        model.data.position(0)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, model.count * HouseModel.FLOATS_PER_POINT * 4, model.data, GLES20.GL_STATIC_DRAW)

        // Cząstki unoszące się po całym ekranie (stałe ziarno – zawsze ten sam układ).
        dustProgram = buildProgram(DUST_VERTEX_SHADER, FRAGMENT_SHADER)
        dAttr = GLES20.glGetAttribLocation(dustProgram, "d")
        dTime = GLES20.glGetUniformLocation(dustProgram, "t")
        dPointScale = GLES20.glGetUniformLocation(dustProgram, "ps")
        dAlpha = GLES20.glGetUniformLocation(dustProgram, "al")
        dustCount = DUST_COUNT
        val random = java.util.Random(7)
        val dust = ByteBuffer.allocateDirect(dustCount * 4 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        repeat(dustCount) {
            val size = random.nextFloat()
            dust.put(random.nextFloat()).put(random.nextFloat()).put(random.nextFloat()).put(size * size)
        }
        dust.position(0)
        GLES20.glGenBuffers(1, ids, 0)
        dustVbo = ids[0]
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, dustVbo)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, dustCount * 4 * 4, dust, GLES20.GL_STATIC_DRAW)

        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE)
        GLES20.glClearColor(0.027f, 0.020f, 0.016f, 1f)
        lastFrameNanos = System.nanoTime()
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        width = maxOf(1, w)
        height = maxOf(1, h)
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        val dt = min(0.1f, (now - lastFrameNanos) / 1e9f)
        lastFrameNanos = now
        angle += dt * 0.08f

        // Płynne zapalanie i gaszenie świateł.
        val targets = roomTargets
        for (i in rooms.indices) rooms[i] += (targets.getOrElse(i) { 0f } - rooms[i]) * min(1f, dt * 4f)

        // Płynne pojawianie się i znikanie domku przy zmianie zakładki.
        val houseTarget = if (showHouse) 1f else 0f
        houseAlpha += (houseTarget - houseAlpha) * min(1f, dt * 3f)
        val dustTarget = if (showHouse) 0.45f else 1f
        dustAlpha += (dustTarget - dustAlpha) * min(1f, dt * 3f)
        val time = (now - startNanos) / 1e9f
        if (curScale.isNaN()) { curScale = scale; curX = offsetX; curY = offsetY }
        val move = min(1f, dt * 1.2f)
        curScale += (scale - curScale) * move
        curX += (offsetX - curX) * move
        curY += (offsetY - curY) * move

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        GLES20.glUseProgram(dustProgram)
        GLES20.glUniform1f(dTime, time)
        GLES20.glUniform1f(dPointScale, height / 1080f)
        GLES20.glUniform1f(dAlpha, dustAlpha * dim)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, dustVbo)
        GLES20.glEnableVertexAttribArray(dAttr)
        GLES20.glVertexAttribPointer(dAttr, 4, GLES20.GL_FLOAT, false, 16, 0)
        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, (dustCount * detail.coerceIn(0.1f, 1f)).toInt())
        GLES20.glDisableVertexAttribArray(dAttr)

        if (houseAlpha < 0.01f) return

        GLES20.glUseProgram(program)
        GLES20.glUniform1f(uTime, time)
        GLES20.glUniform1f(uAng, angle)
        GLES20.glUniform1f(uPit, 0.24f)
        GLES20.glUniform1f(uAsp, width.toFloat() / height)
        GLES20.glUniform1f(uPointScale, height / 1080f)
        GLES20.glUniform1f(uScale, curScale)
        GLES20.glUniform1f(uOff, curY)
        GLES20.glUniform1f(uOffX, curX)
        GLES20.glUniform1f(uBright, brightness)
        if (waveCount != seenWave) { seenWave = waveCount; waveStart = time }
        val waveT = (time - waveStart) / 2.4f
        GLES20.glUniform1f(uWave, if (waveT in 0f..1f) waveT else -1f)
        val breath = if (!breathing) { breathStart = time; 0f } else {
            val phase = (time - breathStart) % 60f
            if (phase < 5f) sin(Math.PI.toFloat() * phase / 5f).let { it * it } else 0f
        }
        GLES20.glUniform1f(uBreath, breath)
        GLES20.glUniform1f(uDim, dim * houseAlpha)
        GLES20.glUniform1fv(uRooms, HouseRooms.COUNT, rooms, 0)

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        val stride = HouseModel.FLOATS_PER_POINT * 4
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, stride, 0)
        GLES20.glEnableVertexAttribArray(aAttr)
        GLES20.glVertexAttribPointer(aAttr, 4, GLES20.GL_FLOAT, false, stride, 12)
        val points = (model.count * detail.coerceIn(0.1f, 1f)).toInt()
        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, points)
        GLES20.glDisableVertexAttribArray(aPos)
        GLES20.glDisableVertexAttribArray(aAttr)
    }

    private fun buildProgram(vs: String, fs: String): Int {
        fun compile(type: Int, src: String): Int {
            val shader = GLES20.glCreateShader(type)
            GLES20.glShaderSource(shader, src)
            GLES20.glCompileShader(shader)
            val ok = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, ok, 0)
            check(ok[0] != 0) { "Błąd shadera: " + GLES20.glGetShaderInfoLog(shader) }
            return shader
        }
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, compile(GLES20.GL_VERTEX_SHADER, vs))
        GLES20.glAttachShader(p, compile(GLES20.GL_FRAGMENT_SHADER, fs))
        GLES20.glLinkProgram(p)
        val ok = IntArray(1)
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, ok, 0)
        check(ok[0] != 0) { "Błąd programu: " + GLES20.glGetProgramInfoLog(p) }
        return p
    }

    private companion object {
        val VERTEX_SHADER = """
            attribute vec3 p;
            attribute vec4 a;
            uniform float t, ang, pit, asp, ps, sc, off, offx, dim, br, wv, bth;
            uniform float rm[12];
            varying float va;
            varying vec3 vc;
            void main() {
                vec3 q = p;
                if (a.x > 1.5) {
                    q += vec3(sin(t * .3 + a.z * 60.) * .5, sin(t * .2 + a.z * 90.) * .7, cos(t * .25 + a.z * 40.) * .5);
                }
                q -= vec3(7.15, 4.3, 4.8);
                float c = cos(ang), s = sin(ang);
                q = vec3(c * q.x + s * q.z, q.y, -s * q.x + c * q.z);
                float cp = cos(pit), sp = sin(pit);
                q = vec3(q.x, cp * q.y - sp * q.z, sp * q.y + cp * q.z);
                q.z -= 28.;
                float lit = 0.;
                int ri = int(a.y + .5);
                for (int i = 1; i < 12; i++) { if (i == ri) lit = rm[i]; }
                float tw = .7 + .3 * sin(t * 1.7 + a.z * 80.);
                vec3 am = vec3(1., .56, .16);
                // Krawędzie: jasność z ustawień (br). Zapalony pokój: krawędzie jaśniejsze i cieplejsze,
                // a wnętrze świeci mocną, ciepłą poświatą – wyraźnie odcina się od bryły.
                if (a.x < .5) { va = a.w * tw * (.6 * br + lit * 1.1); vc = mix(am, vec3(1., .88, .6), lit * .8); }
                else if (a.x < 1.5) { va = lit * (.85 + .15 * tw) * (1.3 + .3 * br); vc = vec3(1., .8, .42); }
                else { va = .3 * tw * br; vc = vec3(1., .6, .25); }
                // Fala od dołu do góry (nowe powiadomienie) i spokojne „oddychanie” (nieprzeczytane).
                float band = wv < 0. ? 0. : exp(-pow((p.y - (wv * 11. - 1.)) / .9, 2.));
                va *= 1. + bth * .7 + band * 2.2;
                vc = mix(vc, vec3(1., .93, .75), band * .6);
                va *= dim;
                gl_Position = vec4(q.x * sc / asp + offx * (-q.z), q.y * sc + off * (-q.z), 0., -q.z);
                gl_PointSize = (a.x > .5 && a.x < 1.5 ? 3.6 : 2.2 * (.85 + .15 * br)) * ps * (sc / 3.) * 28. / (-q.z);
            }
        """.trimIndent()

        const val DUST_COUNT = 900

        /** d = (x, y, ziarno, wielkość); pozycje w 0..1, przesuwają się powoli i zawijają przy brzegach. */
        val DUST_VERTEX_SHADER = """
            attribute vec4 d;
            uniform float t, ps, al;
            varying float va;
            varying vec3 vc;
            void main() {
                vec2 q = d.xy + vec2(
                    sin(t * .05 + d.z * 20.) * .03 + t * .003 * (d.z - .5),
                    t * .010 * (.35 + d.z)
                );
                q = fract(q) * 2. - 1.;
                float tw = .55 + .45 * sin(t * (.6 + d.z) + d.z * 50.);
                va = al * tw * (.22 + .4 * d.w);
                vc = mix(vec3(1., .56, .16), vec3(1., .85, .6), d.w);
                gl_Position = vec4(q, 0., 1.);
                gl_PointSize = (1.8 + 3.2 * d.w) * ps;
            }
        """.trimIndent()

        val FRAGMENT_SHADER = """
            precision mediump float;
            varying float va;
            varying vec3 vc;
            void main() {
                float d = length(gl_PointCoord - .5);
                float al = smoothstep(.5, 0., d) * va;
                gl_FragColor = vec4(vc * al, 1.);
            }
        """.trimIndent()
    }
}
