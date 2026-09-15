package org.communitypoke.fdroidai.ai

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.communitypoke.fdroidai.data.model.FdroidApp
import org.communitypoke.fdroidai.data.search.SearchEngine

/**
 * AI provider backed by an OpenAI-compatible `chat/completions` endpoint
 * (OpenAI, Azure, LiteLLM, Ollama, etc.). Configured through `BuildConfig`:
 *
 *   - `AI_API_KEY`  — bearer token; when blank the provider reports
 *     [isConfigured] = false and every call falls back to [fallback].
 *   - `AI_BASE_URL` — e.g. `https://api.openai.com/v1`
 *   - `AI_MODEL`    — e.g. `gpt-4o-mini`
 *
 * The provider pre-filters the catalog with [SearchEngine] so the prompt stays
 * small, asks the model to re-rank candidates as JSON, and merges the result
 * back into domain objects. Any failure falls back transparently.
 */
class RemoteAiSearchProvider(
    private val apiKey: String,
    private val baseUrl: String,
    private val model: String,
    private val json: Json,
    private val okHttpClient: OkHttpClient,
    private val fallback: AiSearchProvider,
    private val candidateCount: Int = 40,
) : AiSearchProvider {

    override val name = "AI ($model)"
    override val isConfigured = apiKey.isNotBlank()

    override suspend fun search(request: AiSearchRequest): AiSearchResponse {
        if (!isConfigured) {
            return fallback.search(request).copy(isFallback = true)
        }
        return try {
            remoteSearch(request)
        } catch (e: Exception) {
            val local = fallback.search(request)
            local.copy(
                isFallback = true,
                summary = "Remote AI unavailable (${e.javaClass.simpleName}) — " +
                    "showing on-device results. ${local.summary}",
            )
        }
    }

    private suspend fun remoteSearch(request: AiSearchRequest): AiSearchResponse =
        withContext(Dispatchers.IO) {
            val candidates = SearchEngine.search(
                query = request.query,
                apps = request.apps,
                limit = candidateCount,
            ).ifEmpty { request.apps.sortedByDescending { it.lastUpdated }.take(10) }
            val byPackage = candidates.associateBy { it.packageName }

            val prompt = buildPrompt(request.query, candidates)
            val body = ChatRequest(
                model = model,
                messages = listOf(
                    ChatMessage("system", SYSTEM_PROMPT),
                    ChatMessage("user", prompt),
                ),
                responseFormat = buildJsonObject { put("type", "json_object") },
            )
            val httpRequest = Request.Builder()
                .url("${baseUrl.trimEnd('/')}/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(json.encodeToString(ChatRequest.serializer(), body).toRequestBody(JSON))
                .build()

            val response = okHttpClient.newCall(httpRequest).execute()
            response.use {
                if (!it.isSuccessful) throw IOException("AI endpoint HTTP ${it.code}")
                val text = it.body?.string() ?: throw IOException("Empty AI response")
                val chat = json.decodeFromString(ChatResponse.serializer(), text)
                val content = chat.choices.firstOrNull()?.message?.content
                    ?: throw IOException("AI response had no choices")
                parseRanked(content, byPackage, request.limit)
            }
        }

    private fun buildPrompt(query: String, candidates: List<FdroidApp>): String {
        val catalog = candidates.joinToString("\n") { app ->
            "- ${app.packageName} | ${app.name} | ${app.summary} | " +
                app.categories.joinToString(",")
        }
        return """
            User query: "$query"

            Candidate apps (packageName | name | summary | categories):
            $catalog

            Rank the apps that best satisfy the query. Respond with JSON only:
            {"results":[{"packageName":"...","reason":"one short phrase","score":0.0-1.0}]}
        """.trimIndent()
    }

    private fun parseRanked(
        content: String,
        byPackage: Map<String, FdroidApp>,
        limit: Int,
    ): AiSearchResponse {
        val parsed = json.decodeFromString(AiRankList.serializer(), content)
        val results = parsed.results.mapNotNull { r ->
            byPackage[r.packageName]?.let { app ->
                AiRankedApp(app = app, score = r.score.coerceIn(0f, 1f), reason = r.reason)
            }
        }.sortedByDescending { it.score }.take(limit)
        return AiSearchResponse(
            results = results,
            summary = "Ranked by $model semantic relevance.",
            providerName = name,
        )
    }

    @Serializable
    private data class ChatRequest(
        val model: String,
        val messages: List<ChatMessage>,
        val temperature: Float = 0.2f,
        @SerialName("response_format") val responseFormat: kotlinx.serialization.json.JsonObject? = null,
    )

    @Serializable
    private data class ChatMessage(val role: String, val content: String)

    @Serializable
    private data class ChatResponse(val choices: List<Choice> = emptyList()) {
        @Serializable
        data class Choice(val message: ChatMessage)
    }

    @Serializable
    private data class AiRankList(val results: List<AiRank> = emptyList())

    @Serializable
    private data class AiRank(
        val packageName: String,
        val reason: String = "",
        val score: Float = 0.5f,
    )

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()

        const val SYSTEM_PROMPT =
            "You are the semantic search engine of an F-Droid client. " +
                "Given a natural-language query and a candidate app catalog, " +
                "return only the most relevant apps as strict JSON. " +
                "Never invent package names; only use ones from the candidate list."
    }
}
