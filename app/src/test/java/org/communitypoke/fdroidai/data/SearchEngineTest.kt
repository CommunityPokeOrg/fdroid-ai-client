package org.communitypoke.fdroidai.data

import org.communitypoke.fdroidai.data.search.SearchEngine
import org.communitypoke.fdroidai.testApp
import org.communitypoke.fdroidai.testCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchEngineTest {

    private val catalog = testCatalog()

    @Test
    fun `empty query returns no results`() {
        assertTrue(SearchEngine.search("", catalog).isEmpty())
        assertTrue(SearchEngine.search("   ", catalog).isEmpty())
    }

    @Test
    fun `name match outranks description match`() {
        val apps = listOf(
            testApp("a.b.desc", "Some App", description = "Great password helper inside"),
            testApp("a.b.name", "Password Tool"),
        )
        val results = SearchEngine.search("password", apps)
        assertEquals("a.b.name", results.first().packageName)
    }

    @Test
    fun `summary and category contribute to ranking`() {
        val results = SearchEngine.search("podcast", catalog)
        assertEquals("org.example.podcatcher", results.first().packageName)
    }

    @Test
    fun `multi term query requires relevance`() {
        val results = SearchEngine.search("offline navigation", catalog)
        assertEquals("org.example.navigator", results.first().packageName)
    }

    @Test
    fun `limit is respected`() {
        val many = (1..100).map {
            testApp("org.example.app$it", "App $it", summary = "shared keyword")
        }
        assertEquals(10, SearchEngine.search("shared", many, limit = 10).size)
    }

    @Test
    fun `tokenizer strips punctuation and short tokens`() {
        assertEquals(listOf("foo", "bar"), SearchEngine.tokenize("foo, bar! x"))
    }
}
