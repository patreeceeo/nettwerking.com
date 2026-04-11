# Engineering Review

This document captures the engineering decisions from the plan engineering review
for the Live Core Editor MVP.

It is the implementation-facing counterpart to [DESIGN.md](./DESIGN.md) and
[EVALUATOR.md](./EVALUATOR.md).

## Scope Decision

The MVP scope is accepted as-is with one strong constraint:

- keep the implementation browser-only
- use one `.cljc` core plus a thin `.cljs` shell
- do not add server APIs, accounts, sharing, user-defined globals, or view-mode toggles

## What Already Exists

Existing code and flows the MVP should reuse:

- [src/app/backend/server.clj](./src/app/backend/server.clj)
  - already serves the app shell at `/`
  - already serves static frontend assets
- [src/app/frontend/app.cljs](./src/app/frontend/app.cljs)
  - already provides the browser entrypoint
- [deps.edn](./deps.edn)
  - already provides JVM-side `clojure.test`
- [test/app/test_runner.clj](./test/app/test_runner.clj)
  - already provides a working test runner pattern
- [README.md](./README.md)
  - already documents the basic development workflow

There is almost no reusable frontend interaction logic yet. The current frontend
is a scaffold, not an existing editor architecture.

## Architecture Decisions

### 1. Browser-only MVP

The MVP stays in the browser.

Rationale:

- smallest thing that proves the product thesis
- avoids adding backend APIs and persistence complexity too early
- keeps the user loop immediate

### 2. Eager Evaluation

The MVP uses eager evaluation, not normal-order evaluation.

Rationale:

- easier to reason about
- easier to test
- aligns better with visible `success` / `partial` / `error` UI states

See [EVALUATOR.md](./EVALUATOR.md).

### 3. Single `.cljc` Core

The core domain logic should live in `.cljc`.

Core responsibilities:

- AST shape
- command application
- selection movement
- command availability
- evaluation
- status derivation

Browser shell responsibilities:

- DOM rendering
- event wiring
- `localStorage` integration
- copy mapping for statuses and labels

### 4. Single Domain State

Every command should return one full domain state, not partial fragments.

Suggested shape:

```clojure
{:root ...
 :selection ...
 :eval ...
 :status ...
 :storage ...}
```

Exact keys can vary, but the principle stands:

- tree state
- selection state
- evaluation result
- visible status

must move together.

If these drift apart, the UI will show stale or contradictory feedback.

### 5. Command Layer Owns Availability Rules

The same logic that applies commands should also answer:

- what actions are available for the current selection
- why an action is unavailable

Do not duplicate this logic in render code.

## Implementation Shape

Smallest sane implementation:

- one `.cljc` editor/core namespace
- one additional `.cljc` evaluator namespace only if it keeps the core clearer
- one upgraded `.cljs` frontend shell
- one or two JVM test namespaces for the core
- one small browser integration test layer

Avoid:

- many tiny namespaces
- command bus machinery
- backend eval endpoints
- generalized plugin or runtime architecture

## Data Flow

```text
User action
   │
   ▼
Browser event handler (.cljs)
   │
   ▼
apply-command (.cljc)
   │
   ├── rewrite AST
   ├── update selection
   ├── compute available actions
   ├── evaluate tree
   ├── derive visible status
   └── return full domain state
   │
   ▼
render UI (.cljs)
   │
   └── debounce save to localStorage
```

## Code Quality Decisions

### 1. Minimal Namespace Split

Keep the core small and explicit.

Recommended shape:

- one main editor/domain namespace
- one separate evaluator namespace only if it keeps responsibilities clearer

Do not:

- collapse everything into a giant catch-all namespace
- split into lots of tiny future-proofed namespaces

### 2. Structured Status, Centralized Copy

The core should return explicit status values such as:

- `:first-run`
- `:restored`
- `:partial`
- `:success`
- `:error`

The browser shell should map those structured statuses to user-facing copy in
one place.

Do not scatter status strings across many render branches.

### 3. Explicit Invalid-Action Reasons

Invalid actions should return explicit reasons, not silent no-ops.

The UI should be able to explain:

- why an action is unavailable
- what the user can do next

## Test Strategy

### Test Framework Decision

The primary test path is JVM `clojure.test`.

Why:

- it already exists
- it is the cheapest path to deep branch coverage
- the approved architecture puts most logic in `.cljc`

### Coverage Split

Use JVM tests for branch coverage.

Use browser integration tests only for visible user loops.

Recommended split:

- JVM unit tests: most command/eval/state coverage
- browser integration tests: 5 focused tests

### Required JVM Coverage

The plan should cover at minimum:

- starter state creation
- restored-state load/fallback behavior
- available actions for literals, holes, root, invalid selection
- insert, replace, wrap, delete
- parent/child/sibling selection movement
- invalid command handling
- eager evaluator success paths
- hole-driven `partial` paths
- error paths for unknown symbol, wrong arity, invalid type, malformed node
- status derivation for all visible status modes
- guarantee that no raw evaluator exception leaks to the UI

