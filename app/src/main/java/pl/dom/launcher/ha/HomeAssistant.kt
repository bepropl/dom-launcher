package pl.dom.launcher.ha

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import pl.dom.launcher.data.SecretStore
import pl.dom.launcher.data.SettingsRepository
import pl.dom.launcher.house.HouseRooms
import pl.dom.launcher.ui.HouseLights
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicInteger

/** Stan jednej encji HA. */
data class HaState(val entityId: String, val state: String, val attributes: JSONObject) {
    val name: String get() = attributes.optString("friendly_name").ifBlank { entityId }
    val domain: String get() = entityId.substringBefore('.')
    val unit: String? get() = attributes.optString("unit_of_measurement").takeIf { it.isNotBlank() }
    val number: Double? get() = state.replace(',', '.').toDoubleOrNull()
}

sealed interface HaStatus {
    data object Off : HaStatus
    data object Connecting : HaStatus
    data object Connected : HaStatus
    data class Error(val message: String) : HaStatus
    data class Untrusted(val fingerprint: String) : HaStatus
}

/** Powiadomienie wysłane z automatyzacji HA (zdarzenie `dom_launcher_notify`). */
data class HaNotification(
    val title: String,
    val message: String,
    val image: Bitmap? = null,
    val seconds: Int = 10,
    val at: Long = System.currentTimeMillis(),
)

/**
 * Stałe połączenie WebSocket z Home Assistantem:
 *  - stany wszystkich encji (pasek u góry, światła na domku),
 *  - zdarzenia `dom_launcher_notify` → powiadomienia na ekranie.
 * Po zerwaniu łączy się ponownie (co 5 s, potem rzadziej).
 */
