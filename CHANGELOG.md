# Changelog

All notable changes to this project will be documented in this file.

## [0.6.1] - 2026-04-15

### Fixed
- **Corrupt Gemma bundle recovery** - The app now validates saved and imported MediaPipe task bundles before runtime use, invalidates broken local model files, and replaces the native `Unable to open zip archive` failure with a clear recovery message.
- **Verification marker integrity** - Saved model verification now stores the real file checksum and rechecks the archive structure on startup so stale `.verified` markers cannot mask a damaged bundle.
- **Glass dashboard readability** - Rebalanced the Compose glassmorphism theme with stronger card contrast, clearer log surfaces, and more legible text on-device.

### Verified
- `./gradlew.bat testDebugUnitTest --console=plain`
- `./gradlew.bat connectedDebugAndroidTest --console=plain`
- `./gradlew.bat :app:installDebug --console=plain`
- Live emulator QA with a deliberately corrupted `.task` bundle seeded into app storage to confirm the friendly recovery state.

## [0.6.0] - 2026-04-15

### Added
- **On-device cognitive loop** - Introduced a foreground `AgentAutomationService` plus `AccessibilityAgentLoop` that performs a strict perceive, plan, execute, and reflect cycle using the live accessibility tree.
- **Filtered accessibility perception** - Added `AccessibilityNodeSnapshotter` and snapshot models that prune invisible, empty, and non-meaningful nodes before prompting Gemma, reducing prompt size and improving action reliability.
- **Strict JSON action contract** - Added dedicated action command, parser, and dispatcher classes for single-step `TAP`, `SWIPE`, `TYPE`, `LONG_PRESS`, `WAIT`, `BACK`, `HOME`, and `COMPLETE` responses.
- **Compose operator dashboard** - Rebuilt the main dashboard in Jetpack Compose with a glassmorphism control surface, live runtime state, model download controls, and streamed loop logs.
- **Research-style internals documentation** - Added a formal architecture document with screenshots and diagrams describing the runtime pipeline, prompt contract, and failure handling.

### Changed
- **Manifest and runtime wiring** - Registered the foreground automation service, enabled Compose, and routed general app-control commands through the new accessibility-driven loop while preserving safe deterministic fast paths.
- **Gemma inference initialization** - Updated the MediaPipe runtime to prefer GPU execution when available and automatically fall back to default and CPU backends.
- **Android tests** - Reworked instrumentation coverage around the new Compose activity/runtime flow while keeping emulator execution stable.

### Verified
- `./gradlew.bat testDebugUnitTest --console=plain`
- `./gradlew.bat connectedDebugAndroidTest --console=plain`
- `./gradlew.bat assembleRelease --console=plain`

## [0.5.10] - 2026-04-15

### Fixed
- **Full Task Continuation** - Removed the `GENERAL_APP_CONTROL` fast path that only launched an app and stopped. Commands like "open Play Store and download Subway Surfers" now stay in the full planner/executor loop.
- **Play Store Install Reliability** - Added a deterministic Play Store install plan so download/install requests search, open the app listing, tap install, and wait for the visible install state instead of relying on a short partial plan.
- **Safer Completion Checks** - Verifier fallback is now fail-safe instead of fail-open. If Gemma returns malformed verification JSON, the agent keeps working from the live screen instead of silently treating the task as done.
- **UI Target Matching** - Accessibility text taps now support fallback aliases like `Search|Search apps & games`, which improves search-field matching across different Play Store screens and device variants.

## [0.5.3] - 2026-04-14

### Fixed
- **App Conflicts** — Permanently removed `applicationIdSuffix` from debug/alpha builds. All builds now use `com.gemma.agentphone`, enabling seamless updates without uninstalls.
- **In-App Updates** — Rewrote `UpdateManager` to pull from the general releases list instead of the restricted `latest` endpoint, ensuring automated pre-releases are detected.
- **Download Visibility** — Model download status text is now interactive. Tapping an "Error" status surfaces the exact failure reason (e.g., 401 Unauthorized, Insufficient Space).

## [0.5.2] - 2026-04-14

