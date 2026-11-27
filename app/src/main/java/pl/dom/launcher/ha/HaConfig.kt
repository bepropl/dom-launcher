package pl.dom.launcher.ha

import org.json.JSONArray
import org.json.JSONObject

/**
 * Które encje Home Assistanta pokazuje launcher. Wybierane na stronie w telefonie.
 * [rooms]: numer pokoju na domku (HouseRooms) → encja światła (light.* albo switch.*).
 * [certSha256]: odcisk zaufanego certyfikatu HA (gdy HA ma własny certyfikat, nie z zaufanego urzędu).
 */
data class HaConfig(
    val weather: String? = null,
    val outsideTemp: String? = null,
    val insideTemp: String? = null,
    val persons: List<String> = emptyList(),
    val rooms: Map<Int, String> = emptyMap(),
    val certSha256: String? = null,
) {
    fun toJson(): String = JSONObject()
        .put("weather", weather ?: JSONObject.NULL)
        .put("outsideTemp", outsideTemp ?: JSONObject.NULL)
        .put("insideTemp", insideTemp ?: JSONObject.NULL)
        .put("persons", JSONArray(persons))
        .put("rooms", JSONObject().apply { rooms.forEach { (k, v) -> put(k.toString(), v) } })
        .put("certSha256", certSha256 ?: JSONObject.NULL)
        .toString()

    companion object {
        fun fromJson(json: String?): HaConfig {
            if (json.isNullOrBlank()) return HaConfig()
            return runCatching {
                val o = JSONObject(json)
                fun str(name: String) = o.optString(name, "").takeIf { it.isNotBlank() && it != "null" }
                val persons = o.optJSONArray("persons")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
                val roomsJson = o.optJSONObject("rooms")
                val rooms = roomsJson?.keys()?.asSequence()?.mapNotNull { key ->
                    val entity = roomsJson.optString(key).takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    key.toIntOrNull()?.let { it to entity }
                }?.toMap().orEmpty()
                HaConfig(str("weather"), str("outsideTemp"), str("insideTemp"), persons, rooms, str("certSha256"))
            }.getOrDefault(HaConfig())
        }
    }
}
