# Contributing

## Workflow

- Create a topic branch from `main`
- Open a pull request instead of pushing directly to `main`
- Include test evidence for unit tests, emulator tests, and any device validation performed
- Keep changes scoped and explain user impact and risk in the PR

## Quality Bar

- Android CI must pass
- Emulator workflow must pass for behavior-changing changes
- New runtime behavior should include unit tests when practical
- UI changes should include instrumentation coverage when practical

## Safety

- Do not add silent destructive automation
- Do not weaken confirmation rules for risky actions without explicit design approval
- Do not commit secrets, keystores, or local credentials

## Releases

- Development merges target future alpha releases
- Tagged releases follow the internal alpha process described in [docs/RELEASE_PROCESS.md](C:\Users\DaRkAngeL\Desktop\os\repos\agent-phone-app\docs\RELEASE_PROCESS.md)
