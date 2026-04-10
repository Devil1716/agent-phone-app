# Changelog

All notable changes to this project will be documented in this file.

## [0.3.8] - 2026-04-10

### Added
- stricter prompt-to-action formatting for autonomous phone control
- package visibility queries for better installed-app resolution on modern Android
- background prewarm for the local MediaPipe Gemma runtime

### Changed
- autonomous command handling now explains when accessibility control or model runtime is unavailable instead of reporting false success
- multi-step commands can route into the autonomous path when the custom prompt asks for careful analysis
- local Gemma inference now reuses a cached engine and uses leaner generation settings for faster phone-control responses
- main screen now reports accessibility connection state and the Stop button cancels in-flight work

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
