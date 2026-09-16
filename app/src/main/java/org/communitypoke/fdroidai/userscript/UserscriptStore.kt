package org.communitypoke.fdroidai.userscript

import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Disk persistence for installed userscripts — one JSON file per script under
 * `<filesDir>/userscripts/`. The stored `source` is authoritative; metadata is
 * re-parsed on load so edits never produce stale metadata.
 */
class UserscriptStore(
    private val dir: File,
    private val json: Json,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    private val mutex = Mutex()

    suspend fun list(): List<Userscript> = mutex.withLock {
        withContext(ioDispatcher) {
            dir.listFiles { f -> f.extension == "json" }
                ?.mapNotNull { readScript(it) }
                ?.sortedBy { it.name.lowercase() }
                .orEmpty()
        }
    }

    suspend fun get(id: String): Userscript? = mutex.withLock {
        withContext(ioDispatcher) {
            fileFor(id)?.takeIf { it.exists() }?.let { readScript(it) }
        }
    }

    /** Insert or replace a script. `updatedAt` is stamped here. */
    suspend fun save(script: Userscript): Userscript = mutex.withLock {
        withContext(ioDispatcher) {
            dir.mkdirs()
            val existing = fileFor(script.id)?.takeIf { it.exists() }?.let { readScript(it) }
            val stamped = script.copy(
                installedAt = existing?.installedAt
                    ?: script.installedAt.takeIf { it > 0 }
                    ?: System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
            )
            val file = fileFor(stamped.id) ?: dir.resolve("${stamped.id}.json")
            file.writeText(json.encodeToString(stamped.toDto()))
            stamped
        }
    }

    suspend fun delete(id: String): Boolean = mutex.withLock {
        withContext(ioDispatcher) { fileFor(id)?.delete() == true }
    }

    suspend fun setEnabled(id: String, enabled: Boolean): Userscript? {
        val script = get(id) ?: return null
        return save(script.copy(enabled = enabled))
    }

    private fun fileFor(id: String): File? {
        // Guard against path traversal — ids are slugified by the parser but
        // belt-and-suspenders since the value lands in a file name.
        val safe = id.replace(Regex("[^a-z0-9-]"), "-").trim('-').take(80)
        return if (safe.isBlank()) null else dir.resolve("$safe.json")
    }

    private fun readScript(file: File): Userscript? = try {
        val dto = json.decodeFromString(StoredScript.serializer(), file.readText())
        val metadata = UserscriptParser.parse(dto.source).metadata
        Userscript(
            id = dto.id,
            name = dto.name.ifBlank { metadata.name },
            source = dto.source,
            metadata = metadata,
            enabled = dto.enabled,
            installedAt = dto.installedAt,
            updatedAt = dto.updatedAt,
        )
    } catch (e: Exception) {
        null // corrupt file — skip rather than crash the list
    }

    private fun Userscript.toDto() = StoredScript(
        id = id,
        name = name,
        source = source,
        enabled = enabled,
        installedAt = installedAt,
        updatedAt = updatedAt,
    )

    @Serializable
    private data class StoredScript(
        val id: String,
        val name: String,
        val source: String,
        val enabled: Boolean = true,
        val installedAt: Long = 0L,
        val updatedAt: Long = 0L,
    )
}