### Added
- **LLM-Driven Orchestration** — Introduced `LlmGoalInterpreter` which uses Gemma 4 to classifies user commands into categories (Web Search, App Control, etc.) with JSON extraction.
- **True Autonomous Agent Control** — Ambiguous or complex instructions now bypass rigid "one-shot" app launches and route directly to the `AccessibilityExecutor`.
- **Autonomous Feedback Loop** — The agent now "sees" the screen, thinks, taps, and reviews visuals in a loop until the goal is achieved.

## [0.5.0] - 2026-04-14

### Added
- **Manus-style Chain of Thought mini-window** — Real-time, step-by-step agent reasoning displayed as a scrollable RecyclerView in the center of the screen. Each step shows thought, action, executor, and status badge.
- **CotStepAdapter** — New RecyclerView adapter for live CoT step streaming with smooth `notifyItemInserted` animations.
- **Glassmorphism design system** — Frosted glass cards, gradient accents, ambient glow orbs, ripple touch feedback, and immersive transparent status bar across all screens.
- **New drawables**: `glass_card_elevated_bg`, `gradient_accent_bg`, `ripple_glass`, `bg_gradient`, `badge_bg`, `step_number_bg`, `cot_step_indicator`.
- **Back navigation** on History and Settings screens.
- **Alpha APK** now built as part of CI pipeline for early testing.

### Changed
- **MainActivity** completely redesigned as a 3-section Manus-style interface:
  - Top: header bar with app name, model status, status pill, history/settings buttons
  - Center: Agent Control panel with live CoT step list (replaces old text trace)
  - Bottom: Chat-style command input bar with voice and send button
- **History screen** redesigned with glassmorphism, back button, and improved empty state with icon.
- **Onboarding screen** converted from Material CardViews to glassmorphic glass cards with gradient CTA button.
- **Settings screen** converted to glassmorphism cards with back navigation.
- Error messages now appear as CoT steps (BLOCKED status) instead of raw text.
- Confirmation/cancellation actions append as visual steps in the CoT panel.
- Status bar is now fully transparent for immersive dark mode effect.
- Color palette refined with deeper backgrounds (`#0B1120`), expanded glass tones, and CoT-specific colors.

### Fixed
- Removed dead code references to `AgentOrchestrator`, `UpdateManager`, and unused imports from `MainActivity`.
- Merged all fixes from `fix/agent-crashes-v0.4.1` branch (13 commits) including test stabilization, named argument migrations, and CI fixes.

### CI/CD
- **Removed** `android-emulator.yml` — emulator tests always fail in CI due to unavailable AI model/GPU.
- **Updated** `ci.yml` to build both debug and alpha APKs with artifact uploads.
- Consolidated all branch work into `main`.

### Compatibility
- minSdk 28 (Android 9+), targetSdk 34
- All ABI filters: `armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64`

## [0.2.0] - 2026-04-07

### Added
- Material Design theme with deep indigo/teal brand colors
- Material Card-based UI for status, command input, and execution trace
- Voice input support via RecognizerIntent
- Execution history with JSON persistence (FIFO, max 50 entries)
- HistoryActivity with RecyclerView and tap-to-rerun
- Confirmation dialog flow for risky actions (calls, messages)
- NotificationListenerService for real notification summaries
- Onboarding activity with accessibility & notification permission setup
- Generated AI agent app icon
- CI/CD workflow: auto-publish APK to GitHub Releases on every push to main
- 31 new unit tests (ExecutionCoordinator, IntentExecutor, BrowserExecutor, AccessibilityExecutor)

### Changed
- AccessibilityExecutor now queries real notifications via AgentNotificationListener
- MainActivity redesigned with history button, voice input, emoji trace rendering
- Settings layout reorganized into sectioned cards (Primary, Fallback, Runtime)
- AndroidManifest updated with new activities, services, and custom theme

### Dependencies
- Added androidx.fragment:fragment-ktx:1.7.1
- Added androidx.recyclerview:recyclerview:1.3.2
- Added androidx.constraintlayout:constraintlayout:2.1.4

## [0.1.0] - Unreleased

- Professionalized repo governance, CI/CD, and Android agent runtime scaffolding
