# Agent Phone App

This is the normal-phone path for the project.

It is meant to run on a regular Android device as an app-based agent layer.

The model strategy for this repo is open-source-first.

## Scope

- custom Android app stack
- Gemma-first agent flow
- open-source local model support
- accessibility-driven navigation
- voice-first interaction
- settings for model and autonomy mode

## Model Direction

Primary preference:

- `Gemma` as the main model family

Other preferred open-source options:

- `Qwen`
- `Llama`
- `Phi`

Preferred runtimes:

- on-device runtime integration
- `llama.cpp`
- `Ollama` when local service mode is acceptable

## Main Project

- [android](C:\Users\DaRkAngeL\Desktop\os\repos\agent-phone-app\android)

## Docs

- [docs/NORMAL_PHONE_PATH.md](C:\Users\DaRkAngeL\Desktop\os\repos\agent-phone-app\docs\NORMAL_PHONE_PATH.md)

## Git Recommendation

This folder should be its own GitHub repository.

Example:

```bash
cd repos/agent-phone-app
git init
git remote add origin <your-app-repo-url>
git add .
git commit -m "Initial Android agent app"
git push -u origin main
```
