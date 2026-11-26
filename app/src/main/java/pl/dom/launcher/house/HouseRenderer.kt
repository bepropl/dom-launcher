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
    /** Wielkość i pionowe przesunięcie domku na ekranie. */
    @Volatile var scale = 1.85f
    @Volatile var offsetY = -0.17f

    private val rooms = FloatArray(HouseRooms.COUNT)
    private var program = 0
    private var vbo = 0
    private var width = 1
    private var height = 1
    private var angle = Math.PI.toFloat() + 0.45f
    private var lastFrameNanos = 0L
    private val startNanos = System.nanoTime()

    private var uTime = 0; private var uAng = 0; private var uPit = 0; private var uAsp = 0
    private var uPointScale = 0; private var uScale = 0; private var uOff = 0; private var uDim = 0; private var uRooms = 0
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
        uDim = GLES20.glGetUniformLocation(program, "dim")
        uRooms = GLES20.glGetUniformLocation(program, "rm")

        val ids = IntArray(1)
        GLES20.glGenBuffers(1, ids, 0)
        vbo = ids[0]
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        model.data.position(0)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, model.count * HouseModel.FLOATS_PER_POINT * 4, model.data, GLES20.GL_STATIC_DRAW)

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

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(program)
        GLES20.glUniform1f(uTime, (now - startNanos) / 1e9f)
        GLES20.glUniform1f(uAng, angle)
        GLES20.glUniform1f(uPit, 0.24f)
        GLES20.glUniform1f(uAsp, width.toFloat() / height)
        GLES20.glUniform1f(uPointScale, height / 1080f)
        GLES20.glUniform1f(uScale, scale)
        GLES20.glUniform1f(uOff, offsetY)
        GLES20.glUniform1f(uDim, dim)
        GLES20.glUniform1fv(uRooms, HouseRooms.COUNT, rooms, 0)

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        val stride = HouseModel.FLOATS_PER_POINT * 4
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, stride, 0)
        GLES20.glEnableVertexAttribArray(aAttr)
        GLES20.glVertexAttribPointer(aAttr, 4, GLES20.GL_FLOAT, false, stride, 12)
        val points = (model.count * detail.coerceIn(0.1f, 1f)).toInt()
        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, points)
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
            uniform float t, ang, pit, asp, ps, sc, off, dim;
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
                if (a.x < .5) { va = a.w * tw * (.5 + lit * .8); vc = mix(am, vec3(1., .9, .7), lit * .7); }
                else if (a.x < 1.5) { va = lit * tw; vc = vec3(1., .86, .6); }
                else { va = .25 * tw; vc = vec3(1., .6, .25); }
                va *= dim;
                gl_Position = vec4(q.x * sc / asp, q.y * sc + off * (-q.z), 0., -q.z);
                gl_PointSize = (a.x > .5 && a.x < 1.5 ? 2.7 : 2.2) * ps * (sc / 3.) * 28. / (-q.z);
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
