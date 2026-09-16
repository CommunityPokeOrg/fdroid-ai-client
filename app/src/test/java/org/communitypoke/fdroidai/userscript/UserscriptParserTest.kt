package org.communitypoke.fdroidai.userscript

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class UserscriptParserTest {

    private val validScript = """
        |// ==UserScript==
        |// @name        Dark Reader
        |// @namespace   org.example
        |// @version     1.2.3
        |// @description Darkens pages
        |// @match       *://*.example.com/*
        |// @exclude     *://admin.example.com/*
        |// @run-at      document-start
        |// @grant       GM_getValue
        |// @grant       GM_setValue
        |// @noframes
        |// ==/UserScript==
        |console.log('hi');
    """.trimMargin()

    @Test
    fun `parses full metadata block`() {
        val parsed = UserscriptParser.parse(validScript)
        val m = parsed.metadata
        assertEquals("Dark Reader", m.name)
        assertEquals("org.example", m.namespace)
        assertEquals("1.2.3", m.version)
        assertEquals("Darkens pages", m.description)
        assertEquals(listOf("*://*.example.com/*"), m.matches)
        assertEquals(listOf("*://admin.example.com/*"), m.excludes)
        assertEquals(RunAt.DOCUMENT_START, m.runAt)
        assertEquals(setOf("GM_getValue", "GM_setValue"), m.grants)
        assertTrue(m.noframes)
    }

    @Test
    fun `defaults run-at to document-end`() {
        val script = """
            |// ==UserScript==
            |// @name  T
            |// @match *://*/*
            |// ==/UserScript==
        """.trimMargin()
        assertEquals(RunAt.DOCUMENT_END, UserscriptParser.parse(script).metadata.runAt)
    }

    @Test
    fun `missing block throws`() {
        val e = assertThrows(UserscriptParseException::class.java) {
            UserscriptParser.parse("console.log('no block');")
        }
        assertTrue(e.errors.any { "metadata block" in it })
    }

    @Test
    fun `missing name throws`() {
        val e = assertThrows(UserscriptParseException::class.java) {
            UserscriptParser.parse("""
                |// ==UserScript==
                |// @match *://*/*
                |// ==/UserScript==
            """.trimMargin())
        }
        assertTrue(e.errors.any { "@name" in it })
    }

    @Test
    fun `missing match and include throws`() {
        val e = assertThrows(UserscriptParseException::class.java) {
            UserscriptParser.parse("""
                |// ==UserScript==
                |// @name T
                |// ==/UserScript==
            """.trimMargin())
        }
        assertTrue(e.errors.any { "@match or @include" in it })
    }

    @Test
    fun `invalid match pattern throws`() {
        val e = assertThrows(UserscriptParseException::class.java) {
            UserscriptParser.parse("""
                |// ==UserScript==
                |// @name  T
                |// @match not a pattern
                |// ==/UserScript==
            """.trimMargin())
        }
        assertTrue(e.errors.any { "@match" in it })
    }

    @Test
    fun `remote require is rejected`() {
        val e = assertThrows(UserscriptParseException::class.java) {
            UserscriptParser.parse("""
                |// ==UserScript==
                |// @name    T
                |// @match   *://*/*
                |// @require https://evil.example/x.js
                |// ==/UserScript==
            """.trimMargin())
        }
        assertTrue(e.errors.any { "@require" in it })
    }

    @Test
    fun `grant none yields empty grants`() {
        val script = """
            |// ==UserScript==
            |// @name  T
            |// @match *://*/*
            |// @grant none
            |// ==/UserScript==
        """.trimMargin()
        assertTrue(UserscriptParser.parse(script).metadata.grants.isEmpty())
    }

    @Test
    fun `computeId slugifies namespace and name`() {
        val m = UserscriptParser.parse(validScript).metadata
        assertEquals("org-example-dark-reader", UserscriptParser.computeId(m))
    }
}
