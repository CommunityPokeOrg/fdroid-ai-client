package org.communitypoke.fdroidai.data

import kotlinx.serialization.json.Json
import org.communitypoke.fdroidai.data.model.IndexV2
import org.communitypoke.fdroidai.data.model.toDomain
import org.communitypoke.fdroidai.data.model.toRepoMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IndexMappingTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val fixture = """
        {
          "repo": {
            "name": {"en-US": "F-Droid"},
            "icon": {"en-US": {"name": "icons/fdroid-icon.png", "sha256": "abc"}},
            "address": "https://f-droid.org/repo",
            "description": {"en-US": "The official F-Droid repository"},
            "timestamp": 1700000000000,
            "mirrors": [{"url": "https://mirror.example.org/fdroid", "isPrimary": false}],
            "unknownField": "ignored"
          },
          "packages": {
            "org.example.alpha": {
              "metadata": {
                "name": {"en-US": "Alpha"},
                "summary": {"en-US": "First app", "fr-FR": "Première app"},
                "description": {"en-US": "A long description"},
                "icon": {"en-US": {"name": "org.example.alpha.1.png"}},
                "categories": ["Internet", "Tools"],
                "license": "MIT",
                "authorName": "Jane Dev",
                "webSite": "https://alpha.example.org",
                "sourceCode": "https://git.example.org/alpha",
                "added": 1690000000000,
                "lastUpdated": 1699000000000
              },
              "versions": {
                "100": {"manifest": {"versionName": "1.0", "versionCode": 100, "usesSdk": {"minSdkVersion": 21}}, "file": {"name": "a.apk", "size": 5000}},
                "200": {"manifest": {"versionName": "2.0", "versionCode": 200, "usesSdk": {"minSdkVersion": 24}}, "file": {"name": "b.apk", "size": 8000}}
              }
            },
            "org.example.beta": {
              "metadata": {
                "name": {"de-DE": "Beta"},
                "summary": {},
                "categories": [],
                "added": 0,
                "lastUpdated": 0
              },
              "versions": {}
            }
          }
        }
    """.trimIndent()

    @Test
    fun `parses repo metadata including mirrors and localized strings`() {
        val index = json.decodeFromString(IndexV2.serializer(), fixture)
        val meta = index.toRepoMetadata(appCount = 2)
        assertEquals("F-Droid", meta.name)
        assertEquals("The official F-Droid repository", meta.description)
        assertEquals("https://f-droid.org/repo", meta.address)
        assertEquals(1_700_000_000_000L, meta.timestamp)
        assertEquals(listOf("https://mirror.example.org/fdroid"), meta.mirrors)
        assertEquals(2, meta.appCount)
        assertEquals("https://f-droid.org/repo/icons/fdroid-icon.png", meta.iconUrl)
    }

    @Test
    fun `maps package to domain with latest version and icon path`() {
        val index = json.decodeFromString(IndexV2.serializer(), fixture)
        val app = index.packages.getValue("org.example.alpha")
            .toDomain("org.example.alpha", "https://f-droid.org/repo")

        assertEquals("Alpha", app.name)
        assertEquals("First app", app.summary)
        assertEquals(listOf("Internet", "Tools"), app.categories)
        assertEquals("MIT", app.license)
        assertEquals("Jane Dev", app.authorName)
        // Icon name has no path separator -> icons-640 bucket.
        assertEquals("https://f-droid.org/repo/icons-640/org.example.alpha.1.png", app.iconUrl)
        // Latest version picked by versionCode.
        assertEquals("2.0", app.versionName)
        assertEquals(8_000L, app.apkSize)
        assertEquals(24, app.minSdkVersion)
        assertEquals("https://f-droid.org/packages/org.example.alpha/", app.webUrl)
    }

    @Test
    fun `falls back to first locale and tolerates missing fields`() {
        val index = json.decodeFromString(IndexV2.serializer(), fixture)
        val app = index.packages.getValue("org.example.beta")
            .toDomain("org.example.beta", "https://f-droid.org/repo")

        assertEquals("Beta", app.name) // de-DE fallback when no en-US/en
        assertEquals("", app.summary)
        assertNull(app.iconUrl)
        assertNull(app.versionName)
        assertNull(app.apkSize)
    }
}
