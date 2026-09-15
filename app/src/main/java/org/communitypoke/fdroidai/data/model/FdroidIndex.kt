package org.communitypoke.fdroidai.data.model

import kotlinx.serialization.Serializable

/**
 * DTOs mirroring the F-Droid `index-v2.json` format.
 * Unknown fields are ignored via the lenient [kotlinx.serialization.json.Json] config.
 */
@Serializable
data class IndexV2(
    val repo: RepoDto = RepoDto(),
    val packages: Map<String, PackageDto> = emptyMap(),
)

@Serializable
data class RepoDto(
    val name: Map<String, String> = emptyMap(),
    val icon: Map<String, LocalizedFileDto> = emptyMap(),
    val address: String = "",
    val description: Map<String, String> = emptyMap(),
    val timestamp: Long = 0,
    val mirrors: List<MirrorDto> = emptyList(),
)

@Serializable
data class MirrorDto(
    val url: String = "",
    val location: String? = null,
    val isPrimary: Boolean = false,
)

@Serializable
data class PackageDto(
    val metadata: PackageMetadataDto = PackageMetadataDto(),
    val versions: Map<String, VersionDto> = emptyMap(),
)

@Serializable
data class PackageMetadataDto(
    val name: Map<String, String> = emptyMap(),
    val summary: Map<String, String> = emptyMap(),
    val description: Map<String, String> = emptyMap(),
    val icon: Map<String, LocalizedFileDto> = emptyMap(),
    val categories: List<String> = emptyList(),
    val license: String? = null,
    val authorName: String? = null,
    val authorEmail: String? = null,
    val webSite: String? = null,
    val sourceCode: String? = null,
    val issueTracker: String? = null,
    val changelog: String? = null,
    val added: Long = 0,
    val lastUpdated: Long = 0,
    val suggestedVersionName: String? = null,
)

@Serializable
data class LocalizedFileDto(
    val name: String = "",
    val sha256: String? = null,
    val size: Long? = null,
)

@Serializable
data class VersionDto(
    val manifest: ManifestDto = ManifestDto(),
    val file: FileDto? = null,
    val added: Long = 0,
)

@Serializable
data class ManifestDto(
    val versionName: String = "",
    val versionCode: Long = 0,
    val usesSdk: UsesSdkDto? = null,
)

@Serializable
data class UsesSdkDto(
    val minSdkVersion: Int? = null,
    val targetSdkVersion: Int? = null,
)

@Serializable
data class FileDto(
    val name: String = "",
    val size: Long = 0,
)
