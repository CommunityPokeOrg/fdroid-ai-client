package org.communitypoke.fdroidai.userscript

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Backing store for the `GM_*Value` APIs — a single JSON document mapping
 * `scriptId -> { key: arbitraryJsonValue }`. Synchronous + synchronized
 * because it is called from the WebView's `@JavascriptInterface` bridge.
 */
class GmValueStore(
    private val file: File,
    private val json: Json,
) {

    private val lock = Any()

    @Volatile
    private var cache: MutableMap<String, MutableMap<String, JsonElement>>? = null

    fun getValue(scriptId: String, key: String): JsonElement? = synchronized(lock) {
        data()[scriptId]?.get(key)
    }

    fun setValue(scriptId: String, key: String, value: JsonElement) = synchronized(lock) {
        val map = data().getOrPut(scriptId) { mutableMapOf() }
        map[key] = value
        persist()
    }

    fun deleteValue(scriptId: String, key: String) = synchronized(lock) {
        data()[scriptId]?.remove(key)
        persist()
    }

    fun listValues(scriptId: String): List<String> = synchronized(lock) {
        data()[scriptId]?.keys?.sorted().orEmpty()
    }

    /** Remove all stored values for a script (called on uninstall). */
    fun clearScript(scriptId: String) = synchronized(lock) {
        if (data().remove(scriptId) != null) persist()
    }

    private fun data(): MutableMap<String, MutableMap<String, JsonElement>> {
        cache?.let { return it }
        val loaded = mutableMapOf<String, MutableMap<String, JsonElement>>()
        try {
            if (file.exists()) {
                val root = json.parseToJsonElement(file.readText()).jsonObject
                for ((scriptId, entries) in root) {
                    loaded[scriptId] = entries.jsonObject.toMutableMap()
                }
            }
        } catch (e: Exception) {
            // Corrupt store — start empty rather than crash the bridge.
        }
        cache = loaded
        return loaded
    }

    private fun persist() {
        try {
            file.parentFile?.mkdirs()
            val root = JsonObject(cache.orEmpty().mapValues { (_, v) -> JsonObject(v) })
            file.writeText(json.encodeToString(JsonObject.serializer(), root))
        } catch (e: Exception) {
            // Best-effort persistence; values still live in memory.
        }
    }
}
