package org.communitypoke.fdroidai.userscript

/**
 * Thrown by [UserscriptParser.parse] with all validation problems found.
 * [partialSource] carries the rejected source so callers (e.g. the editor)
 * can still show it for fixing.
 */
class UserscriptParseException(
    val errors: List<String>,
    val partialSource: String? = null,
) : Exception(errors.joinToString("; "))

/**
 * Parser + validator for the `==UserScript==` metadata block.
 *
 * Supported keys: `@name`, `@namespace`, `@version`, `@description`,
 * `@match`, `@include`, `@exclude`, `@run-at`, `@grant`, `@noframes`,
 * `@require`.
 *
 * Validation is deliberately strict where security matters: remote `@require`
 * URLs are rejected (the runtime never fetches remote code), every `@match`
 * must be a well-formed pattern, and at least one `@match`/`@include` is
 * required so a script can't accidentally run everywhere.
 */
object UserscriptParser {

    private const val BLOCK_OPEN = "==UserScript=="
    private const val BLOCK_CLOSE = "==/UserScript=="

    private val DIRECTIVE = Regex("^//\\s*@(\\S+)\\s*(.*)$")

    private val KNOWN_KEYS = setOf(
        "name", "namespace", "version", "description",
        "match", "include", "exclude", "exclude-match",
        "run-at", "grant", "noframes", "require", "icon", "author", "license",
        "homepage", "updateURL", "downloadURL",
    )

    data class Parsed(
        val metadata: UserscriptMetadata,
        val source: String,
    )

    fun parse(source: String): Parsed {
        val errors = mutableListOf<String>()
        val block = extractBlock(source)
        if (block == null) {
            throw UserscriptParseException(
                listOf("Missing ==UserScript== metadata block"),
            )
        }

        var name = ""
        var namespace = ""
        var version = ""
        var description = ""
        val matches = mutableListOf<String>()
        val includes = mutableListOf<String>()
        val excludes = mutableListOf<String>()
        var runAt = RunAt.DOCUMENT_END
        val grants = mutableSetOf<String>()
        var noframes = false

        for (line in block) {
            val m = DIRECTIVE.find(line.trim()) ?: continue
            val key = m.groupValues[1].lowercase()
            val value = m.groupValues[2].trim()
            when (key) {
                "name" -> name = value
                "namespace" -> namespace = value
                "version" -> version = value
                "description" -> description = value
                "match" -> {
                    if (value.isEmpty() || !MatchPattern.isValidMatchPattern(value)) {
                        errors += "Invalid @match pattern: \"$value\""
                    } else {
                        matches += value
                    }
                }
                "include" -> if (value.isNotEmpty()) includes += value
                "exclude" -> if (value.isNotEmpty()) excludes += value
                "exclude-match" -> {
                    if (value.isEmpty() || !MatchPattern.isValidMatchPattern(value)) {
                        errors += "Invalid @exclude-match pattern: \"$value\""
                    } else {
                        // Convert match pattern to an equivalent glob for exclusion.
                        excludes += value.replace("://", "*://").let { "*$it*" }
                    }
                }
                "run-at" -> {
                    val parsed = RunAt.fromMetadata(value)
                    if (parsed == null) {
                        errors += "Invalid @run-at value: \"$value\""
                    } else {
                        runAt = parsed
                    }
                }
                "grant" -> if (value.isNotEmpty() && value != "none") grants += value
                "noframes" -> noframes = true
                "require" -> {
                    if (value.startsWith("http://") || value.startsWith("https://")) {
                        errors += "Remote @require is not supported: \"$value\" " +
                            "(inline the dependency in the script body)"
                    }
                    // Local require paths are ignored — the runtime inlines nothing.
                }
                else -> {
                    if (key !in KNOWN_KEYS) {
                        // Unknown keys are tolerated silently (e.g. @copyright).
                    }
                }
            }
        }

        if (name.isBlank()) errors += "Missing required @name"
        if (matches.isEmpty() && includes.isEmpty()) {
            errors += "At least one @match or @include is required"
        }

        if (errors.isNotEmpty()) throw UserscriptParseException(errors)

        return Parsed(
            metadata = UserscriptMetadata(
                name = name,
                namespace = namespace,
                version = version,
                description = description,
                matches = matches,
                includes = includes,
                excludes = excludes,
                runAt = runAt,
                grants = grants,
                noframes = noframes,
            ),
            source = source,
        )
    }

    /** Lines between `==UserScript==` and `==/UserScript==`, or null. */
    private fun extractBlock(source: String): List<String>? {
        val lines = source.lines()
        val start = lines.indexOfFirst { BLOCK_OPEN in it }
        if (start < 0) return null
        val endOffset = lines.subList(start + 1, lines.size)
            .indexOfFirst { BLOCK_CLOSE in it }
        if (endOffset < 0) return null
        return lines.subList(start + 1, start + 1 + endOffset)
    }

    /**
     * Stable script id from metadata: `namespace/name` slugified, falling back
     * to the name alone. Used for file names and GM storage scoping.
     */
    fun computeId(metadata: UserscriptMetadata): String {
        val base = listOf(metadata.namespace, metadata.name)
            .filter { it.isNotBlank() }
            .joinToString("/")
            .ifBlank { "unnamed" }
        return base.lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(80)
            .ifBlank { "unnamed" }
    }
}
