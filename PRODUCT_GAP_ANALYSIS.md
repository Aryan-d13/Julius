# Julius AI — Product Gap Analysis & Strategic Roadmap

---

## 1. Current Product Capabilities

Julius currently provides a solid technical core for backend video ingestion, AI scoring, workspace multi-tenancy, and operator management. Below is the feature mapping:

### Authentication & Identity
*   **Secure JWT Engine:** Issuer validation, clock skew offset tolerance, and token signature validation.
*   **Hardened Cryptography:** Password storage using Argon2id with dummy Argon2 verify routines to mitigate email enumeration attacks.
*   **Session Tracking & RTR:** Database-backed active session ledger featuring automatic token reuse detection and compromised token tree revocation.
*   **SSO Federation:** Login hooks supporting Google and GitHub federated identity mapping.

### Organizations & Multi-Tenancy
*   **Resource-Oriented Hierarchies:** Clear tenant boundaries matching `Organizations` -> `Workspaces` -> `Jobs`.
*   **Role-Based Access Control (RBAC):** Normalised database tables (`roles`, `role_permissions`) with hierarchical custom permission mapping checks.
*   **Soft Deletes:** Audit logs and resource safety utilizing `deleted_at TIMESTAMPTZ` fields instead of raw boolean flags.

### Video Ingestion & Download Pipeline
*   **YouTube Fetcher:** Native CLI-based downloader extraction.
*   **Media Converter:** Audio stream extraction into AAC/WAV format using FFmpeg.

### Transcription & AI Intelligence
*   **Gemini Audio Processing:** Audio upload via Files API and native `transcribeAudio` using Gemini 1.5 Flash.
*   **Virality Scoring:** Structured prompts in `GeminiService` ranking sections, generating reasoning texts, and providing English and Hindi POV captions overlay ideas.
*   **Idempotency & Locks:** Redis-backed distributed lock manager protecting pipeline segments against concurrent executions.

### Video Editing & Rendering
*   **FFmpeg Cut Engine:** Frame-accurate cuts utilizing hardware-accelerated/ultrafast presets.

### Customer Web Platform
*   **Modular Next.js App Router:** Isolated feature paths (`(auth)`, `(dashboard)`) using TanStack Query.
*   **Split-Screen Clip Viewer:** HTML5 video preview alongside interactive, word-clickable transcript seeking (clicking text jumps video position).
*   **Command Palette:** Global search and navigation overlay mapped to `⌘K`.
*   **SSE Logs Console:** Live pipeline status nodes and logging logs streamed to a simulated CLI terminal.

### Admin Platform
*   **Polymorphic Global Search:** Zentrale query matching users, orgs, workspaces, and jobs.
*   **Operator Notes:** Private internal comments section for operators on tenant models.
*   **Telemetry Panel:** Whisper processing minutes, Gemini token volumes, and queue workers utilization tracking.

---

## 2. Missing Core Features

To move Julius from a developer tool to a viral clip platform, the following features are missing, ranked by urgency:

### Critical (Blockers for Launch)
*   **AI Auto-Reframe (Face/Object Tracking):** Users upload 16:9 widescreen videos. Julius currently cuts the video but does not crop it dynamically to follow the speaker. Without 9:16 vertical tracking, the clips are unusable on TikTok or Reels.
*   **Burn-in Captions Renderer:** Modern shorts require styled dynamic subtitles (highlight colors, animations, emojis) burned directly onto the video. Julius currently displays text next to the video but exports plain cut MP4s.
*   **Interactive Trim Adjuster:** Users must be able to tweak the start/end cut times of a clip manually before exporting.

### Important (Retention & Conversion)
*   **Integrated Billing & Usage Quotas:** Stripe checkouts mapping processing limits (e.g., "120 minutes/month").
*   **Workspace Template System:** Preconfigured crop positions, caption fonts, and layouts.
*   **Export Pipeline Options:** Render resolution switches, watermark configurations, and direct download links.
*   **Collaborative Team Invitations:** Actual organization invite emails flow.

### Nice to Have (Growth & Viral Loops)
*   **Direct Social Publishing:** One-click posting to TikTok, YouTube Shorts, and Instagram.
*   **API & Webhooks:** Letting developers trigger clip rendering via API request keys.
*   **Workspace Analytics Dashboard:** Click rates, views, and engagements tracking on shared clips.

---

## 3. Competitive Analysis

| Feature | Opus Clip | Riverside / Descript | Riverside | Julius AI (Current) |
|---|---|---|---|---|
| **Transcription** | Yes (AI) | Yes (AI text-editor) | Yes (AI) | Yes (Gemini 1.5 Flash) |
| **Auto-Reframe** | Yes (Active Tracking) | No (Manual Frame) | No (Manual Frame) | **No** (Plain crop/cut) |
| **Burn-in Captions** | Yes (Animated Templates) | Yes (Basic styles) | Yes (Basic styles) | **No** (UI only text) |
| **Interactive Editor** | Yes | Yes (Text-based edit) | Yes | **No** (Read-only transcript) |
| **Multi-Tenancy** | Yes | Yes | Yes | Yes |

