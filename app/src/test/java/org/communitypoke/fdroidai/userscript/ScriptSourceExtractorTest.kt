package org.communitypoke.fdroidai.userscript

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScriptSourceExtractorTest {

    private val script = """
        |// ==UserScript==
        |// @name  T
        |// @match *://*/*
        |// ==/UserScript==
        |console.log('x');
    """.trimMargin()

    @Test
    fun `extracts fenced javascript block`() {
        val out = "Here is your script:\n```javascript\n$script\n```\nEnjoy!"
        assertEquals(script, ScriptSourceExtractor.extract(out))
    }

    @Test
    fun `extracts fenced block without language tag`() {
        val out = "```\n$script\n```"
        assertEquals(script, ScriptSourceExtractor.extract(out))
    }

    @Test
    fun `returns raw script starting at metadata block`() {
        val extracted = ScriptSourceExtractor.extract(script)
        assertTrue(extracted.contains("==UserScript=="))
        assertTrue(extracted.contains("console.log('x');"))
    }

    @Test
    fun `slices away leading prose`() {
        val out = "Sure! This script does X.\n$script"
        val extracted = ScriptSourceExtractor.extract(out)
        assertTrue(extracted.contains("==UserScript=="))
        assertTrue(UserscriptParser.parse(extracted).metadata.name == "T")
    }
}
