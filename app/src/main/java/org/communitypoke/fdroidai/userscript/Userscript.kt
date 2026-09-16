package org.communitypoke.fdroidai.userscript

/** When in the page lifecycle a userscript runs (`@run-at`). */
enum class RunAt {
    /** Injected as early as possible, before page scripts (best effort in a WebView). */
    DOCUMENT_START,

    /** Injected once the DOM is loaded. */
    DOCUMENT_END,

    /** Treated like [DOCUMENT_END] — after page load finishes. */
    DOCUMENT_IDLE;

    companion object {
        fun fromMetadata(value: String): RunAt? = when (value.lowercase()) {
            "document-start" -> DOCUMENT_START
            "document-end" -> DOCUMENT_END
            "document-idle" -> DOCUMENT_IDLE
            else -> null
        }
    }
}

/** Parsed `==UserScript==` metadata block. */
data class UserscriptMetadata(
    val name: String,
    val namespace: String = "",
    val version: String = "",
    val description: String = "",
    /** `@match` patterns in `scheme://host/path` form. */
    val matches: List<String> = emptyList(),
    /** `@include` URL globs. */
    val includes: List<String> = emptyList(),
    /** `@exclude` URL globs. */
    val excludes: List<String> = emptyList(),
    val runAt: RunAt = RunAt.DOCUMENT_END,
    /** `@grant` values, e.g. `GM_getValue`, `GM_notification`, `none`. */
    val grants: Set<String> = emptySet(),
    val noframes: Boolean = false,
)

/** An installed userscript. [source] is the source of truth for [metadata]. */
data class Userscript(
    /** Stable identifier derived from namespace + name (used for GM storage scoping). */
    val id: String,
    val name: String,
    val source: String,
    val metadata: UserscriptMetadata,
    val enabled: Boolean = true,
    val installedAt: Long = 0L,
    val updatedAt: Long = 0L,
) {
    /** True when this script should run on [url] (respects excludes + noframes). */
    fun matchesUrl(url: String, isMainFrame: Boolean = true): Boolean {
        if (metadata.noframes && !isMainFrame) return false
        return MatchPattern.matches(metadata, url)
    }
}