class HomeAssistant(
    private val settings: SettingsRepository,
    private val secrets: SecretStore,
    private val scope: CoroutineScope,
) {
    private val _status = MutableStateFlow<HaStatus>(HaStatus.Off)
    val status: StateFlow<HaStatus> = _status.asStateFlow()

    /** Wszystkie encje (do wyboru na stronie w telefonie) – bez powiadamiania ekranu o każdej zmianie. */
    private val all = java.util.concurrent.ConcurrentHashMap<String, HaState>()
    fun allStates(): List<HaState> = all.values.toList()

    /**
     * Tylko encje wybrane do pokazania. HA potrafi wysyłać kilka zmian na sekundę
     * (liczniki prądu itp.) – ekran odświeża się wyłącznie, gdy zmieni się coś, co widać.
     */
    @Volatile private var watched: Set<String> = emptySet()
    private val _states = MutableStateFlow<Map<String, HaState>>(emptyMap())
    val states: StateFlow<Map<String, HaState>> = _states.asStateFlow()

    /** Dane do paska u góry i wygaszacza; zmienia się tylko, gdy zmieni się to, co widać. */
    val summary: StateFlow<HaSummary?> by lazy {
        combine(_states, settings.flow.map { it.ha }.distinctUntilChanged()) { s, c -> summarize(s, c) }
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.Eagerly, null)
    }

    private fun publishWatched() {
        val w = watched
        _states.value = w.mapNotNull { id -> all[id]?.let { id to it } }.toMap()
    }

    private val _notifications = MutableSharedFlow<HaNotification>(extraBufferCapacity = 8)
    val notifications: SharedFlow<HaNotification> = _notifications.asSharedFlow()

    private val _recent = MutableStateFlow<List<HaNotification>>(emptyList())
    /** Ostatnie powiadomienia (bez zdjęć) – lista pod dzwonkiem. */
    val recent: StateFlow<List<HaNotification>> = _recent.asStateFlow()

    private val restart = MutableStateFlow(0)
    private val ids = AtomicInteger(1)

    @Volatile private var baseUrl = ""
    @Volatile private var http: OkHttpClient? = null

    fun start() {
        scope.launch {
            combine(
                settings.flow.map { it.haUrl to it.ha.certSha256 }.distinctUntilChanged(),
                restart,
            ) { connection, _ -> connection }.collectLatest { (url, pin) -> connectLoop(url, pin) }
        }
        scope.launch {
            settings.flow.map { it.ha }.distinctUntilChanged().collect { c ->
                watched = buildSet {
                    c.weather?.let(::add); c.outsideTemp?.let(::add); c.insideTemp?.let(::add)
                    addAll(c.persons); c.rooms.values.forEach { addAll(it) }
                }
                publishWatched()
            }
        }
        // Światła na domku: włączona lampa w pokoju rozjaśnia ten pokój.
        scope.launch {
            combine(states, settings.flow.map { it.ha.rooms }.distinctUntilChanged()) { current, rooms ->
                FloatArray(HouseRooms.COUNT).also { levels ->
                    rooms.forEach { (room, entities) ->
                        // Pokój świeci, gdy którekolwiek z jego świateł jest włączone (najjaśniejsze wygrywa).
                        if (room in levels.indices) levels[room] = entities.maxOfOrNull { lightLevel(current[it]) } ?: 0f
                    }
                }
            }.distinctUntilChanged { a, b -> a.contentEquals(b) }.collect { HouseLights.rooms.value = it }
        }
    }

    /** Po zmianie adresu lub tokenu na stronie w telefonie. */
    fun reconnect() {
        restart.value++
    }

    private fun lightLevel(state: HaState?): Float {
        if (state == null || state.state != "on") return 0f
        val brightness = state.attributes.optInt("brightness", -1)
        return if (brightness >= 0) 0.35f + 0.65f * (brightness / 255f) else 1f
    }

    private suspend fun connectLoop(url: String, pin: String?) {
        var wait = 5_000L
        while (true) {
            val token = secrets.get(SecretStore.HA_TOKEN)
            if (url.isBlank() || token.isNullOrBlank()) {
                _status.value = HaStatus.Off
                all.clear()
                _states.value = emptyMap()
                return
            }
            _status.value = HaStatus.Connecting
            try {
                runSocket(url, token, pin)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                _status.value = errorStatus(e)
            }
            delay(wait)
            wait = (wait * 2).coerceAtMost(60_000L)
            if (_status.value == HaStatus.Connected) wait = 5_000L
        }
    }

    private fun errorStatus(e: Throwable): HaStatus {
        var cause: Throwable? = e
        while (cause != null) {
            if (cause is UntrustedCertificateException) return HaStatus.Untrusted(cause.fingerprint)
            cause = cause.cause
        }
        val untrusted = HaTls.lastUntrusted
        if (untrusted != null && e is javax.net.ssl.SSLException) return HaStatus.Untrusted(untrusted)
        return HaStatus.Error(e.message ?: e.javaClass.simpleName)
    }

    private suspend fun runSocket(url: String, token: String, pin: String?) {
        val client = HaTls.client(pin)
        http = HaTls.client(pin, readTimeoutSeconds = 15)
        baseUrl = url
        val wsUrl = url.replaceFirst("https://", "wss://").replaceFirst("http://", "ws://") + "/api/websocket"
        val inbox = Channel<Any>(Channel.UNLIMITED)
        val socket = client.newWebSocket(
            Request.Builder().url(wsUrl).build(),
            object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, text: String) { inbox.trySend(text) }
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { inbox.trySend(t) }
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    inbox.trySend(IllegalStateException("Home Assistant zamknął połączenie"))
                }
            },
        )
        try {
            var statesRequest = -1
            for (item in inbox) {
                if (item is Throwable) throw item
                val msg = JSONObject(item as String)
                when (msg.optString("type")) {
                    "auth_required" -> socket.send(JSONObject().put("type", "auth").put("access_token", token).toString())
                    "auth_invalid" -> throw IllegalStateException("Nieprawidłowy token")
                    "auth_ok" -> {
                        statesRequest = send(socket, JSONObject().put("type", "get_states"))
                        send(socket, JSONObject().put("type", "subscribe_events").put("event_type", "state_changed"))
                        send(socket, JSONObject().put("type", "subscribe_events").put("event_type", NOTIFY_EVENT))
                        _status.value = HaStatus.Connected
                    }
                    "result" -> if (msg.optInt("id") == statesRequest && msg.optBoolean("success")) {
                        val list = msg.optJSONArray("result")
                        all.clear()
                        if (list != null) for (i in 0 until list.length()) parseState(list.getJSONObject(i))?.let { all[it.entityId] = it }
                        publishWatched()
                    }
                    "event" -> handleEvent(msg.optJSONObject("event") ?: continue)
                }
            }
        } finally {
            socket.cancel()
        }
    }

    private fun send(socket: WebSocket, message: JSONObject): Int {
        val id = ids.incrementAndGet()
        socket.send(message.put("id", id).toString())
        return id
    }

    private fun parseState(o: JSONObject?): HaState? {
        o ?: return null
        val id = o.optString("entity_id").takeIf { it.isNotBlank() } ?: return null
        return HaState(id, o.optString("state"), o.optJSONObject("attributes") ?: JSONObject())
    }

    private fun handleEvent(event: JSONObject) {
        val data = event.optJSONObject("data") ?: JSONObject()
        when (event.optString("event_type")) {
            "state_changed" -> {
                val id = data.optString("entity_id")
                val newState = parseState(data.optJSONObject("new_state"))
                if (newState == null) all.remove(id) else all[id] = newState
                if (id in watched) publishWatched()
            }
            NOTIFY_EVENT -> scope.launch { notifyFromHa(data) }
        }
    }

    private suspend fun notifyFromHa(data: JSONObject) {
        val camera = data.optString("camera").takeIf { it.isNotBlank() }
        val image = data.optString("image").takeIf { it.isNotBlank() }
        val bitmap = when {
            camera != null -> fetchImage("/api/camera_proxy/" + URLEncoder.encode(camera, "UTF-8"))
            image != null -> fetchImage(image)
            else -> null
        }
        show(
            HaNotification(
                title = data.optString("title").ifBlank { "Home Assistant" },
                message = data.optString("message"),
                image = bitmap,
                seconds = data.optInt("seconds", if (bitmap != null) 20 else 10).coerceIn(3, 120),
            ),
        )
    }

    /** Pokazuje powiadomienie (też do testu ze strony w telefonie). */
    fun show(notification: HaNotification) {
        _notifications.tryEmit(notification)
        // Zdjęcia zostają tylko przy 5 najnowszych (pamięć), lista trzyma 20 pozycji.
        _recent.value = (listOf(notification) + _recent.value).take(20)
            .mapIndexed { i, n -> if (i < 5 || n.image == null) n else n.copy(image = null) }
    }

    fun clearRecent() {
        _recent.value = emptyList()
    }

    /** Zdjęcie z HA (kamera albo ścieżka /local/…) z tokenem; zmniejszone do ok. 960 px szerokości. */
    suspend fun fetchImage(pathOrUrl: String): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val client = http ?: return@runCatching null
            val token = secrets.get(SecretStore.HA_TOKEN) ?: return@runCatching null
            val url = if (pathOrUrl.startsWith("http")) pathOrUrl else baseUrl + "/" + pathOrUrl.trimStart('/')
            val request = Request.Builder().url(url).header("Authorization", "Bearer $token").build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                val bytes = response.body?.bytes() ?: return@runCatching null
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= 960) sample *= 2
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            }
        }.getOrNull()
    }

    companion object {
        const val NOTIFY_EVENT = "dom_launcher_notify"
    }
}

