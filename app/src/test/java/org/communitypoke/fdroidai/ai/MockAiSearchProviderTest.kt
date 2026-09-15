package org.communitypoke.fdroidai.ai

import kotlinx.coroutines.test.runTest
import org.communitypoke.fdroidai.testCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MockAiSearchProviderTest {

    private val provider = MockAiSearchProvider()
    private val catalog = testCatalog()

    @Test
    fun `semantic concept matches rank relevant app first`() = runTest {
        // "vpn" should expand toward privacy/security concepts.
        val response = provider.search(
            AiSearchRequest(query = "keep my passwords safe", apps = catalog),
        )
        assertTrue(response.results.isNotEmpty())
        assertEquals("org.example.passwordvault", response.results.first().app.packageName)
        assertTrue(response.results.first().reason.isNotBlank())
    }

    @Test
    fun `navigation concept finds maps app`() = runTest {
        val response = provider.search(
            AiSearchRequest(query = "offline maps for hiking", apps = catalog),
        )
        assertEquals("org.example.navigator", response.results.first().app.packageName)
        assertTrue(response.summary.contains("map", ignoreCase = true) ||
            response.summary.contains("navigation", ignoreCase = true))
    }

    @Test
    fun `results are limited and scored`() = runTest {
        val response = provider.search(
            AiSearchRequest(query = "audio", apps = catalog, limit = 2),
        )
        assertTrue(response.results.size <= 2)
        val scores = response.results.map { it.score }
        assertEquals(scores.sortedDescending(), scores)
    }

    @Test
    fun `gibberish query produces empty results with guidance`() = runTest {
        val response = provider.search(
            AiSearchRequest(query = "zzzqqq nonsense", apps = catalog),
        )
        assertTrue(response.results.isEmpty())
        assertTrue(response.summary.contains("No apps matched"))
        assertFalse(response.isFallback)
    }
}
