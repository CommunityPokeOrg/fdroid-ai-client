package org.communitypoke.fdroidai.ai

import kotlinx.coroutines.delay
import org.communitypoke.fdroidai.data.model.FdroidApp
import org.communitypoke.fdroidai.data.search.SearchEngine

/**
 * On-device "AI" provider used when no remote API key is configured.
 *
 * It simulates semantic search with a synonym/expansion table, concept
 * extraction and weighted field scoring, and produces per-app reasons plus a
 * natural-language summary — the same contract a real LLM provider fulfils.
 */
class MockAiSearchProvider : AiSearchProvider {

    override val name = "On-device AI (demo)"
    override val isConfigured = true

    override suspend fun search(request: AiSearchRequest): AiSearchResponse {
        delay(600) // simulate inference latency so the loading state is visible
        val intent = QueryIntent.parse(request.query)
        val ranked = request.apps.asSequence()
            .map { app -> rank(app, intent) }
            .filter { it.score >= MIN_SCORE }
            .sortedByDescending { it.score }
            .take(request.limit)
            .map { scored ->
                AiRankedApp(
                    app = scored.app,
                    score = scored.score,
                    reason = scored.reasons.joinToString("; "),
                )
            }
            .toList()
        return AiSearchResponse(
            results = ranked,
            summary = buildSummary(request.query, intent, ranked.size),
            providerName = name,
        )
    }

    internal data class QueryIntent(
        val rawTerms: List<String>,
        val concepts: Map<String, List<String>>,
    ) {
        companion object {
            fun parse(query: String): QueryIntent {
                val terms = SearchEngine.tokenize(query)
                val concepts = mutableMapOf<String, List<String>>()
                for (term in terms) {
                    for ((concept, triggers) in CONCEPT_SYNONYMS) {
                        val hitsConcept = term == concept || concept in term
                        val hitsTrigger = triggers.any { term in it || it in term }
                        if (hitsConcept || hitsTrigger) {
                            concepts[concept] = triggers + concept
                        }
                    }
                }
                return QueryIntent(rawTerms = terms, concepts = concepts)
            }
        }
    }

    internal data class Scored(val app: FdroidApp, val score: Float, val reasons: List<String>)

    private fun rank(app: FdroidApp, intent: QueryIntent): Scored {
        val name = app.name.lowercase()
        val summary = app.summary.lowercase()
        val description = app.description.lowercase()
        val haystack = "$name $summary $description ${app.categories.joinToString(" ").lowercase()}"

        var score = 0f
        val reasons = mutableListOf<String>()

        for (term in intent.rawTerms) {
            when {
                name.contains(term) -> {
                    score += 0.30f
                    reasons += "name matches \"$term\""
                }
                summary.contains(term) -> {
                    score += 0.18f
                    reasons += "summary mentions \"$term\""
                }
                haystack.contains(term) -> score += 0.06f
            }
        }

        for ((concept, expansions) in intent.concepts) {
            val hits = expansions.count { haystack.contains(it) }
            if (hits > 0) {
                score += 0.10f * hits.coerceAtMost(4)
                reasons += "relevant to \"$concept\""
            }
            if (app.categories.any { it.equals(concept, ignoreCase = true) }) {
                score += 0.15f
                reasons += "category: $concept"
            }
        }

        // Small freshness nudge — recently maintained apps rank slightly higher.
        val ageDays = (System.currentTimeMillis() - app.lastUpdated) / 86_400_000L
        if (ageDays in 0..180) score += 0.05f

        return Scored(app, score.coerceIn(0f, 1f), reasons.distinct().take(3))
    }

    private fun buildSummary(query: String, intent: QueryIntent, resultCount: Int): String {
        if (resultCount == 0) {
            return "No apps matched \"$query\". Try broader terms or a different description."
        }
        val concepts = if (intent.concepts.isEmpty()) {
            "your keywords"
        } else {
            intent.concepts.keys.joinToString(", ")
        }
        return "Interpreted \"$query\" as looking for $concepts — " +
            "here are the $resultCount best matches, ranked by semantic relevance."
    }