/** Dane do paska u góry, wyliczone ze stanów i wybranych encji. */
data class HaSummary(
    val weatherIcon: String?,
    val weatherLabel: String?,
    val outside: String?,
    val inside: String?,
    val peopleHome: Int?,
    val peopleTotal: Int,
    val namesHome: List<String>,
)

fun summarize(states: Map<String, HaState>, config: HaConfig): HaSummary {
    val weather = config.weather?.let { states[it] }
    val weatherTemp = weather?.attributes?.optDouble("temperature")?.takeIf { !it.isNaN() }
    fun temp(entity: String?): String? = entity?.let { states[it] }?.number?.let { "%.0f°".format(it).replace("-0°", "0°") }
    val persons = config.persons.mapNotNull { states[it] }
    val home = persons.filter { it.state == "home" }
    return HaSummary(
        weatherIcon = weather?.let { weatherIcon(it.state) },
        weatherLabel = weather?.let { weatherLabel(it.state) },
        outside = temp(config.outsideTemp) ?: weatherTemp?.let { "%.0f°".format(it).replace("-0°", "0°") },
        inside = temp(config.insideTemp),
        peopleHome = if (config.persons.isEmpty()) null else home.size,
        peopleTotal = config.persons.size,
        namesHome = home.map { it.name },
    )
}

fun weatherLabel(condition: String): String = when (condition) {
    "sunny" -> "Słonecznie"
    "clear-night" -> "Pogodnie"
    "partlycloudy" -> "Częściowe zachmurzenie"
    "cloudy" -> "Pochmurno"
    "rainy" -> "Deszcz"
    "pouring" -> "Ulewa"
    "snowy" -> "Śnieg"
    "snowy-rainy" -> "Deszcz ze śniegiem"
    "fog" -> "Mgła"
    "windy", "windy-variant" -> "Wietrznie"
    "lightning", "lightning-rainy" -> "Burza"
    "hail" -> "Grad"
    else -> "Pogoda"
}

fun weatherIcon(condition: String): String = when (condition) {
    "sunny" -> "☀"
    "clear-night" -> "☾"
    "partlycloudy" -> "⛅"
    "cloudy", "fog" -> "☁"
    "rainy", "pouring" -> "☂"
    "snowy", "snowy-rainy", "hail" -> "❄"
    "windy", "windy-variant" -> "≋"
    "lightning", "lightning-rainy" -> "⚡"
    else -> "☁"
}
