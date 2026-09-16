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
 * Codex SDK backend over the OpenAI Responses API (`POST {base}/v1/responses`),
 * the same wire format the Codex CLI/SDK drives. Configured through `BuildConfig`:
 *
 *   - `CODEX_API_KEY`  — bearer token; when blank [isConfigured] is false.
 *   - `CODEX_BASE_URL` — e.g. `https://api.openai.com/v1`
 *   - `CODEX_MODEL`    — e.g. `gpt-5-codex`
 *
 * `store=false` is always sent so runs are stateless; the conversation is
 * carried entirely in the `input` list, matching the Codex SDK's local agent
 * loop. `function_call` output items are executed locally and returned as
 * `function_call_output` items until the model emits no further calls or
 * [AgentRequest.maxTurns] is hit.
 */
class CodexSdkProvider(
    private val apiKey: String,
    private val baseUrl: String,
    private val model: String,
    private val json: Json,
    private val okHttpClient: OkHttpClient,
) : AgentSdkProvider {

    override val sdk = AgentSdk.CODEX
    override val name = "Codex SDK ($model)"
    override val isConfigured = apiKey.isNotBlank()

    override suspend fun run(request: AgentRequest): AgentResult {
        check(isConfigured) { "Codex SDK is not configured (missing CODEX_API_KEY)" }
        require(request.maxTurns >= 1) { "maxTurns must be >= 1" }

        val input = mutableListOf(userInput(request.prompt))
        val toolCalls = mutableListOf<ToolCallRecord>()
        var turns = 0
        var stopReason: String? = null

        while (turns < request.maxTurns) {
            turns++
            val body = buildRequestBody(request, input)
            val response = post(body)
            stopReason = response["status"]?.jsonPrimitive?.content
            val output = response["output"]?.jsonArray ?: JsonArray(emptyList())

            // Carry the model's output items forward as conversation state.
            output.forEach { input += it.jsonObject }

            val functionCalls = output.filter {
                it.jsonObject["type"]?.jsonPrimitive?.content == "function_call"
            }
            if (functionCalls.isEmpty() || request.tools.isEmpty()) {
                return AgentResult(
                    text = extractText(output),
                    providerName = name,
                    sdk = sdk,
                    turns = turns,
                    toolCalls = toolCalls,
                    stopReason = stopReason,
                )
            }

            for (call in functionCalls) {
                val obj = call.jsonObject
                val toolName = obj["name"]?.jsonPrimitive?.content ?: continue
                val args = obj["arguments"]?.jsonPrimitive?.content.orEmpty()
                val record = executeToolCall(
                    request.tools,
                    toolName,
                    args.toJsonObjectOrEmpty(),
                )
                toolCalls += record
                input += buildJsonObject {
                    put("type", "function_call_output")
                    put("call_id", obj["call_id"]?.jsonPrimitive?.content ?: "")
                    put("output", record.output)
                }
            }
        }
        throw IOException("Codex SDK hit maxTurns=${request.maxTurns} without a final answer")
    }

    private fun buildRequestBody(request: AgentRequest, input: List<JsonObject>): JsonObject =
        buildJsonObject {
            put("model", model)
            put("store", false)
            request.systemPrompt?.let { put("instructions", it) }
            request.temperature?.let { put("temperature", it) }
            if (request.tools.isNotEmpty()) {
                put("tools", buildJsonArray {
                    for (tool in request.tools) {
                        add(buildJsonObject {
                            put("type", "function")
                            put("name", tool.name)
                            put("description", tool.description)
                            put("parameters", tool.inputSchema)
                        })
                    }
                })
            }
            put("input", JsonArray(input))
        }

    private fun userInput(text: String): JsonObject = buildJsonObject {
        put("type", "message")
        put("role", "user")
        put("content", buildJsonArray {
            add(buildJsonObject {
                put("type", "input_text")
                put("text", text)
            })
        })
    }

    private fun String.toJsonObjectOrEmpty(): JsonObject =
        try {
            json.parseToJsonElement(this).jsonObject
        } catch (e: Exception) {
            buildJsonObject { put("_raw", this@toJsonObjectOrEmpty) }
        }

    private fun extractText(output: JsonArray): String =
        output.filter { it.jsonObject["type"]?.jsonPrimitive?.content == "message" }
            .flatMap { it.jsonObject["content"]?.jsonArray.orEmpty() }
            .filter { it.jsonObject["type"]?.jsonPrimitive?.content == "output_text" }
            .joinToString("\n") { it.jsonObject["text"]?.jsonPrimitive?.content.orEmpty() }

    private suspend fun post(body: JsonObject): JsonObject = withContext(Dispatchers.IO) {
        val httpRequest = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/responses")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(json.encodeToString(JsonObject.serializer(), body).toRequestBody(JSON))
            .build()
        okHttpClient.newCall(httpRequest).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("Codex API HTTP ${response.code}: ${text.take(300)}")
            }
            json.parseToJsonElement(text).jsonObject
        }
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
