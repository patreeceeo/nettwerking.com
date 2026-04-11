# DESIGN

## Purpose

This document captures the current design decisions for the Live Core Editor MVP.
It is not a full brand system. It is the minimum design contract needed to build
the first version consistently.

The product goal is to make programming feel like shaping live programmable
material, not editing text files. The interface should feel like a calm workshop,
not a dashboard and not a marketing page.

## Product Shape

- Single-screen app UI, not a multi-page flow
- Structural editor is the product, so it gets the dominant visual weight
- Result and status feedback stay visible at all times
- Actions stay close to the current selection
- The UI teaches itself with a starter task instead of a heavy tutorial

## Screen Hierarchy

The first screen should read in this order:

1. Orientation header
2. Structural editor workspace
3. Result and status pane
4. Selection-specific actions and hints

Use this layout shape as the default:

```text
+---------------------------------------------------------------+
| Live Core Editor                                              |
| Build expressions by shaping the tree. Starter: make (+ 2 3). |
+-----------------------------------+---------------------------+
|                                   | Result                    |
| Structural expression tree        | Status: ok/partial/error  |
|                                   | Current value / message   |
| Selected node actions appear      |                           |
| inline or in a tight side rail    |                           |
| next to the active selection      |                           |
|                                   |                           |
+-----------------------------------+---------------------------+
```

## First-Use Experience

The first session needs a fast first win. A new user should get to a visible
success in under 30 seconds.

Required first-use support:

- Compact persistent orientation header
- One-sentence explanation of what the product is
- One starter task or example expression
- A starter tree, never a blank workspace

Desired emotional arc:

```text
STEP | USER DOES                     | USER FEELS
-----|-------------------------------|-----------------------
1    | Lands on page                 | Curious, unsure
2    | Clicks a placeholder          | Tentative control
3    | Completes a simple expression | "Oh, it reacts live"
4    | Breaks part of the tree       | Slight doubt
5    | Recovers and keeps going      | Competent, engaged
```

## Interaction Model

- The selected node is always visually clear
- Actions are contextual and stay near the selected node
- A small persistent hint area explains what the current selection can do
- Do not use a large global toolbar
- Incomplete parts of the tree render as explicit interactive placeholders
- Placeholders should use action-oriented labels like `add value` or `choose function`

## State Design

The status area must always be in one of these visible modes:

- `first-run`
- `restored`
- `partial`
- `success`
- `error`

Every state must include:

- short explanation
- clear next step when relevant
- non-blaming language

Supportive language is required. Do not use cold failure copy like
`invalid expression` unless it is paired with recovery guidance.

State table:

```text
FEATURE              | LOADING | EMPTY | ERROR | SUCCESS | PARTIAL
---------------------|---------|-------|-------|---------|--------
Initial workspace    | none    | starter tree with prompt | corrupt session recovery message | restored session loaded | n/a
Expression tree      | none    | starter tree, not blank  | invalid action message near selection | complete tree with active selection | holes shown as interactive placeholders
Result/status pane   | n/a     | first-run guidance       | recoverable error with next action | current value + confirmation | partial explanation + suggested next edit
Selection actions    | n/a     | actions shown for starter selection | unavailable actions explain why | active actions shown inline | next valid actions biased toward filling holes
```

## Visual Direction

This is an app workspace. Treat it like a precise instrument.

Required direction:

- Calm workshop feel
- Editor-first layout
- Low chrome
- Strong typographic hierarchy
- Restrained light palette
- One accent color

Explicitly avoid:

- dashboard card mosaics
- purple or blue-purple gradients
- decorative blobs or floating shapes
- icon-in-colored-circle SaaS patterns
- bubbly radius on everything
- centered marketing-page composition

## Typography

Use only two type roles:

- Orientation and header: expressive serif or humanist display face
- Workspace, tree, result, and utility text: practical sans or mono pairing

Typography should do most of the hierarchy work. Do not rely on heavy borders,
filled cards, or decorative shadows to create structure.

## Color And Surface Tokens

Implementation should define these tokens before building the screen:

- `--color-background`
- `--color-surface`
- `--color-text`
- `--color-text-muted`
- `--color-accent`
- `--color-success`
- `--color-partial`
- `--color-error`
- `--color-selection`

Guidelines:

- light background by default
- strong text contrast
- muted color for secondary explanation only
- selection highlight must be visible without relying on color alone
- success, partial, and error states must look distinct

## Spacing And Shape

- Choose one base spacing unit and 3 to 4 multiples
- Use thin structural separators instead of thick boxes
- Use modest corner radius only where it helps interaction clarity
- Selection emphasis should come from contrast, stroke, and spacing, not inflated rounded UI

## Responsive Behavior

### Desktop

- Two-column layout
- Editor dominates left side
- Result and status stay visible on the right
- Orientation header stays compact

### Tablet

- Editor remains primary
- Result and status move below or into a compact secondary region
- Actions remain close to the current selection

### Mobile

- Single-column layout
- Header first, editor second, status third
- Result area may collapse to a compact summary
- Selection actions become a bottom action tray tied to the current node
- Minimum touch target size is 44px

Responsive does not mean "stack everything and hope." Each viewport should feel intentionally arranged.

## Accessibility

Accessibility is part of the interaction model.

Required behaviors:

- keyboard movement between parent, child, and sibling nodes
- visible focus ring that does not depend on color alone
- semantic labels for literals, symbols, calls, and holes
- clear selected-node announcement
- polite live region for status updates
- error and partial states not conveyed by color alone

The editor must communicate:

- what is selected
- what kind of node it is
- what the user can do next

## Voice And Copy

Copy should feel supportive and precise.

Do:

- explain what happened
- show what to do next
- keep copy short
- treat partial progress as progress

Do not:

- blame the user
- sound like a compiler error dump
- hide errors behind silence

## Out Of Scope For This Document

These are intentionally deferred:

- full brand system
- multi-view editor modes beyond the structural view
- heavy onboarding flow
- marketing-site design
- future named-definition workspace visuals

## Current Design Bar

If a proposed UI choice makes the product feel more like a generic SaaS app or
less like a live structural tool, reject it.

When in doubt:

- make the tree clearer
- make the current selection clearer
- make the next action clearer
- remove chrome before adding chrome
