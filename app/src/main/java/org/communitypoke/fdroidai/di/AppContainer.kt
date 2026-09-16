package org.communitypoke.fdroidai.di

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.communitypoke.fdroidai.BuildConfig
import org.communitypoke.fdroidai.ai.AiSearchProvider
import org.communitypoke.fdroidai.ai.MockAiSearchProvider
import org.communitypoke.fdroidai.ai.RemoteAiSearchProvider
import org.communitypoke.fdroidai.ai.agent.AgentProviderRegistry
import org.communitypoke.fdroidai.ai.agent.ClaudeCodeSdkProvider
import org.communitypoke.fdroidai.ai.agent.CodexSdkProvider
import org.communitypoke.fdroidai.ai.agent.MockAgentProvider
import org.communitypoke.fdroidai.data.fdroid.FdroidIndexService
import org.communitypoke.fdroidai.data.fdroid.FdroidRepository
import org.communitypoke.fdroidai.userscript.GmValueStore
import org.communitypoke.fdroidai.userscript.UserscriptBuilder
import org.communitypoke.fdroidai.userscript.UserscriptRuntime
import org.communitypoke.fdroidai.userscript.UserscriptStore
import retrofit2.Retrofit

/** Manual dependency container — keeps the object graph explicit and testable. */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS) // index-v2.json is large
        .build()

    private val indexService: FdroidIndexService = Retrofit.Builder()
        .baseUrl("https://f-droid.org/")
        .client(okHttpClient)
        .build()
        .create(FdroidIndexService::class.java)

    val fdroidRepository = FdroidRepository(
        service = indexService,
        json = json,
        cacheDir = context.cacheDir,
    )

    private val mockAiProvider = MockAiSearchProvider()

    /**
     * The provider used by the UI. When `AI_API_KEY` is configured the remote
     * provider answers; otherwise it transparently falls back to the mock.
     */
    val aiSearchProvider: AiSearchProvider = RemoteAiSearchProvider(
        apiKey = BuildConfig.AI_API_KEY,
        baseUrl = BuildConfig.AI_BASE_URL,
        model = BuildConfig.AI_MODEL,
        json = json,
        okHttpClient = okHttpClient,
        fallback = mockAiProvider,
    )

    /**
     * Agent SDK providers: Claude Code SDK (Anthropic Messages API) and Codex
     * SDK (OpenAI Responses API), plus an always-configured on-device mock.
     * `AgentProviderRegistry.preferred()` picks the first configured one.
     */
    val agentRegistry = AgentProviderRegistry(
        providers = listOf(
            ClaudeCodeSdkProvider(
                apiKey = BuildConfig.CLAUDE_API_KEY,
                baseUrl = BuildConfig.CLAUDE_BASE_URL,
                model = BuildConfig.CLAUDE_MODEL,
                json = json,
                okHttpClient = okHttpClient,
            ),
            CodexSdkProvider(
                apiKey = BuildConfig.CODEX_API_KEY,
                baseUrl = BuildConfig.CODEX_BASE_URL,
                model = BuildConfig.CODEX_MODEL,
                json = json,
                okHttpClient = okHttpClient,
            ),
            MockAgentProvider(),
        ),
    )

    val userscriptStore = UserscriptStore(
        dir = File(context.filesDir, "userscripts"),
        json = json,
    )

    val gmValueStore = GmValueStore(
        file = File(context.filesDir, "userscripts/gm-values.json"),
        json = json,
    )

    private val mainHandler = Handler(Looper.getMainLooper())

    /** WebView userscript runtime; the UI feeds it the enabled script set. */
    val userscriptRuntime = UserscriptRuntime(
        gmValues = gmValueStore,
        json = json,
        notifier = { title, text ->
            mainHandler.post {
                Toast.makeText(appContext, "$title\n$text".trim(), Toast.LENGTH_LONG).show()
            }
        },
        logger = { android.util.Log.d("Userscript", it) },
    )

    val userscriptBuilder = UserscriptBuilder(agentRegistry)

    /** Selected category for the Browse tab, set from the Categories tab. */
    val browseCategoryFilter = MutableStateFlow<String?>(null)
}
