# Normal Phone Path

## Goal

Ship a professional Android app that proves the agent architecture on normal phones before moving deeper into a custom ROM.

## Product Target

- general app control across common phone apps
- browser and web task support
- open-source-first model stack
- Gemma as the main model family
- hybrid local + laptop relay inference
- near-real-time fast path for common flows

## Quality Bar

- PR-only delivery
- CI + emulator automation
- physical-device release checklist
- internal alpha releases first
- explicit Android 12-14 support target

## Current Architecture

- settings and provider registry
- command input and execution trace UI
- goal interpretation and task planning
- policy engine and confirmation gates
- intent, browser, and accessibility executors

## What This Version Proves

- the agent can reason about a goal
- the app can classify low-risk vs confirm-required actions
- supported flows can launch real Android intents
- the runtime can be validated in CI and on emulator/device targets

## Next Stage

Once this path is stable, move the runtime contracts into privileged services in the OS repo.
