# Video Downloader

An Android app that works like a browser with a built-in video detector. The user opens a
webpage in an in-app browser, plays/interacts with the video, and the app automatically
detects downloadable **HLS (M3U/M3U8)** streams and **direct video files** (MP4/MKV/WebM/
MOV). It then lets the user pick a quality and downloads it as a standard **MP4** using
**FFmpeg**.

> **FFmpeg:** integrated via Anton Karpenko's maintained FFmpegKit republish on Maven
> Central (`com.antonkarpenko:ffmpeg-kit-full-gpl`) — a normal Gradle sync pulls it, no
> extra setup. See [`BUILD_NOTES.md`](BUILD_NOTES.md) for details/fallbacks.

## Workflow

1. **Home** — paste a webpage URL and tap *Browse*.
2. **Browser** — a full WebView (JavaScript, cookies, localStorage, full-screen video,
   back/forward/refresh, loading progress).
3. **Detection** — every network request is passively inspected; media URLs are recorded
   with the page's Referer/Cookie/User-Agent so downloads replay authentically.
4. **Indicator** — an animated FAB shows a live count badge (`Download (3)`); it's
   inert until something playable is found.
5. **Analyze** — tapping the FAB fetches each playlist, distinguishes master vs. media
   playlists, and expands master playlists into per-quality formats (resolution, codec,
   estimated bitrate & size). Direct files are listed too.
6. **Format sheet** — a Material 3 bottom sheet lists every option.
7. **Download** — FFmpeg reads the playlist, downloads & merges segments (decrypting
   AES-128 when the key is reachable) and produces a single MP4. No `.ts` fragments are
   left behind.
8. **Encode** — remux (`-c copy`) when the source is already H.264/H.265 + AAC for
   maximum speed; otherwise transcode to H.264/AAC for compatibility. The worker falls
   back to a re-encode automatically if a remux fails.
9. **Download Manager** — progress, speed, ETA, current FFmpeg operation, and
   pause/resume/cancel/retry/delete. Multiple concurrent downloads are supported.
10. **Saved videos** — the MP4 is published to `Movies/VideoDownloader/` via MediaStore,
    so it appears in the gallery automatically.

## Architecture

MVVM + Repository, Kotlin Coroutines & `StateFlow`, WorkManager for background work, Room
for the persistent download queue, Jetpack Compose (Material 3) UI.

**Dependency injection:** a lightweight **manual DI container** (`di/AppContainer`, a
ServiceLocator created in `VideoDownloaderApp`). Hilt was the original choice, but its
Gradle plugin is incompatible with AGP 9 (it fails applying against the removed
`BaseExtension` API), so manual DI is used for a deterministic build with no annotation
processing. ViewModels are provided via `viewModel(factory = …)` and the worker via a
custom `WorkerFactory`. See `BUILD_NOTES.md` if you want to switch back to Hilt.

```
data/
  detection/   MediaUrlClassifier, DetectionRepository        (what counts as media)
  playlist/    HlsPlaylist(+Parser), CodecNames               (pure M3U8 parsing)
  analyze/     MediaAnalyzer                                   (playlist -> formats)
  download/    FfmpegCommandBuilder, CodecCompatibility,
               MediaStoreSaver, DownloadRepository             (how it gets downloaded)
  db/          Room entity/dao/database                        (persistence)
  model/       DetectedMedia, MediaFormat, DownloadStatus, ... (domain types)
worker/        DownloadWorker, DownloadWorkerFactory,
               DownloadNotifier                                (FFmpeg execution)
di/            AppContainer                                     (manual DI / ServiceLocator)
ui/
  home/        HomeScreen
  browser/     BrowserScreen, BrowserViewModel, WebViewClients
  downloads/   DownloadsScreen, DownloadsViewModel
  components/  FormatBottomSheet
  navigation/  Destinations
```

### Key design decisions

- **FFmpeg does the HLS heavy lifting.** Feeding the playlist URL to FFmpeg lets it
  download, decrypt (AES-128), concatenate, and remux/transcode in one pass — no scratch
  `.ts` files. Progress is derived from FFmpeg's statistics callbacks (processed media
  time ÷ total duration), with speed/ETA computed from output growth vs. wall-clock.
- **Passive, non-blocking detection.** `shouldInterceptRequest` only *observes* traffic
  and always returns `null`, so page behaviour is never altered.
- **Credential replay.** Referer/Cookie/User-Agent captured during browsing are forwarded
  to both analysis (OkHttp) and download (FFmpeg `-headers`/`-user_agent`).
- **Restart-based pause/resume.** WorkManager can't natively pause a running FFmpeg
  session, so pause cancels the session (killing the temp file) and resume re-runs it.
  This is called out honestly in `DownloadRepository`; a segment-level downloader would
  be the next step for true byte-accurate resume.

## Error handling

Expired/invalid playlists, network failures, HTTP redirects, encrypted-but-unsupported
streams, empty FFmpeg output, and codec incompatibilities are surfaced as user-friendly
messages (in the format sheet or the download manager's status line).
