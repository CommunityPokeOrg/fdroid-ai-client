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

class ClaudeCodeSdkProviderTest {

    private lateinit var server: MockWebServer
    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var provider: ClaudeCodeSdkProvider

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        provider = ClaudeCodeSdkProvider(
            apiKey = "test-key",
            baseUrl = server.url("/").toString().trimEnd('/'),
            model = "claude-test",
            json = json,
            okHttpClient = OkHttpClient(),
        )
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `text response is returned with correct headers`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"id":"msg_1","role":"assistant","stop_reason":"end_turn",
                   "content":[{"type":"text","text":"hello there"}]}""",
            ),
        )
        val result = provider.run(AgentRequest(prompt = "hi", systemPrompt = "be nice"))
        assertEquals("hello there", result.text)
        assertEquals(1, result.turns)
        assertEquals("end_turn", result.stopReason)

        val req = server.takeRequest()
        assertEquals("/v1/messages", req.path)
        assertEquals("test-key", req.getHeader("x-api-key"))
        assertEquals("2023-06-01", req.getHeader("anthropic-version"))
        val body = json.parseToJsonElement(req.body.readUtf8()).jsonObject
        assertEquals("claude-test", body["model"]!!.jsonPrimitive.content)
        assertEquals("be nice", body["system"]!!.jsonPrimitive.content)
        assertEquals("user", body["messages"]!!.jsonArray[0].jsonObject["role"]!!.jsonPrimitive.content)
    }

    @Test
    fun `tool_use drives a second turn with tool_result`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"id":"msg_1","role":"assistant","stop_reason":"tool_use",
                   "content":[{"type":"tool_use","id":"tu_1","name":"echo",
                               "input":{"msg":"ping"}}]}""",
            ),
        )
        server.enqueue(
            MockResponse().setBody(
                """{"id":"msg_2","role":"assistant","stop_reason":"end_turn",
                   "content":[{"type":"text","text":"pong done"}]}""",
            ),
        )
        val tool = AgentTool(name = "echo", description = "echoes") { input ->
            "echo:" + input["msg"]!!.jsonPrimitive.content
        }
        val result = provider.run(AgentRequest(prompt = "go", tools = listOf(tool)))
        assertEquals("pong done", result.text)
        assertEquals(2, result.turns)
        assertEquals(1, result.toolCalls.size)
        assertEquals("echo:ping", result.toolCalls[0].output)

        server.takeRequest() // first call
        val second = json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        val messages = second["messages"]!!.jsonArray
        // user, assistant(tool_use), user(tool_result)
        assertEquals(3, messages.size)
        val toolResult = messages[2].jsonObject["content"]!!.jsonArray[0].jsonObject
        assertEquals("tool_result", toolResult["type"]!!.jsonPrimitive.content)
        assertEquals("tu_1", toolResult["tool_use_id"]!!.jsonPrimitive.content)
        assertEquals("echo:ping", toolResult["content"]!!.jsonPrimitive.content)
        // tools were advertised
        val tools = second["tools"]!!.jsonArray
        assertEquals("echo", tools[0].jsonObject["name"]!!.jsonPrimitive.content)
    }

    @Test
    fun `maxTurns=1 with tool_use returns partial text or throws`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"id":"msg_1","role":"assistant","stop_reason":"tool_use",
                   "content":[{"type":"tool_use","id":"tu_1","name":"x","input":{}}]}""",
            ),
        )
        val tool = AgentTool(name = "x", description = "x") { "ok" }
        // With maxTurns=1 the loop ends without a final answer -> IOException
        assertThrows(java.io.IOException::class.java) {
            kotlinx.coroutines.runBlocking {
                provider.run(AgentRequest(prompt = "go", tools = listOf(tool), maxTurns = 1))
            }
        }
    }

    @Test
    fun `http error surfaces as IOException`() = runTest {
        server.enqueue(MockResponse().setResponseCode(429).setBody("""{"error":"rate limited"}"""))
        val e = assertThrows(java.io.IOException::class.java) {
            kotlinx.coroutines.runBlocking { provider.run(AgentRequest(prompt = "go")) }
        }
        assertTrue(e.message!!.contains("429"))
    }

    @Test
    fun `unconfigured provider refuses to run`() {
        val unconfigured = ClaudeCodeSdkProvider(
            apiKey = "",
            baseUrl = "https://api.anthropic.com",
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
