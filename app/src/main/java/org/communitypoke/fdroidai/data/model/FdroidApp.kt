package org.communitypoke.fdroidai.data.model

/** Domain model for a single app in the F-Droid repository. */
data class FdroidApp(
    val packageName: String,
    val name: String,
    val summary: String,
    val description: String,
    val iconUrl: String?,
    val categories: List<String>,
    val license: String?,
    val authorName: String?,
    val webSite: String?,
    val sourceCode: String?,
    val issueTracker: String?,
    val added: Long,
    val lastUpdated: Long,
    val versionName: String?,
    val apkSize: Long?,
    val minSdkVersion: Int?,
) {
    /** F-Droid web page for this package. */
    val webUrl: String get() = "https://f-droid.org/packages/$packageName/"
}

/** Metadata describing the F-Droid repository itself (the `repo` block of the index). */
data class RepoMetadata(
    val name: String,
    val description: String,
    val address: String,
    val iconUrl: String?,
    val timestamp: Long,
    val mirrors: List<String>,
    val appCount: Int,
)
