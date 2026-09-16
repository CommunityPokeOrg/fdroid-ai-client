package org.communitypoke.fdroidai.ai.agent

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Claude Code SDK backend over the Anthropic Messages API (`POST {base}/v1/messages`).
 * Configured through `BuildConfig`:
 *
 *   - `CLAUDE_API_KEY`  — `x-api-key` credential; when blank [isConfigured] is false.
 *   - `CLAUDE_BASE_URL` — e.g. `https://api.anthropic.com`
 *   - `CLAUDE_MODEL`    — e.g. `claude-sonnet-4-20250514`
 *
 * When [AgentRequest.tools] is non-empty the provider runs the agentic loop the
 * Claude Code SDK performs client-side: `tool_use` blocks are executed locally
 * and returned as `tool_result` user messages until `stop_reason` is no longer
 * `"tool_use"` or [AgentRequest.maxTurns] is hit.
 */
class ClaudeCodeSdkProvider(
    private val apiKey: String,
    private val baseUrl: String,
    private val model: String,
    private val json: Json,
    private val okHttpClient: OkHttpClient,
    private val anthropicVersion: String = DEFAULT_VERSION,
) : AgentSdkProvider {

    override val sdk = AgentSdk.CLAUDE_CODE
    override val name = "Claude Code SDK ($model)"
    override val isConfigured = apiKey.isNotBlank()

    override suspend fun run(request: AgentRequest): AgentResult {
        check(isConfigured) { "Claude Code SDK is not configured (missing CLAUDE_API_KEY)" }
        require(request.maxTurns >= 1) { "maxTurns must be >= 1" }

        val messages = mutableListOf(userMessage(request.prompt))
        val toolCalls = mutableListOf<ToolCallRecord>()
        var turns = 0
        var stopReason: String? = null

        while (turns < request.maxTurns) {
            turns++
            val body = buildRequestBody(request, messages)
            val response = post(body)
            stopReason = response["stop_reason"]?.jsonPrimitive?.content
            val content = response["content"]?.jsonArray ?: JsonArray(emptyList())

            val toolUseBlocks = content.filter {
                it.jsonObject["type"]?.jsonPrimitive?.content == "tool_use"
            }
            if (toolUseBlocks.isEmpty() || request.tools.isEmpty()) {
                return AgentResult(
                    text = extractText(content),
                    providerName = name,
                    sdk = sdk,
                    turns = turns,
                    toolCalls = toolCalls,
                    stopReason = stopReason,
                )
            }

            // Keep the assistant turn verbatim so tool_use ids stay intact.
            messages += buildJsonObject {
                put("role", "assistant")
                put("content", content)
            }
            val results = buildJsonArray {
                for (block in toolUseBlocks) {
                    val obj = block.jsonObject
                    val toolName = obj["name"]?.jsonPrimitive?.content ?: continue
                    val input = obj["input"]?.jsonObject ?: buildJsonObject {}
                    val record = executeToolCall(request.tools, toolName, input)
                    toolCalls += record
                    add(buildJsonObject {
                        put("type", "tool_result")
                        put("tool_use_id", obj["id"]?.jsonPrimitive?.content ?: "")
                        put("content", record.output)
                        if (record.isError) put("is_error", true)
                    })
                }
            }
            messages += buildJsonObject {
                put("role", "user")
                put("content", results)
            }
        }
        throw IOException("Claude Code SDK hit maxTurns=${request.maxTurns} without a final answer")
    }

    private fun buildRequestBody(request: AgentRequest, messages: List<JsonObject>): JsonObject =
        buildJsonObject {
            put("model", model)
            put("max_tokens", request.maxTokens)
            request.systemPrompt?.let { put("system", it) }
            request.temperature?.let { put("temperature", it) }
            if (request.tools.isNotEmpty()) {
                put("tools", buildJsonArray {
                    for (tool in request.tools) {
                        add(buildJsonObject {
                            put("name", tool.name)
                            put("description", tool.description)
                            put("input_schema", tool.inputSchema)
                        })
                    }
                })
            }
            put("messages", JsonArray(messages))
        }

    private fun userMessage(text: String): JsonObject = buildJsonObject {
        put("role", "user")
        put("content", buildJsonArray {
            add(buildJsonObject {
                put("type", "text")
                put("text", text)
            })
        })
    }

    private fun extractText(content: JsonArray): String =
        content.filter { it.jsonObject["type"]?.jsonPrimitive?.content == "text" }
            .joinToString("\n") { it.jsonObject["text"]?.jsonPrimitive?.content.orEmpty() }

    private suspend fun post(body: JsonObject): JsonObject = withContext(Dispatchers.IO) {
        val httpRequest = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/v1/messages")
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", anthropicVersion)
            .addHeader("Content-Type", "application/json")
            .post(json.encodeToString(JsonObject.serializer(), body).toRequestBody(JSON))
            .build()
        okHttpClient.newCall(httpRequest).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("Claude API HTTP ${response.code}: ${text.take(300)}")
            }
            json.parseToJsonElement(text).jsonObject
        }
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
        const val DEFAULT_VERSION = "2023-06-01"
    }
}
