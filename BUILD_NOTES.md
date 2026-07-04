# Build & Integration Notes

Important, practical notes for getting this project to build and run. Read the FFmpeg
section first — it's the one dependency that needs a decision from you.

---

## 1. FFmpeg (FFmpegKit) — required action

The app processes HLS/segmented and progressive video with **FFmpeg**, integrated via
**FFmpegKit**. We use a **`full-gpl`** variant because it bundles **libx264**, which is
needed for the H.264 re-encode path.

✅ **Resolved — no action needed.** The original `com.arthenica` artifacts were retired
in 2025, so this project depends on **Anton Karpenko's maintained republish**, which is
on **Maven Central** (already configured):

```
# gradle/libs.versions.toml  — FFmpeg 8.1.1
ffmpeg-kit = { group = "com.antonkarpenko", name = "ffmpeg-kit-full-gpl", version = "2.2.0" }
```

Crucially, this fork uses the **`com.antonkarpenko.ffmpegkit`** Java package, so
every `import com.antonkarpenko.ffmpegkit.*` in the code works. A normal Gradle
sync pulls it from Maven Central.

Reference: <https://central.sonatype.com/artifact/com.antonkarpenko/ffmpeg-kit-full-gpl>

### Fallback options (only if the above ever becomes unavailable)
1. **Vendor the `.aar` locally.** Drop it in `app/libs/` and use
   `implementation files('libs/ffmpeg-kit-full-gpl.aar')` (plus its `smart-exception-java`
   transitive dep).
2. **A self-hosted / internal Maven repo** mirroring the artifact.

The rest of the codebase is agnostic to *how* the binary is sourced — only the
`ffmpeg-kit` catalog entry needs to resolve, and it uses the stable FFmpegKit API
(`FFmpegKit`, `FFmpegSession`, `Statistics`, `ReturnCode`).

### If you'd rather not depend on FFmpegKit at all
The download layer is isolated behind `DownloadWorker` + `FfmpegCommandBuilder`. You can
swap in a native `mobile-ffmpeg` fork, a JNI build, or a segment-by-segment OkHttp
downloader + a single FFmpeg concat/remux pass, without touching the UI, analysis, or
persistence layers.

---

## 2. Toolchain versions

This project inherits an intentionally modern toolchain from the generated scaffold:

| Tool            | Version        |
|-----------------|----------------|
| AGP             | 9.2.1          |
| Kotlin          | 2.2.10         |
| Compose BOM     | 2026.02.01     |
| KSP             | `2.2.10-2.0.2` |

- **KSP must match Kotlin.** If you change the Kotlin version, update the `ksp` version in
  `libs.versions.toml` to the matching `"<kotlin>-<ksp>"` release, or KSP will refuse to run.
  (KSP is used only for Room's compiler.)
- AGP 9 provides built-in Kotlin compilation, so this module does **not** apply the
  `org.jetbrains.kotlin.android` plugin — only `kotlin.compose` and `ksp`.
- `minSdk` is **29** (Android 10) per the requirements; `targetSdk`/`compileSdk` are 36.

If your environment can't resolve these exact versions, dropping to a known-good LTS set
(AGP 8.7.x / Kotlin 2.0.21 / KSP 2.0.21-1.0.28 / Compose BOM 2024.09.x) requires no
source changes — only the catalog.

---

## 2a. Dependency injection: manual, not Hilt

The app uses a small **manual DI container** (`di/AppContainer`) instead of Hilt.

**Why:** Hilt's Gradle plugin (`com.google.dagger.hilt.android`, tried at 2.52) is
incompatible with **AGP 9** — it applies against the legacy `BaseExtension` API that AGP 9
removed and fails with *"Android BaseExtension not found."* Rather than pin an uncertain
Hilt version, we inject dependencies by hand:

- `AppContainer` holds the singletons (OkHttp, Room, repositories, analyzer, saver),
  created once in `VideoDownloaderApp`.
- ViewModels are built with `viewModel(factory = viewModelFactory { initializer { … } })`.
- The worker gets its dependencies from a custom `DownloadWorkerFactory` registered on
  WorkManager via `Configuration.Provider`.

**To switch back to Hilt** once a Hilt release supports AGP 9 (or if you downgrade AGP to
8.x): re-add the `hilt` plugin + `hilt-android`/`hilt-compiler`/`hilt-work`/
`hilt-navigation-compose` dependencies, restore the `@HiltAndroidApp` / `@AndroidEntryPoint`
/ `@HiltViewModel` / `@HiltWorker` / `@Inject` annotations, replace `AppContainer` with a
Hilt `@Module`, and swap the `viewModel(factory = …)` calls back to `hiltViewModel()`.

---

## 3. Permissions

- `INTERNET`, `ACCESS_NETWORK_STATE` — fetch pages, playlists, segments, keys.
- `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_DATA_SYNC` + `WAKE_LOCK` — background downloads.
- `POST_NOTIFICATIONS` — requested at runtime on first launch (Android 13+).
- **No storage permission** is needed: finished files are written straight into the
  MediaStore `Movies/VideoDownloader` collection (scoped storage, API 29+).

---

## 4. Config cache

`gradle.properties` enables the configuration cache. Hilt + KSP are compatible with it; if
you hit a cache-serialization issue on your specific plugin versions, disable it with
`org.gradle.configuration-cache=false`.
