# Architecture

See also: [Research Agent Automation](RESEARCH_AGENT_AUTOMATION.md)

## Runtime Layers

- Input layer for voice, text, and quick actions
- Goal interpretation and task planning
- Policy and confirmation enforcement
- Execution coordination
- Intent, accessibility, and browser executors
- Provider registry for local and relay-backed models

## Two-Speed Execution

- Fast path for common known flows
- Slow path for ambiguous or recovery-heavy flows

## Contracts

- `UserGoal`
- `TaskPlan`
- `TaskStep`
- `ScreenObservation`
- `PolicyDecision`
- `StepResult`
- `ExecutionTrace`
