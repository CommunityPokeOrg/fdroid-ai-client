package org.communitypoke.fdroidai.userscript

/**
 * Extracts a userscript source from free-form model output. Handles the
 * common cases: a fenced code block, a bare script starting with the
 * `==UserScript==` block, or prose with a script embedded inside.
 */
object ScriptSourceExtractor {

    private val FENCE = Regex("```(?:javascript|js|userscript|ecmascript)?\\s*\\n(.*?)```", RegexOption.DOT_MATCHES_ALL)

    fun extract(output: String): String {
        val trimmed = output.trim()
        if (trimmed.isEmpty()) return trimmed

        // Prefer an explicit fenced block — models often wrap code even when
        // asked not to.
        val fenced = FENCE.find(trimmed)?.groupValues?.get(1)?.trim()
        if (fenced != null && "==UserScript==" in fenced) return fenced

        // Otherwise slice from the start of the metadata block; if the script
        // is followed by trailing prose/fences the parser validates anyway.
        val marker = trimmed.indexOf("==UserScript==")
        if (marker >= 0) {
            var slice = trimmed.substring(marker)
            // Rewind to the start of the comment line containing the marker.
            val commentStart = slice.indexOf("//").takeIf { it in 0..4 }
            if (commentStart != null) slice = slice.substring(commentStart)
            return slice.trimEnd('`', ' ', '\n', '\t').trim()
        }
        return fenced ?: trimmed
    }
}
