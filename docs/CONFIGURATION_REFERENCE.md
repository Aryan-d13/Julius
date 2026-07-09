# Julius Configuration Reference Guide

This reference guide lists all configuration namespaces, property keys, and validation rules in Julius.

## 1. Key Migration & Breaking Changes (Epic 4)

Julius has migrated from raw `@Value` properties to strongly typed, immutable configuration properties classes. This is a breaking pre-1.0 design change to simplify the property schema and enable automatic metadata autocompletion.

### Migration Mapping Table

| Legacy Key (Raw `@Value`) | New Property Key | Namespace | Description |
| :--- | :--- | :--- | :--- |
| `clipper.download.dir` | `clipper.workspace.download-dir` | `clipper.workspace` | Local processing download path |
| `clipper.convert.dir` | `clipper.workspace.convert-dir` | `clipper.workspace` | Local conversion path |
| `clipper.cut.dir` | `clipper.workspace.cut-dir` | `clipper.workspace` | Local fragment cutting path |
| `clipper.cache.dir` | `clipper.workspace.cache-dir` | `clipper.workspace` | Local cache/analysis path |
| `clipper.library.video.dir` | `clipper.workspace.video-library-dir` | `clipper.workspace` | Local library videos root |
| `clipper.library.audio.dir` | `clipper.workspace.audio-library-dir` | `clipper.workspace` | Local library audios root |
| `clipper.render.output.dir` | `clipper.workspace.render-output-dir` | `clipper.workspace` | Local render jobs output root |
| `youtube.cookies.path` | `clipper.download.cookies-path` | `clipper.download` | yt-dlp cookie files path |
| `ytdlp.format` | `clipper.download.format` | `clipper.download` | yt-dlp download resolution format |
| `google.api.key` | `clipper.ai.gemini-api-key` | `clipper.ai` | Google Gemini model API key |
| `gemini.model` | `clipper.ai.gemini-model` | `clipper.ai` | Google Gemini model name |
| `clipper.whisper.model` | `clipper.ai.whisper.model` | `clipper.ai` | Whisper model configuration name |
| `clipper.python.path` | `clipper.ai.whisper.python-path` | `clipper.ai` | Path to python execution executable |
| `clipper.python.env` | `clipper.ai.whisper.python-env` | `clipper.ai` | Environment variables for python |

---

## 2. Configuration Properties Reference

### `clipper.workspace`
*   `download-dir` (String, Required, default: `data/temp/downloads`): Temp storage for video downloads.
*   `convert-dir` (String, Required, default: `data/temp/converted`): Temp storage for media conversion outputs.
*   `cut-dir` (String, Required, default: `data/temp/fragments`): Temp storage for clip rendering cuts.
*   `cache-dir` (String, Required, default: `data/library/cache`): Analysis cache.
*   `video-library-dir` (String, Required, default: `data/library/videos`): Library video folder.
*   `audio-library-dir` (String, Required, default: `data/library/audios`): Library audio folder.
*   `render-output-dir` (String, Required, default: `data/jobs`): Pipeline render output directory.

### `clipper.download`
*   `dir` (String, Required, default: `data/temp/downloads`): Directory for yt-dlp execution.
*   `cookies-path` (String, Optional): Path to Netscape cookies text files.
*   `format` (String, Required, default: `bestvideo[height<=720]+bestaudio/best`): Resolution constraints.

### `clipper.ai`
*   `gemini-api-key` (String, Required): API key. Masked in summaries.
*   `gemini-model` (String, Required, default: `gemini-1.5-flash`): AI model.
*   `whisper.model` (String, Required, default: `large-v3-turbo`): Whisper setup model.
*   `whisper.python-path` (String, Required, default: `python`): Executable path.
*   `whisper.python-env` (String, Optional): Python environment details.
