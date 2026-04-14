# Changelog

All notable changes to this project will be documented in this file.

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