    private companion object {
        const val MIN_SCORE = 0.10f

        /**
         * concept -> trigger words. A query term that equals the concept or
         * contains/is contained in a trigger expands the query to all triggers.
         */
        val CONCEPT_SYNONYMS: Map<String, List<String>> = mapOf(
            "photo" to listOf("camera", "image", "picture", "gallery", "photograph"),
            "music" to listOf("audio", "song", "player", "sound", "mp3"),
            "video" to listOf("movie", "player", "media", "stream", "youtube"),
            "vpn" to listOf("proxy", "tor", "privacy", "tunnel", "wireguard", "openvpn"),
            "privacy" to listOf("security", "encrypted", "anonymous", "tracker", "tor"),
            "security" to listOf("privacy", "encrypted", "password", "2fa", "authenticator"),
            "password" to listOf("keepass", "vault", "credential", "authenticator", "2fa"),
            "chat" to listOf("messaging", "messenger", "irc", "xmpp", "matrix", "conversation"),
            "email" to listOf("mail", "inbox", "smtp", "imap"),
            "browser" to listOf("web", "internet", "surf", "firefox"),
            "map" to listOf("osm", "openstreetmap", "navigation", "gps", "routing"),
            "navigation" to listOf("map", "gps", "osm", "routing", "directions"),
            "game" to listOf("play", "puzzle", "arcade", "fun", "emulator"),
            "podcast" to listOf("audio", "rss", "episode", "radio"),
            "news" to listOf("rss", "feed", "reader", "article"),
            "rss" to listOf("feed", "news", "reader", "atom", "podcast"),
            "read" to listOf("reader", "ebook", "epub", "pdf", "book"),
            "book" to listOf("ebook", "epub", "reader", "library"),
            "note" to listOf("notes", "markdown", "todo", "journal", "write"),
            "todo" to listOf("task", "checklist", "note", "organizer"),
            "weather" to listOf("forecast", "temperature", "rain", "climate"),
            "calendar" to listOf("schedule", "event", "agenda", "planner"),
            "file" to listOf("manager", "explorer", "storage", "folder"),
            "terminal" to listOf("shell", "ssh", "command", "console", "emulator"),
            "ssh" to listOf("terminal", "shell", "remote", "server"),
            "keyboard" to listOf("input", "typing", "ime"),
            "launcher" to listOf("home", "desktop", "start"),
            "sync" to listOf("backup", "cloud", "synchronize", "syncthing"),
            "backup" to listOf("sync", "restore", "cloud", "export"),
            "translate" to listOf("dictionary", "language", "translator"),
            "dictionary" to listOf("translate", "language", "word", "wiktionary"),
            "draw" to listOf("paint", "sketch", "image", "art"),
            "edit" to listOf("editor", "text", "code", "ide", "write"),
            "code" to listOf("editor", "ide", "git", "developer", "programming"),
            "git" to listOf("code", "version", "developer", "repository"),
            "fitness" to listOf("health", "exercise", "workout", "track", "sport"),
            "health" to listOf("fitness", "medical", "track", "wellness"),
            "crypto" to listOf("bitcoin", "wallet", "monero", "currency"),
            "bitcoin" to listOf("crypto", "wallet", "currency"),
            "wallet" to listOf("bitcoin", "crypto", "payment", "money"),
            "offline" to listOf("local", "download", "cache"),
            "kid" to listOf("child", "education", "learn", "game"),
            "learn" to listOf("education", "study", "flashcard", "kid"),
            "radio" to listOf("fm", "stream", "audio", "music"),
            "call" to listOf("phone", "dialer", "sip", "voip"),
            "sms" to listOf("message", "text", "messaging"),
        )
    }
}
