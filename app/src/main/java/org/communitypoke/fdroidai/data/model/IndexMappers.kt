package org.communitypoke.fdroidai.data.model

/** Pick the best localized string: en-US, then en, then the first available locale. */
fun Map<String, String>.localized(): String =
    this["en-US"] ?: this["en"] ?: values.firstOrNull().orEmpty()

/** Pick the best localized file descriptor. */
private fun Map<String, LocalizedFileDto>.localizedFile(): LocalizedFileDto? =
    this["en-US"] ?: this["en"] ?: values.firstOrNull()

/**
 * Build an absolute icon URL from a repo-relative file name.
 * index-v2 icons ship under `icons-<dpi>/` when the name has no path component.
 */
fun iconUrl(repoAddress: String, icon: Map<String, LocalizedFileDto>): String? {
    val file = icon.localizedFile() ?: return null
    if (file.name.isBlank()) return null
    if (file.name.startsWith("http")) return file.name
    val base = repoAddress.trimEnd('/')
    return if (file.name.contains('/')) "$base/${file.name}" else "$base/icons-640/${file.name}"
}

private fun PackageDto.latestVersion(): VersionDto? =
    versions.values.maxByOrNull { it.manifest.versionCode }

fun PackageDto.toDomain(packageName: String, repoAddress: String): FdroidApp {
    val latest = latestVersion()
    return FdroidApp(
        packageName = packageName,
        name = metadata.name.localized().ifBlank { packageName },
        summary = metadata.summary.localized(),
        description = metadata.description.localized(),
        iconUrl = iconUrl(repoAddress, metadata.icon),
        categories = metadata.categories,
        license = metadata.license,
        authorName = metadata.authorName,
        webSite = metadata.webSite,
        sourceCode = metadata.sourceCode,
        issueTracker = metadata.issueTracker,
        added = metadata.added,
        lastUpdated = metadata.lastUpdated,
        versionName = latest?.manifest?.versionName?.ifBlank { null }
            ?: metadata.suggestedVersionName,
        apkSize = latest?.file?.size?.takeIf { it > 0 },
        minSdkVersion = latest?.manifest?.usesSdk?.minSdkVersion,
    )
}

fun IndexV2.toRepoMetadata(appCount: Int): RepoMetadata = RepoMetadata(
    name = repo.name.localized().ifBlank { "F-Droid" },
    description = repo.description.localized(),
    address = repo.address,
    iconUrl = iconUrl(repo.address, repo.icon),
    timestamp = repo.timestamp,
    mirrors = repo.mirrors.map { it.url }.filter { it.isNotBlank() },
    appCount = appCount,
)
