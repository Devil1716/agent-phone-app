# Changelog

All notable changes to this project will be documented in this file.

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
