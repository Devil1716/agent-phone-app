# Gemma Agent Phone App

[![Android CI](https://github.com/Devil1716/agent-phone-app/actions/workflows/ci.yml/badge.svg)](https://github.com/Devil1716/agent-phone-app/actions/workflows/ci.yml)
[![Android Emulator](https://github.com/Devil1716/agent-phone-app/actions/workflows/emulator.yml/badge.svg)](https://github.com/Devil1716/agent-phone-app/actions/workflows/emulator.yml)
[![Dependency Scan](https://github.com/Devil1716/agent-phone-app/actions/workflows/dependency-scan.yml/badge.svg)](https://github.com/Devil1716/agent-phone-app/actions/workflows/dependency-scan.yml)

Gemma Agent Phone App is a public Android project for building a general app-control agent that can act across common phone apps, browser flows, and system screens with visible execution tracing and safety checks.

## Product Direction

- Android 12-14 support target
- open-source-first model strategy
- Gemma as the primary model family
- hybrid local + laptop relay inference
- near-real-time fast path for common supported flows
- slower recovery path for ambiguous or complex app navigation

## Repository Standards

- PR-only delivery into `main`
- required CI, emulator, and security checks
- internal alpha release flow before wider public rollout
- explicit device matrix and physical-device checklist
- community docs and security reporting guidance

## Project Layout

- [android](android): Android application and tests
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md): runtime architecture
- [docs/AUTOMATION_SAFETY.md](docs/AUTOMATION_SAFETY.md): safety and confirmation rules
- [docs/MODEL_PROVIDERS.md](docs/MODEL_PROVIDERS.md): model/provider strategy
- [docs/DEVICE_MATRIX.md](docs/DEVICE_MATRIX.md): compatibility and release gate devices
- [docs/PHYSICAL_DEVICE_CHECKLIST.md](docs/PHYSICAL_DEVICE_CHECKLIST.md): manual release checklist
- [docs/RELEASE_PROCESS.md](docs/RELEASE_PROCESS.md): alpha and tagged release flow
- [docs/LABELS_AND_MILESTONES.md](docs/LABELS_AND_MILESTONES.md): issue/PR triage conventions

## CI/CD

The app repo uses a multi-stage workflow set:

- [ci.yml](.github/workflows/ci.yml): lint, unit tests, build
- [emulator.yml](.github/workflows/emulator.yml): emulator instrumentation tests
- [dependency-scan.yml](.github/workflows/dependency-scan.yml): dependency review and CodeQL
- [release-alpha.yml](.github/workflows/release-alpha.yml): signed alpha builds and tagged releases

## Local Setup

1. Install Android Studio with Android SDK 34.
2. Install JDK 17.
3. Run `android/gradlew testDebugUnitTest`.
4. Run `android/gradlew connectedDebugAndroidTest` with an emulator or device connected.

## Status

The current codebase contains:

- a configurable settings screen for model and relay selection
- a testable command input and execution trace UI
- a formalized agent runtime scaffold
- unit and instrumentation test coverage for the current supported behaviors

## Troubleshooting Installation

If you encounter an "**App not installed**" error when downloading the APK from GitHub Releases:

1. **Remove any old debug or differently signed side-load once**: current published alpha/release updates are signed with a consistent release key, but Android will reject installs over older builds that used a different package name or signing key.
2. **Check your Android version**: The app requires Android 9 (API 28) or higher.
3. **Enable "Install from Unknown Sources"**: Ensure your browser or file manager has permission to install APKs.

## Update Channel

- signed GitHub alpha releases now keep the same installable package name as the production channel so future updates do not hit package-name conflicts
- debug builds use the separate `.debug` package and do not participate in in-app updates

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) before opening a pull request.
