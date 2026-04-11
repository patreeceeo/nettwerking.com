# TODOS

## Editor

### Add Named User Definitions And Namespaced References

**What:** Introduce user-defined named expressions and a namespace-style reference model after the structural editor MVP proves the core editing loop.

**Why:** The long-term product vision includes reusable named definitions, but the MVP deliberately defers naming, collisions, discovery, and persistence so the first version can validate structural editing and live feedback without extra language design work.

**Context:** The approved design for the Live Core Editor keeps v1 frontend-only, with literals, built-in symbol references, function application, explicit holes, and local session persistence. Once that core loop feels good, the next serious language step is letting users create named definitions and reference them predictably. Start by revisiting the AST, evaluator lookup rules, and persistence model chosen for the MVP, then decide whether names live in one local workspace or a broader namespace model.

**Effort:** L
**Priority:** P2
**Depends on:** Structural editor MVP shipped and validated

### Explore A Compact Expression View

**What:** Explore a secondary compact expression view after the single structural view proves understandable and usable.

**Why:** A compact view could make larger expressions easier to scan, but adding multiple representations before validating the primary structural view would muddy the MVP and hide whether the main interaction model actually works.

**Context:** The current plan intentionally commits to one screen with a tree editor region, explicit selection affordances, and a live result/status pane. That is the right first bet. If users understand and enjoy the structural version, the next usability question is whether a denser expression-style presentation helps with navigation, comparison, or editing speed without reintroducing text-editor problems. Start by observing pain points in the single-view MVP before designing any toggle or hybrid layout.

**Effort:** M
**Priority:** P3
**Depends on:** Structural editor MVP shipped and observed in use

### Add Undo And Redo On Top Of The Command Layer

**What:** Add undo/redo once the MVP command set and editor state model have settled.

**Why:** Undo is one of the first safety features users expect in an editor, but it depends heavily on the final shape of commands, selection updates, and state transitions. Building it too early risks hard-coding history mechanics around unstable operations.

**Context:** The review locked in a single editor command layer that applies structural operations like insert, replace, wrap, delete, and selection movement. That is the right seam for future history support. After the MVP ships, review how commands are represented and whether storing prior editor states or inverse operations is the simpler fit. Keep the first version boring and reliable rather than designing a generalized event-sourcing system nobody asked for.

**Effort:** M
**Priority:** P3
**Depends on:** Command layer implemented and stable

## Completed
