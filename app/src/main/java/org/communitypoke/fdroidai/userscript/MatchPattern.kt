package org.communitypoke.fdroidai.userscript

/**
 * URL matching for userscripts, following the Greasemonkey/Tampermonkey rules:
 *
 * - `@match` uses `scheme://host/path` patterns where scheme is `http`,
 *   `https` or `*`, host is `*`, `*.example.com` or an exact host, and the
 *   path is any string where `*` matches any characters.
 * - `@include`/`@exclude` use plain globs where `*` matches any characters.
 *
 * Only `http` and `https` page URLs ever match — `file:`, `data:` and other
 * schemes are never script targets.
 */
object MatchPattern {

    private val MATCH_PATTERN = Regex("^(\\*|http|https)://(\\*|\\*\\.[^/]+|[^/*]+)(/.*)$")

    private val ALLOWED_SCHEMES = setOf("http", "https")

    fun isValidMatchPattern(pattern: String): Boolean =
        MATCH_PATTERN.matches(pattern.trim())

    /** Does [url] match a single `@match` [pattern]? */
    fun matchesMatchPattern(pattern: String, url: String): Boolean {
        val m = MATCH_PATTERN.find(pattern.trim()) ?: return false
        val (patScheme, patHost, patPath) = m.destructured

        val schemeEnd = url.indexOf("://")
        if (schemeEnd <= 0) return false
        val scheme = url.substring(0, schemeEnd).lowercase()
        if (scheme !in ALLOWED_SCHEMES) return false
        if (patScheme != "*" && patScheme != scheme) return false

        val rest = url.substring(schemeEnd + 3)
        val pathStart = rest.indexOf('/')
        val host = (if (pathStart < 0) rest else rest.substring(0, pathStart))
            .substringBefore(':') // strip port
            .lowercase()
        val path = if (pathStart < 0) "/" else rest.substring(pathStart)

        val hostMatches = when {
            patHost == "*" -> host.isNotEmpty()
            patHost.startsWith("*.") -> {
                val suffix = patHost.substring(1) // ".example.com"
                host.endsWith(suffix) && host.length > suffix.length || host == patHost.substring(2)
            }
            else -> host == patHost.lowercase()
        }
        if (!hostMatches) return false

        return globToRegex(patPath).matches(path)
    }

    /** Does [url] match an `@include`/`@exclude` glob? */
    fun matchesGlob(glob: String, url: String): Boolean =
        globToRegex(glob.trim()).matches(url)

    /** Full-script evaluation: excludes win, then @match, then @include. */
    fun matches(metadata: UserscriptMetadata, url: String): Boolean {
        val schemeEnd = url.indexOf("://")
        if (schemeEnd <= 0 || url.substring(0, schemeEnd).lowercase() !in ALLOWED_SCHEMES) {
            return false
        }
        if (metadata.excludes.any { matchesGlob(it, url) }) return false
        return when {
            metadata.matches.isNotEmpty() ->
                metadata.matches.any { matchesMatchPattern(it, url) }
            metadata.includes.isNotEmpty() ->
                metadata.includes.any { matchesGlob(it, url) }
            else -> false
        }
    }

    /** `*` → `.*`; every other regex metachar is literal. */
    fun globToRegex(glob: String): Regex = buildString {
        append('^')
        for (c in glob) {
            if (c == '*') append(".*") else append(Regex.escape(c.toString()))
        }
        append('$')
    }.toRegex()
}
