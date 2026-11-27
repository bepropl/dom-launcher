package pl.dom.launcher.ha

import org.json.JSONArray
import org.json.JSONObject

/**
 * Które encje Home Assistanta pokazuje launcher. Wybierane na stronie w telefonie.
 * [rooms]: numer pokoju na domku (HouseRooms) → encje świateł (light.* / switch.*); pokój świeci,
 * gdy którakolwiek z nich jest włączona.
 * [certSha256]: odcisk zaufanego certyfikatu HA (gdy HA ma własny certyfikat, nie z zaufanego urzędu).
 */
data class HaConfig(
    val weather: String? = null,
    val outsideTemp: String? = null,
    val insideTemp: String? = null,
    val persons: List<String> = emptyList(),
    val rooms: Map<Int, List<String>> = emptyMap(),
    val certSha256: String? = null,
) {
    fun toJson(): String = JSONObject()
        .put("weather", weather ?: JSONObject.NULL)
        .put("outsideTemp", outsideTemp ?: JSONObject.NULL)
        .put("insideTemp", insideTemp ?: JSONObject.NULL)
        .put("persons", JSONArray(persons))
        .put("rooms", JSONObject().apply { rooms.forEach { (k, v) -> put(k.toString(), JSONArray(v)) } })
        .put("certSha256", certSha256 ?: JSONObject.NULL)
        .toString()

    companion object {
        /** Pokoje: {"1": ["light.a", "light.b"]} – przyjmuje też stary zapis {"1": "light.a"}. */
        fun parseRooms(o: JSONObject): Map<Int, List<String>> = o.keys().asSequence().mapNotNull { key ->
            val room = key.toIntOrNull() ?: return@mapNotNull null
            val entities = when (val v = o.opt(key)) {
                is JSONArray -> (0 until v.length()).map { v.optString(it) }
                is String -> listOf(v)
                else -> emptyList()
            }.filter { it.isNotBlank() && it != "null" }.distinct()
            if (entities.isEmpty()) null else room to entities
        }.toMap()

        fun fromJson(json: String?): HaConfig {
            if (json.isNullOrBlank()) return HaConfig()
            return runCatching {
                val o = JSONObject(json)
                fun str(name: String) = o.optString(name, "").takeIf { it.isNotBlank() && it != "null" }
                val persons = o.optJSONArray("persons")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
                val roomsJson = o.optJSONObject("rooms")
                val rooms = roomsJson?.let { parseRooms(it) }.orEmpty()
                HaConfig(str("weather"), str("outsideTemp"), str("insideTemp"), persons, rooms, str("certSha256"))
            }.getOrDefault(HaConfig())
        }
    }
}