### Competitor Deep-dive

#### 1. Opus Clip
*   *Their Strength:* Elite face-tracking reframe, highly optimized dynamic text presets, automatic virality scoring.
*   *Julius Opportunity:* Julius has a faster backend pipeline using the raw Gemini Files API and a clean Next.js architecture. By implementing an open-source face detection model (like Mediapipe or OpenCV) on the worker, Julius can deliver comparable cropping without premium SaaS costs.

#### 2. Descript
*   *Their Strength:* Text-based video editing (deleting a word in the transcript cuts the video segment).
*   *Julius Opportunity:* Julius can integrate a simplified version of this by allowing users to select word spans on the transcript viewer and trigger a crop/cut process on the backend.

---

## 4. Technical Weaknesses

Remaining engineering issues affecting production quality:
*   **FFmpeg CPU Bottleneck:** The worker cuts clips synchronously using process builders. Under load, rendering multiple clips concurrently will saturate CPU threads.
*   **No Media Storage Cleanup Policy:** Expired jobs leave raw video files on local storage or GCS, causing storage costs to grow indefinitely.
*   **No Dead Letter Queue (DLQ):** Task worker queue failures are retried but can clog worker threads if media downloads fail repeatedly.
*   **EventSource Memory Leak Risk:** If a user navigates away from a running job view, unclosed SSE streams will cause socket exhaustion.

---

## 5. Recommended Roadmap (Epics 9 - 13)

### Epic 9 — AI Auto-Reframe & 9:16 Vertical Cropper
*   **Goal:** Crop 16:9 input videos into 9:16 portrait orientation, tracking the active speaker automatically.
*   **User Value:** Uploaded videos become instantly compatible with mobile TikTok/Reels layout rules.
*   **Estimated Complexity:** High
*   **Dependencies:** Epic 8 (Cut engine)
*   **Acceptance Criteria:** Backend tracks coordinate coordinates (face bounding boxes) using a lightweight detector, generating cropped segments via FFmpeg's `crop` filter without cutting off the subject.

### Epic 10 — Subtitle Burner & Visual Templates
*   **Goal:** Render animated, styled text highlights on top of the exported MP4 file.
*   **User Value:** Dynamic, high-retention subtitles are baked into the video.
*   **Estimated Complexity:** High
*   **Dependencies:** Epic 9
*   **Acceptance Criteria:** FFmpeg applies `drawtext` or overlay subtitle files (.ass) styled with custom fonts, colors, and border widths based on selected template presets.

### Epic 11 — Interactive Transcript editor & Trim Adjuster
*   **Goal:** Enable users to edit transcript texts and tweak start/end timestamps.
*   **User Value:** Allows correcting spelling errors and adjusting clip timings before downloading.
*   **Estimated Complexity:** Medium
*   **Dependencies:** Epic 10
*   **Acceptance Criteria:** The Next.js UI allows dragging timeline markers, and clicking "Re-Render" submits updated segment timestamps to the backend.

### Epic 12 — Usage Quotas & Stripe Payments Billing
*   **Goal:** Restrict processing limits based on subscription tiers.
*   **User Value:** Fair pricing model protecting server CPU resources.
*   **Estimated Complexity:** Medium
*   **Dependencies:** Epic 5 (Auth)
*   **Acceptance Criteria:** Stripe webhook intercepts limit upgrades, and the worker rejects jobs if the tenant's workspace processing minute balance is depleted.

---

## 6. Product Vision

### "If Julius launched tomorrow, what would stop users from choosing it over Opus Clip?"

Brutally honest answer:

**Absolutely everything that makes a clip viral is missing.**

1.  **Widescreen is Dead:** If a user receives a 16:9 clip cut of their podcast, they cannot post it to TikTok. They would have to take it into Premiere or CapCut to crop it manually. Opus Clip does this in 3 seconds.
2.  **No Subtitles = No Views:** 80% of users watch shorts on silent. If Julius does not burn styled captions onto the video, the clip will not get engagement.
3.  **Read-only is Useless:** AI makes mistakes in choosing start/end timestamps. Without a timeline trimmer to adjust the margins, users are stuck with awkward cuts.

*Conclusion:* Julius has built a secure, performant enterprise backend framework. However, until it implements **AI Auto-Reframe** and **Subtitle Burning**, it remains a developer platform rather than a competitive consumer SaaS. Epic 9 and 10 are the mandatory milestones for product-market fit.
