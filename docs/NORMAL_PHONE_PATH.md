# Normal Phone Path

## Goal

Build the first version as a normal Android app stack that runs on a regular Android phone.

This version should prove:

- voice-first control
- Gemma-first reasoning
- open-source-first model support
- agent planning
- app navigation through Android accessibility
- model switching without reflashing the phone

## What This Version Is

This is an app-based agent layer running on top of Android.

It is not yet a full custom ROM.

## Main Parts

- custom launcher
- accessibility service
- voice input
- Gemma-first model router
- open-source provider registry
- action executor
- settings for model and autonomy mode

## What It Can Do

- open apps
- navigate screens
- tap buttons
- type text
- summarize notifications
- start maps
- draft messages
- complete low-risk tasks

## What It Cannot Fully Do Yet

- replace the whole Android framework
- bypass normal Android permission limits
- do privileged OS-only actions on locked devices

## Why This Path Matters

This is the fastest way to build the product and test whether people actually want an agent-first phone.

It also becomes the prototype for the future full OS path.

## Phase 1 Deliverables

1. Android app starter
2. Gemma-first provider interface
3. task planner contract
4. accessibility executor skeleton
5. autonomy mode settings

## Preferred Model Stack

Use mostly open-source models:

- Gemma as the default
- Qwen as a strong alternate
- Llama as another supported family
- Phi as a lightweight fallback

Avoid depending on closed cloud APIs for the main experience.

## Later Transition To Full OS

Once this works well:

1. move the services into a privileged system app
2. integrate into AOSP
3. replace more of the stock phone experience
