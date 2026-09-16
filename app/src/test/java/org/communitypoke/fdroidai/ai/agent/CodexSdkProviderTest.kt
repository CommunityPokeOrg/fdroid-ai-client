package org.communitypoke.fdroidai.ai.agent

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CodexSdkProviderTest {

    private lateinit var server: MockWebServer
    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var provider: CodexSdkProvider

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        provider = CodexSdkProvider(
            apiKey = "codex-key",
            baseUrl = server.url("/").toString().trimEnd('/') + "/v1",
            model = "codex-test",
            json = json,
            okHttpClient = OkHttpClient(),
        )
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `message output is returned with bearer auth and store=false`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"id":"r1","status":"completed","output":[{"type":"message",
                   "role":"assistant","content":[{"type":"output_text","text":"hi!"}]}]}""",
            ),
        )
        val result = provider.run(AgentRequest(prompt = "hello", systemPrompt = "sys"))
        assertEquals("hi!", result.text)
        assertEquals(1, result.turns)

        val req = server.takeRequest()
        assertEquals("/v1/responses", req.path)
        assertEquals("Bearer codex-key", req.getHeader("Authorization"))
        val body = json.parseToJsonElement(req.body.readUtf8()).jsonObject
        assertEquals("codex-test", body["model"]!!.jsonPrimitive.content)
        assertEquals(false, body["store"]!!.jsonPrimitive.content.toBoolean())
        assertEquals("sys", body["instructions"]!!.jsonPrimitive.content)
        assertEquals(
            "message",
            body["input"]!!.jsonArray[0].jsonObject["type"]!!.jsonPrimitive.content,
        )
    }

    @Test
    fun `function_call drives a second turn with function_call_output`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"id":"r1","status":"completed","output":[{"type":"function_call",
                   "id":"fc_1","call_id":"call_1","name":"echo",
                   "arguments":"{\"msg\":\"ping\"}"}]}""",
            ),
        )
        server.enqueue(
            MockResponse().setBody(
                """{"id":"r2","status":"completed","output":[{"type":"message",
                   "role":"assistant","content":[{"type":"output_text","text":"done"}]}]}""",
            ),
        )
        val tool = AgentTool(name = "echo", description = "echoes") { input ->
            "echo:" + input["msg"]!!.jsonPrimitive.content
        }
        val result = provider.run(AgentRequest(prompt = "go", tools = listOf(tool)))
        assertEquals("done", result.text)
        assertEquals(2, result.turns)
        assertEquals("echo:ping", result.toolCalls[0].output)

        server.takeRequest()
        val second = json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        val input = second["input"]!!.jsonArray
        // original message + function_call item + function_call_output item
        val last = input.last().jsonObject
        assertEquals("function_call_output", last["type"]!!.jsonPrimitive.content)
        assertEquals("call_1", last["call_id"]!!.jsonPrimitive.content)
        assertEquals("echo:ping", last["output"]!!.jsonPrimitive.content)
        assertEquals(
            "function",
            second["tools"]!!.jsonArray[0].jsonObject["type"]!!.jsonPrimitive.content,
        )
    }

    @Test
    fun `http error surfaces as IOException`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"bad key"}"""))
        val e = assertThrows(java.io.IOException::class.java) {
            kotlinx.coroutines.runBlocking { provider.run(AgentRequest(prompt = "go")) }
        }
        assertTrue(e.message!!.contains("401"))
    }

    @Test
    fun `unconfigured provider refuses to run`() {
        val unconfigured = CodexSdkProvider(
            apiKey = "",
            baseUrl = "https://api.openai.com/v1",
            model = "m",
            json = json,
            okHttpClient = OkHttpClient(),
        )
        assertFalse(unconfigured.isConfigured)
        assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking { unconfigured.run(AgentRequest(prompt = "x")) }
        }
    }
}
