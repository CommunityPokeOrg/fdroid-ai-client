package org.communitypoke.fdroidai.data.search

import org.communitypoke.fdroidai.data.model.FdroidApp

/**
 * Fast local keyword search used for the regular (non-AI) search mode and as
 * the candidate pre-filter for remote AI ranking.
 */
object SearchEngine {

    fun search(query: String, apps: List<FdroidApp>, limit: Int = 60): List<FdroidApp> {
        val terms = tokenize(query)
        if (terms.isEmpty()) return emptyList()
        return apps.asSequence()
            .map { app -> app to score(terms, app) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
            .toList()
    }

    fun tokenize(query: String): List<String> =
        query.trim().lowercase().split(Regex("[^a-z0-9.+#-]+"))
            .filter { it.length >= 2 }

    internal fun score(terms: List<String>, app: FdroidApp): Double {
        var total = 0.0
        val name = app.name.lowercase()
        val pkg = app.packageName.lowercase()
        val summary = app.summary.lowercase()
        val description = app.description.lowercase()
        val categories = app.categories.map { it.lowercase() }

        for (term in terms) {
            var s = 0.0
            when {
                name == term -> s += 40.0
                name.startsWith(term) -> s += 25.0
                name.contains(term) -> s += 15.0
            }
            if (pkg.contains(term)) s += 10.0
            if (summary.contains(term)) s += 8.0
            if (categories.any { it.contains(term) }) s += 6.0
            if (description.contains(term)) s += 3.0
            total += s
        }
        return total
    }
}
