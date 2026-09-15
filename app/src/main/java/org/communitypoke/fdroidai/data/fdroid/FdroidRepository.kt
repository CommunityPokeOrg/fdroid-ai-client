package org.communitypoke.fdroidai.data.fdroid

import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import org.communitypoke.fdroidai.data.model.FdroidApp
import org.communitypoke.fdroidai.data.model.IndexV2
import org.communitypoke.fdroidai.data.model.RepoMetadata
import org.communitypoke.fdroidai.data.model.toDomain
import org.communitypoke.fdroidai.data.model.toRepoMetadata

/**
 * Single source of truth for the F-Droid repository contents.
 *
 * Downloads `index-v2.json` once, caches the raw file on disk, and exposes an
 * in-memory snapshot for fast search/browse. On network failure a previously
 * cached index is used as a fallback.
 */
@OptIn(ExperimentalSerializationApi::class)
class FdroidRepository(
    private val service: FdroidIndexService,
    private val json: Json,
    private val cacheDir: File,
    private val indexUrl: String = DEFAULT_INDEX_URL,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    companion object {
        const val DEFAULT_INDEX_URL = "https://f-droid.org/repo/index-v2.json"
        private const val INDEX_FILE = "fdroid-index-v2.json"

        /** Re-use an on-disk index younger than this instead of re-downloading. */
        private const val CACHE_TTL_MS = 24L * 60 * 60 * 1000
    }

    /** Immutable view of the repo, cheap to hand to the UI layer. */
    data class RepoSnapshot(
        val metadata: RepoMetadata,
        val apps: List<FdroidApp>,
        val byPackageName: Map<String, FdroidApp>,
        val byCategory: Map<String, List<FdroidApp>>,
        val fromCache: Boolean,
    )

    private val mutex = Mutex()

    @Volatile
    private var snapshot: RepoSnapshot? = null

    suspend fun getSnapshot(): RepoSnapshot = mutex.withLock {
        snapshot ?: loadIntoMemory().also { snapshot = it }
    }

    /** Force a re-download of the index (with fallback to the cached file). */
    suspend fun refresh(): RepoSnapshot = mutex.withLock {
        loadIntoMemory(forceDownload = true).also { snapshot = it }
    }

    suspend fun getApp(packageName: String): FdroidApp? =
        getSnapshot().byPackageName[packageName]

    private suspend fun loadIntoMemory(forceDownload: Boolean = false): RepoSnapshot =
        withContext(ioDispatcher) {
            val indexFile = File(cacheDir, INDEX_FILE)
            val freshEnough = indexFile.exists() &&
                System.currentTimeMillis() - indexFile.lastModified() < CACHE_TTL_MS
            var fromCache = false
            if (!forceDownload && freshEnough) {
                fromCache = true
            } else {
                try {
                    download(indexFile)
                } catch (e: Exception) {
                    if (indexFile.exists()) {
                        fromCache = true
                    } else {
                        throw IndexUnavailableException(
                            "Could not download the F-Droid index and no cached copy exists."
                        )
                    }
                }
            }
            val index = parseIndex(indexFile)
            mapToSnapshot(index, fromCache = fromCache)
        }

    private suspend fun download(target: File) {
        cacheDir.mkdirs()
        val tmp = File(cacheDir, "$INDEX_FILE.tmp")
        val response = service.downloadIndex(indexUrl)
        if (!response.isSuccessful) {
            throw IndexUnavailableException("Index download failed with HTTP ${response.code()}")
        }
        response.body()?.byteStream()?.use { input ->
            tmp.outputStream().use { output -> input.copyTo(output) }
        } ?: throw IndexUnavailableException("Index download had an empty body")
        tmp.renameTo(target)
    }

    private fun parseIndex(file: File): IndexV2 =
        file.inputStream().use { json.decodeFromStream(IndexV2.serializer(), it) }

    private fun mapToSnapshot(index: IndexV2, fromCache: Boolean): RepoSnapshot {
        val repoAddress = index.repo.address.ifBlank {
            indexUrl.substringBeforeLast('/')
        }
        val apps = index.packages
            .map { (pkg, dto) -> dto.toDomain(pkg, repoAddress) }
            .sortedByDescending { it.lastUpdated }
        val byCategory = mutableMapOf<String, MutableList<FdroidApp>>()
        apps.forEach { app ->
            app.categories.forEach { cat ->
                byCategory.getOrPut(cat) { mutableListOf() }.add(app)
            }
        }
        return RepoSnapshot(
            metadata = index.toRepoMetadata(appCount = apps.size),
            apps = apps,
            byPackageName = apps.associateBy { it.packageName },
            byCategory = byCategory,
            fromCache = fromCache,
        )
    }
}

class IndexUnavailableException(message: String) : Exception(message)
