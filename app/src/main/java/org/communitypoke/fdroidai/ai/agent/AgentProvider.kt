package org.communitypoke.fdroidai.ai.agent

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Which agent SDK a provider speaks. */
enum class AgentSdk {
    /** Claude Code SDK — Anthropic Messages API (`/v1/messages`). */
    CLAUDE_CODE,

    /** OpenAI Codex SDK — Responses API (`/v1/responses`). */
    CODEX,

    /** Local fallback used when no SDK credentials are configured. */
    MOCK,
}

/**
 * A tool the agent may call during a run. [handler] executes locally on-device
 * and its return value is fed back to the model as the tool result.
 */
data class AgentTool(
    val name: String,
    val description: String,
    /** JSON Schema describing the tool input (usually `{"type":"object",...}`). */
    val inputSchema: JsonObject = buildJsonObject { put("type", "object") },
    val handler: suspend (JsonObject) -> String,
)

data class AgentRequest(
    val prompt: String,
    /** Optional system prompt / instructions for the whole run. */
    val systemPrompt: String? = null,
    /** Tools the model may call; enables the multi-turn agent loop. */
    val tools: List<AgentTool> = emptyList(),
    /** Maximum model calls (turns) before the run is cut short. */
    val maxTurns: Int = 8,
    val maxTokens: Int = 4096,
    val temperature: Double? = null,
)

/** Record of a single tool invocation, for transparency/debugging in the UI. */
data class ToolCallRecord(
    val toolName: String,
    val input: JsonObject,
    val output: String,
    val isError: Boolean = false,
)

data class AgentResult(
    /** Concatenated final text output of the agent. */
    val text: String,
    val providerName: String,
    val sdk: AgentSdk,
    /** Number of model calls performed. */
    val turns: Int,
    val toolCalls: List<ToolCallRecord> = emptyList(),
    /** Provider-reported stop reason, when available. */
    val stopReason: String? = null,
)

/**
 * Abstraction over agent SDK backends (Claude Code SDK, Codex SDK).
 *
 * Implementations run a model call and — when [AgentRequest.tools] is
 * non-empty — drive the multi-turn tool-call loop locally: each tool call the
 * model emits is executed on-device and its result is sent back, until the
 * model produces a final text answer or [AgentRequest.maxTurns] is reached.
 */
interface AgentSdkProvider {
    val sdk: AgentSdk

    /** Human-readable provider name for UI badges, e.g. "Claude Code SDK (sonnet)". */
    val name: String

    /** False when required configuration (e.g. an API key) is missing. */
    val isConfigured: Boolean

    /**
     * Run the agent. Throws when the provider is not configured or the remote
     * call fails; callers that want resilience should fall back to another
     * provider from [AgentProviderRegistry].
     */
    suspend fun run(request: AgentRequest): AgentResult
}

/** Execute the named tool, catching handler errors into a result record. */
internal suspend fun executeToolCall(
    tools: List<AgentTool>,
    toolName: String,
    input: JsonObject,
): ToolCallRecord {
    val tool = tools.firstOrNull { it.name == toolName }
    if (tool == null) {
        return ToolCallRecord(
            toolName = toolName,
            input = input,
            output = "Error: unknown tool \"$toolName\"",
            isError = true,
        )
    }
    return try {
        ToolCallRecord(toolName = toolName, input = input, output = tool.handler(input))
    } catch (e: Exception) {
        ToolCallRecord(
            toolName = toolName,
            input = input,
            output = "Error: ${e.message ?: e.javaClass.simpleName}",
            isError = true,
        )
    }
}
