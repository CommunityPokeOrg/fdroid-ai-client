package org.communitypoke.fdroidai.userscript

import org.communitypoke.fdroidai.ai.agent.AgentProviderRegistry
import org.communitypoke.fdroidai.ai.agent.AgentRequest
import org.communitypoke.fdroidai.ai.agent.AgentSdk
import org.communitypoke.fdroidai.ai.agent.AgentSdkProvider

/**
 * Turns a natural-language spec into a validated userscript via an agent SDK
 * provider (Claude Code SDK or Codex SDK when configured, otherwise the
 * on-device demo agent).
 */
class UserscriptBuilder(
    private val registry: AgentProviderRegistry,
) {

    data class Draft(
        /** Full userscript source, ready to save. */
        val source: String,
        val metadata: UserscriptMetadata,
        /** Provider that produced the draft. */
        val providerName: String,
        /** True when the on-device demo agent produced it. */
        val isDemo: Boolean,
    )

    /**
     * Generate a script from [prompt]. [provider] defaults to the registry's
     * preferred provider. Throws [UserscriptParseException] when the model's
     * output is not a valid script (the UI shows the error list and keeps the
     * extracted source editable).
     */
    suspend fun build(
        prompt: String,
        provider: AgentSdkProvider = registry.preferred(),
    ): Draft {
        val result = provider.run(
            AgentRequest(
                prompt = prompt,
                systemPrompt = SYSTEM_PROMPT,
                maxTurns = 1,
                maxTokens = 4096,
                temperature = 0.2,
            ),
        )
        val source = ScriptSourceExtractor.extract(result.text)
        val parsed = try {
            UserscriptParser.parse(source)
        } catch (e: UserscriptParseException) {
            throw UserscriptParseException(e.errors, partialSource = source)
        }
        return Draft(
            source = source,
            metadata = parsed.metadata,
            providerName = result.providerName,
            isDemo = result.sdk == AgentSdk.MOCK,
        )
    }

    /** Assemble a [Userscript] ready for [UserscriptStore.save]. */
    fun toUserscript(source: String, enabled: Boolean = true): Userscript {
        val parsed = UserscriptParser.parse(source)
        return Userscript(
            id = UserscriptParser.computeId(parsed.metadata),
            name = parsed.metadata.name,
            source = source,
            metadata = parsed.metadata,
            enabled = enabled,
        )
    }

    private companion object {
        const val SYSTEM_PROMPT =
            "You are a userscript generator for an Android WebView userscript " +
                "runtime (Greasemonkey-style). Reply with ONLY the complete " +
                "userscript source — no prose, no markdown fences. " +
                "Rules: the script MUST start with a ==UserScript== block " +
                "containing @name, @namespace, @version, @match (a real " +
                "scheme://host/path pattern), and @grant for every GM_* " +
                "function used (supported: GM_getValue, GM_setValue, " +
                "GM_deleteValue, GM_listValues, GM_notification, GM_log, " +
                "GM_info; otherwise '@grant none'). " +
                "Never use remote @require — inline any helper code. " +
                "Write plain ES5-compatible JavaScript wrapped so it cannot " +
                "leak globals."
    }
}
