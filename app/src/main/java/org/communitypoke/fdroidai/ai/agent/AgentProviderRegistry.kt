package org.communitypoke.fdroidai.ai.agent

/**
 * Ordered collection of agent SDK providers. [preferred] returns the first
 * configured provider; the last entry is expected to be the always-configured
 * local mock so [preferred] never fails.
 */
class AgentProviderRegistry(val providers: List<AgentSdkProvider>) {

    /** The provider the UI should default to: first configured, else the mock. */
    fun preferred(): AgentSdkProvider =
        providers.firstOrNull { it.isConfigured } ?: providers.last()

    fun bySdk(sdk: AgentSdk): AgentSdkProvider? = providers.firstOrNull { it.sdk == sdk }

    /** True when at least one real (non-mock) SDK is configured. */
    val hasLiveProvider: Boolean get() = providers.any { it.sdk != AgentSdk.MOCK && it.isConfigured }
}
