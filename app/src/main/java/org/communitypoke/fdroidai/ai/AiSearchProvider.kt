package org.communitypoke.fdroidai.ai

import org.communitypoke.fdroidai.data.model.FdroidApp

/**
 * Abstraction over AI-powered semantic search providers.
 *
 * Implementations receive the user's natural-language query plus the app
 * catalog and return a ranked list with per-app human-readable reasons.
 */
interface AiSearchProvider {
    /** Human-readable provider name for UI badges, e.g. "On-device mock" or "GPT-4o mini". */
    val name: String

    /** False when required configuration (e.g. an API key) is missing. */
    val isConfigured: Boolean

    suspend fun search(request: AiSearchRequest): AiSearchResponse
}

data class AiSearchRequest(
    val query: String,
    val apps: List<FdroidApp>,
    val limit: Int = 15,
)

data class AiSearchResponse(
    val results: List<AiRankedApp>,
    val summary: String,
    /** Name of the provider that actually produced the results (may differ on fallback). */
    val providerName: String,
    /** True when a primary provider fell back to a simpler implementation. */
    val isFallback: Boolean = false,
)

data class AiRankedApp(
    val app: FdroidApp,
    val score: Float,
    val reason: String,
)
