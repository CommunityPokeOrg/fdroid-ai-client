package org.communitypoke.fdroidai.userscript

import kotlinx.coroutines.test.runTest
import org.communitypoke.fdroidai.ai.agent.AgentProviderRegistry
import org.communitypoke.fdroidai.ai.agent.AgentRequest
import org.communitypoke.fdroidai.ai.agent.AgentResult
import org.communitypoke.fdroidai.ai.agent.AgentSdk
import org.communitypoke.fdroidai.ai.agent.AgentSdkProvider
import org.communitypoke.fdroidai.ai.agent.MockAgentProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class UserscriptBuilderTest {

    private fun fakeProvider(output: String) = object : AgentSdkProvider {
        override val sdk = AgentSdk.CLAUDE_CODE
        override val name = "fake"
        override val isConfigured = true
        override suspend fun run(request: AgentRequest): AgentResult =
            AgentResult(text = output, providerName = name, sdk = sdk, turns = 1)
    }

    private val validOutput = """
        |```javascript
        |// ==UserScript==
        |// @name        Night Mode
        |// @namespace   test
        |// @version     1.0
        |// @match       https://*.example.com/*
        |// @grant       none
        |// ==/UserScript==
        |document.body.style.filter = 'invert(1)';
        |```
    """.trimMargin()

    @Test
    fun `build returns parsed draft from fenced output`() = runTest {
        val builder = UserscriptBuilder(AgentProviderRegistry(listOf(fakeProvider(validOutput))))
        val draft = builder.build("make example.com dark")
        assertEquals("Night Mode", draft.metadata.name)
        assertEquals("fake", draft.providerName)
        assertFalse(draft.isDemo)
        assertTrue(draft.source.contains("==UserScript=="))
    }

    @Test
    fun `invalid model output throws with partial source attached`() = runTest {
        val builder = UserscriptBuilder(AgentProviderRegistry(listOf(fakeProvider("no script here"))))
        val e = assertThrows(UserscriptParseException::class.java) {
            kotlinx.coroutines.runBlocking { builder.build("x") }
        }
        assertEquals("no script here", e.partialSource)
    }

    @Test
    fun `mock provider produces a valid installable script`() = runTest {
        val registry = AgentProviderRegistry(listOf(MockAgentProvider()))
        val builder = UserscriptBuilder(registry)
        val draft = builder.build("dark theme everywhere")
        assertTrue(draft.isDemo)
        assertTrue(draft.source.contains("==UserScript=="))
        assertTrue(draft.source.contains("@match"))
    }

    @Test
    fun `toUserscript validates and derives id`() = runTest {
        val builder = UserscriptBuilder(AgentProviderRegistry(listOf(MockAgentProvider())))
        val draft = builder.build("hide ads")
        val script = builder.toUserscript(draft.source)
        assertTrue(script.id.isNotBlank())
        assertTrue(script.enabled)
        assertEquals(draft.metadata.name, script.name)
    }
}
