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

## On-Device Model (Gemma 4)

The app runs **Gemma 4** fully on-device using the [Google AI Edge LiteRT](https://ai.google.dev/edge/litert) runtime (formerly MediaPipe).
No internet connection is required for inference once the model file is present.

### Model format

The app expects a **MediaPipe LiteRT task bundle** (`.task` file), **not** a raw `.gguf` or `.bin` file.
A task bundle is a ZIP archive containing the tokenizer and quantised TFLite weights.

### Getting the model

1. Accept the Gemma model terms on [Hugging Face](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm).
2. Generate a Hugging Face access token and paste it into **Settings → Hugging Face token** inside the app.
3. Tap **Download Gemma 4** — the app will download, verify (SHA-256), and cache the file automatically.

Alternatively, download the `.task` file manually and use **Import Gemma File** to load it from your device.

### Architecture

| Layer | Implementation |
|---|---|
| Prompt formatting | `GemmaPromptFormatter` — Gemma 4 instruct chat template (`<start_of_turn>`) |
| Inference engine | `GemmaInferenceEngine` — MediaPipe `LlmInference`, GPU→CPU fallback, temperature 0.1f |
| Model management | `GemmaModelManager` — resumable download, SHA-256 verification, import support |
| Agent loop | `AccessibilityAgentLoop` — 18-step sense-plan-act loop driven by Gemma 4 |
| Node grounding | `AccessibilityNodeGrounder` — resolves Gemma text labels to live accessibility nodeIds |

## Status

The current codebase contains:

- a configurable settings screen for model and relay selection
- a testable command input and execution trace UI
- a formalized agent runtime scaffold
- unit and instrumentation test coverage for the current supported behaviors

## Troubleshooting Installation

If you encounter an "**App not installed**" error when downloading the APK from GitHub Releases:

1. **Uninstall the previous version**: GitHub Actions generates a new signing key for each build. Android will not allow you to install an update over a version signed with a different key.
2. **Check your Android version**: The app requires Android 9 (API 28) or higher.
3. **Enable "Install from Unknown Sources"**: Ensure your browser or file manager has permission to install APKs.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) before opening a pull request.
