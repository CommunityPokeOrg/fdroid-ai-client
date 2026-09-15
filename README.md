# F-Droid AI

A modern F-Droid client for Android with AI-powered semantic search.

Built with Kotlin and Jetpack Compose (Material 3), MVVM architecture, Retrofit +
kotlinx-serialization for the F-Droid `index-v2` format, and a clean
`AiSearchProvider` abstraction with a working on-device provider plus an
OpenAI-compatible remote provider.

## Features

- **Browse** the full F-Droid catalog with per-category filtering, pull the
  latest `index-v2.json` and cache it on disk (24 h TTL, offline fallback).
- **Categories** — grid of every category in the repo with app counts; tapping
  a category filters the Browse list.
- **Search** — instant local keyword search, plus an **AI mode** (sparkle
  icon) that interprets natural-language queries like *"offline maps for
  hiking"* and returns ranked results with per-app reasons.
- **App details** — icon, author, categories, license, version, APK size,
  min-SDK, added/updated dates, description and outbound links.
- **Repository info** — repo name, address, description, index timestamp,
  app count and mirrors.
- Loading, empty and error states on every screen; offline banner when the
  index is served from cache.
- Custom splash screen with the project otter artwork and the credit line
  "Built by Poke x Devin".

## AI search

`ai/AiSearchProvider.kt` defines the contract:

```kotlin
interface AiSearchProvider {
    val name: String
    val isConfigured: Boolean
    suspend fun search(request: AiSearchRequest): AiSearchResponse
}
```

Two implementations ship in `ai/`:

- `RemoteAiSearchProvider` — OpenAI-compatible `chat/completions` client. It
  pre-filters the catalog with the local `SearchEngine` to keep prompts small,
  asks the model to re-rank candidates as JSON, and merges results back into
  domain objects. Any failure (network, missing key, bad JSON) falls back to
  the mock provider transparently.
- `MockAiSearchProvider` — fully on-device provider used when no API key is
  configured. It simulates semantic search with concept/synonym expansion and
  weighted scoring, and emits per-app reasons and a natural-language summary.
  The UI shows a "Demo mode" badge while this provider is active.

### Configuring a real AI backend

Set these before building (either as Gradle properties or environment
variables — no secrets are committed):

```bash
export AI_API_KEY="sk-..."            # any OpenAI-compatible key
export AI_BASE_URL="https://api.openai.com/v1"   # optional, this is the default
export AI_MODEL="gpt-4o-mini"                     # optional
./gradlew :app:assembleDebug
```

They are baked into `BuildConfig` (`AI_API_KEY`, `AI_BASE_URL`, `AI_MODEL`),
so the APK works without any local.properties entries.

## Requirements

- JDK 17
- Android SDK Platform 34 + Build-Tools 34 (`sdkmanager "platforms;android-34" "build-tools;34.0.0"`)
- Or just open the project in **Android Studio Ladybug+** and let it sync.

## Build & test

```bash
# one-off: generate the Gradle wrapper (needs any Gradle ≥ 8.9)
gradle wrapper --gradle-version 8.10.2

./gradlew :app:assembleDebug     # APK in app/build/outputs/apk/debug/
./gradlew :app:testDebugUnitTest # unit tests
```

The Gradle build expects `sdk.dir` in `local.properties` or the
`ANDROID_HOME`/`ANDROID_SDK_ROOT` environment variable.

## Project layout

```
app/src/main/java/org/communitypoke/fdroidai/
├── FdroidAiApplication.kt      # holds the AppContainer
├── MainActivity.kt             # native splash + Compose host
├── di/AppContainer.kt          # manual DI (no framework)
├── data/
│   ├── model/                  # index-v2 DTOs, domain models, mappers
│   ├── fdroid/                 # Retrofit service + FdroidRepository (disk cache)
│   └── search/SearchEngine.kt  # local keyword scoring
├── ai/                         # AiSearchProvider + mock + remote impl
└── ui/
    ├── common/                 # UiState + Loading/Empty/Error views, AppListItem
    ├── navigation/             # NavHost + bottom bar
    ├── splash/                 # otter splash ("Built by Poke x Devin")
    ├── home/ · categories/ · search/ · detail/ · repo/   # screens + VMs
    └── theme/                  # navy/teal Material 3 palette
app/src/test/                   # unit tests (mapping, search, mock AI)
```

## Data source

The app downloads `https://f-droid.org/repo/index-v2.json` on first launch
(streamed to disk, then parsed with `Json.decodeFromStream`). The file is
several tens of MB; subsequent launches reuse the cached file for 24 h and
fall back to it when offline.

## License

MIT — see [LICENSE](LICENSE). F-Droid and its index data belong to their
respective owners; app metadata and icons are fetched from f-droid.org.
