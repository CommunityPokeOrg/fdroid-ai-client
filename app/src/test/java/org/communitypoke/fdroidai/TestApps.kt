package org.communitypoke.fdroidai

import org.communitypoke.fdroidai.data.model.FdroidApp

fun testApp(
    packageName: String,
    name: String,
    summary: String = "",
    description: String = "",
    categories: List<String> = emptyList(),
    lastUpdated: Long = System.currentTimeMillis(),
): FdroidApp = FdroidApp(
    packageName = packageName,
    name = name,
    summary = summary,
    description = description,
    iconUrl = null,
    categories = categories,
    license = "GPL-3.0",
    authorName = null,
    webSite = null,
    sourceCode = null,
    issueTracker = null,
    added = 1_600_000_000_000L,
    lastUpdated = lastUpdated,
    versionName = "1.0",
    apkSize = 1_000_000,
    minSdkVersion = 21,
)

fun testCatalog(): List<FdroidApp> = listOf(
    testApp(
        "org.example.passwordvault",
        "Password Vault",
        "Offline password manager with strong encryption",
        categories = listOf("Security", "Passwords"),
    ),
    testApp(
        "org.example.navigator",
        "Offline Navigator",
        "Maps and GPS navigation without internet",
        categories = listOf("Navigation"),
    ),
    testApp(
        "org.example.podcatcher",
        "PodCatcher",
        "Podcast player and RSS audio subscriptions",
        categories = listOf("Multimedia"),
    ),
    testApp(
        "org.example.termemu",
        "TermEmu",
        "Terminal emulator and SSH client",
        categories = listOf("Development"),
    ),
    testApp(
        "org.example.fotorama",
        "Fotorama",
        "Photo gallery and camera companion",
        categories = listOf("Multimedia"),
    ),
)