### Required Browser Integration Coverage

Five focused browser tests:

1. First-use render with orientation header, starter tree, and starter task
2. Placeholder fill path that reaches visible success
3. Editing path that creates a partial state and then recovers
4. Session restore path on reload
5. Keyboard navigation plus visible focus and announced status/selection

### Coverage Diagram

```text
CODE PATH COVERAGE
===========================
[+] Core domain state + command application (.cljc)
    │
    ├── initial-state()
    ├── available-actions(state)
    ├── apply-command(state, command)
    ├── evaluate(root)
    └── derive-status/domain-update

[+] Browser shell (.cljs)
    │
    ├── render orientation header
    ├── render tree + placeholders
    ├── render status/result pane
    └── storage integration

USER FLOW COVERAGE
===========================
[+] First-use flow
[+] Editing flow
[+] Persistence + recovery flow
[+] Accessibility-visible flow
```

At review time, there were 34 explicit gaps. They are all accounted for by the
approved test strategy above.

## Performance Decisions

### 1. Immediate Render And Evaluation

Render and evaluation should happen immediately on every command.

The product thesis depends on immediate visible feedback.

### 2. Debounced Persistence

`localStorage` writes should be debounced and kept off the critical interaction path.

Do not save synchronously after every command.

### 3. Full Re-render For V1

For the MVP, re-render the visible tree from full state on each command.

Do not add:

- selective subtree diffing
- memoization layers
- custom rendering optimization infrastructure

until measurement proves it matters.

## Failure Modes

These failure modes must be treated as first-class during implementation:

### Invalid Or Stale Selection Path

Risk:

- user sees impossible actions
- focus moves unpredictably
- command handling breaks trust

Plan response:

- core validates selection paths
- invalid actions return explicit reasons
- tests cover invalid and root-edge selections

### Corrupt Saved State

Risk:

- app boots into a broken or blank state
- failure becomes silent and spooky

Plan response:

- safe load fallback
- visible recovery message
- tests for corrupt saved state and unavailable storage

### Status/Result Drift

Risk:

- UI shows stale success or stale values after newer edits

Plan response:

- one full domain state returned by command application
- evaluation and status derived together
- tests assert end-to-end state consistency

### Keyboard Navigation Mismatch

Risk:

- visible selection and actual focus diverge
- accessibility support becomes fake

Plan response:

- core owns selection movement rules
- browser tests cover focus and selection-visible behavior

## Not In Scope

These items were considered and explicitly deferred:

- user-defined global names and namespace addressing
  - too much language and persistence design for the MVP
- compact secondary expression view
  - wait until the single structural view proves itself
- undo/redo
  - wait until the command and state model settle
- backend eval or persistence APIs
  - browser-only MVP is still the right scope
- render optimization
  - full re-render is fine until measurement proves otherwise
- heavy onboarding flow
  - the approved design uses a starter task and self-teaching screen instead

## Parallelization Strategy

### Dependency Table

| Step | Modules touched | Depends on |
|------|----------------|------------|
| Core domain state + evaluator | `src/app/core/` | — |
| Command layer + action availability | `src/app/core/` | Core domain state + evaluator |
| Browser shell rendering + storage | `src/app/frontend/` | Core domain state + command layer |
| JVM unit tests | `test/app/` | Core domain state + command layer |
| Browser integration tests | `test/` plus frontend test setup | Browser shell rendering + storage |

### Parallel Lanes

- Lane A: core domain state + evaluator -> command layer -> JVM unit tests
- Lane B: browser shell rendering + storage
- Lane C: browser integration tests

### Execution Order

1. Start Lane A first
2. Start Lane B once the domain-state contract is agreed
3. Start Lane C after Lane B is stable enough to test real flows

### Conflict Flags

- Lane A and Lane B both depend on the exact domain-state shape
- Lane B and Lane C both touch frontend behavior and test affordances

## Review Totals

- Step 0: scope accepted as-is
- Architecture Review: 3 issues found
- Code Quality Review: 3 issues found
- Test Review: diagram produced, 34 gaps identified
- Performance Review: 2 issues found
- TODO updates proposed: 0
- Critical gaps flagged: 0
- Outside voice: skipped
- Lake score: 10/10 complete options chosen

## Files That Should Carry Inline ASCII Diagrams

If implementation introduces enough complexity, these are the first places that
should get inline diagrams:

- the main `.cljc` command/state namespace
  - command flow and state update pipeline
- the evaluator namespace
  - evaluation flow and result branching
- browser integration tests
  - setup/interaction/result sequence when test structure becomes non-obvious

Keep diagrams updated. Stale diagrams are worse than none.
