package org.communitypoke.fdroidai.di

import android.content.Context
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.communitypoke.fdroidai.BuildConfig
import org.communitypoke.fdroidai.ai.AiSearchProvider
import org.communitypoke.fdroidai.ai.MockAiSearchProvider
import org.communitypoke.fdroidai.ai.RemoteAiSearchProvider
import org.communitypoke.fdroidai.data.fdroid.FdroidIndexService
import org.communitypoke.fdroidai.data.fdroid.FdroidRepository
import retrofit2.Retrofit

/** Manual dependency container — keeps the object graph explicit and testable. */
class AppContainer(context: Context) {

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

    /** Selected category for the Browse tab, set from the Categories tab. */
    val browseCategoryFilter = MutableStateFlow<String?>(null)
}
