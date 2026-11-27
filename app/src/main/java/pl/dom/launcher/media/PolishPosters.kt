package pl.dom.launcher.media

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Polskie okładki z TMDB. TMDB nie zawsze ustawia polską okładkę jako domyślną, więc dla każdego
 * tytułu sprawdzamy listę okładek raz i zapamiętujemy wynik (na dysku):
 * znaleziona – sprawdzamy znów po 60 dniach, brak – po 14 dniach (może ktoś doda).
 */
class PolishPosters(context: Context, private val tmdb: Tmdb) {
    private val file = File(context.filesDir, "posters_pl.json")
    /** klucz „m123”/„t123” → „ścieżka|dzień” (pusta ścieżka = brak polskiej okładki). */
    private val cache = ConcurrentHashMap<String, String>()
    @Volatile private var loaded = false
    private val limit = Semaphore(6)

    private fun key(type: MediaType, id: Int) = (if (type == MediaType.MOVIE) "m" else "t") + id
    private fun today() = System.currentTimeMillis() / 86_400_000L

    private fun load() {
        if (loaded) return
        loaded = true
        runCatching {
            val o = JSONObject(file.readText())
            o.keys().forEach { k -> cache[k] = o.getString(k) }
        }
    }

    private fun save() {
        runCatching { file.writeText(JSONObject(cache as Map<*, *>).toString()) }
    }

    /** Polska okładka dla tytułu (z pamięci albo z TMDB); null = brak polskiej. */
    private fun lookup(type: MediaType, id: Int): String? {
        val k = key(type, id)
        cache[k]?.let { entry ->
            val path = entry.substringBefore('|')
            val day = entry.substringAfter('|').toLongOrNull() ?: 0
            val maxAge = if (path.isEmpty()) 14 else 60
            if (today() - day < maxAge) return path.ifEmpty { null }
        }
        val found = runCatching { tmdb.polishPoster(type, id) }.getOrElse { return cache[k]?.substringBefore('|')?.ifEmpty { null } }
        cache[k] = (found ?: "") + "|" + today()
        return found
    }

    suspend fun apply(items: List<MediaItem>): List<MediaItem> = withContext(Dispatchers.IO) {
        load()
        val result = coroutineScope {
            items.map { item ->
                async { limit.withPermit { lookup(item.type, item.tmdbId)?.let { item.copy(posterPath = it) } ?: item } }
            }.awaitAll()
        }
        save()
        result
    }

    /** Pojedynczy tytuł (np. przy dodawaniu do listy). */
    suspend fun posterFor(type: MediaType, id: Int): String? = withContext(Dispatchers.IO) {
        load()
        lookup(type, id).also { save() }
    }
}
