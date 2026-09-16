package org.communitypoke.fdroidai.userscript

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchPatternTest {

    @Test
    fun `match all urls pattern matches http and https`() {
        assertTrue(MatchPattern.matchesMatchPattern("*://*/*", "https://a.b/c"))
        assertTrue(MatchPattern.matchesMatchPattern("*://*/*", "http://a.b/c"))
        assertFalse(MatchPattern.matchesMatchPattern("*://*/*", "file:///etc/passwd"))
    }

    @Test
    fun `scheme is matched exactly`() {
        assertTrue(MatchPattern.matchesMatchPattern("https://example.com/*", "https://example.com/x"))
        assertFalse(MatchPattern.matchesMatchPattern("https://example.com/*", "http://example.com/x"))
    }

    @Test
    fun `wildcard host covers apex and subdomains`() {
        val p = "*://*.example.com/*"
        assertTrue(MatchPattern.matchesMatchPattern(p, "https://example.com/"))
        assertTrue(MatchPattern.matchesMatchPattern(p, "https://www.example.com/a"))
        assertFalse(MatchPattern.matchesMatchPattern(p, "https://other.com/"))
        assertFalse(MatchPattern.matchesMatchPattern(p, "https://notexample.com/"))
    }

    @Test
    fun `path glob matches suffixes`() {
        assertTrue(MatchPattern.matchesMatchPattern("https://example.com/docs/*", "https://example.com/docs/a/b"))
        assertFalse(MatchPattern.matchesMatchPattern("https://example.com/docs/*", "https://example.com/blog"))
    }

    @Test
    fun `port is ignored`() {
        assertTrue(MatchPattern.matchesMatchPattern("http://example.com/*", "http://example.com:8080/x"))
    }

    @Test
    fun `invalid patterns are rejected`() {
        assertFalse(MatchPattern.isValidMatchPattern("https://example.com"))
        assertFalse(MatchPattern.isValidMatchPattern("http//example.com/"))
        assertFalse(MatchPattern.isValidMatchPattern("example.com/*"))
        assertFalse(MatchPattern.isValidMatchPattern("ftp://example.com/*"))
        assertTrue(MatchPattern.isValidMatchPattern("https://example.com/*"))
        assertTrue(MatchPattern.isValidMatchPattern("*://*/*"))
    }

    @Test
    fun `metadata match respects excludes`() {
        val metadata = UserscriptMetadata(
            name = "t",
            matches = listOf("*://*.example.com/*"),
            excludes = listOf("*://admin.example.com/*"),
        )
        assertTrue(MatchPattern.matches(metadata, "https://www.example.com/"))
        assertFalse(MatchPattern.matches(metadata, "https://admin.example.com/"))
    }

    @Test
    fun `include globs are used when no match patterns`() {
        val metadata = UserscriptMetadata(
            name = "t",
            includes = listOf("https://*.example.com/*"),
        )
        assertTrue(MatchPattern.matches(metadata, "https://www.example.com/x"))
        assertFalse(MatchPattern.matches(metadata, "http://www.example.com/x"))
    }

    @Test
    fun `no patterns matches nothing`() {
        val metadata = UserscriptMetadata(name = "t")
        assertFalse(MatchPattern.matches(metadata, "https://example.com/"))
    }
}
