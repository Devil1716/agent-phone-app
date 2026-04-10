# Release Process

## Release Types

- debug builds for development
- internal alpha builds for gated testing
- tagged releases for stable alpha milestones

## Alpha Requirements

- all required CI checks pass
- emulator workflow passes
- manual device checklist completed
- changelog updated
- signed artifacts generated from GitHub Actions secrets

## Guarded Alpha Publishing

- run `Alpha Release` via GitHub Actions `workflow_dispatch`
- provide a new `tag_name` input such as `v0.3.4-alpha`
- the workflow runs `lintDebug`, `testDebugUnitTest`, and `assembleDebug` first
- the tag is created and pushed only after all checks pass
- release artifacts are published only after successful tag creation
