# Evaluator

This document defines the evaluator behavior for the Live Core Editor MVP.

It replaces the earlier normal-order sketch. The approved MVP uses eager
evaluation because it is smaller, easier to test, and easier to explain in
the UI.

## Scope

The evaluator is for the browser-only MVP.

It supports:

- literal values
- symbol references to built-in functions
- function application
- incomplete trees via explicit holes

It does not yet support:

- user-defined named definitions
- namespaces
- special forms
- macros
- lazy evaluation

## Design Goals

- Be explicit and predictable
- Never throw raw exceptions into the UI path
- Return structured results the UI can render directly
- Treat incomplete expressions as first-class, not as crashes

## Evaluation Strategy

The MVP uses eager evaluation.

That means:

1. Evaluate child expressions before applying a function
2. Resolve built-in symbols explicitly
3. If any child is incomplete, return a `:partial` result
4. If any child or application is invalid, return an `:error` result
5. Otherwise return `:success` with the computed value

## Result Shape

The evaluator should return structured data, not ad hoc values.

Suggested shape:

```clojure
{:kind :success | :partial | :error
 :value ...
 :message ...
 :reason ...
 :node-path ...}
```

Notes:

- `:kind` is required
- `:value` is present for successful evaluation
- `:message` is a short user-facing explanation or UI-ready summary
- `:reason` is a stable internal keyword for tests and branching
- `:node-path` can point at the subtree that caused a partial or error result

## AST Expectations

The evaluator expects the editor core to produce explicit node types.

Suggested MVP node kinds:

- `:literal`
- `:symbol`
- `:call`
- `:hole`

Suggested examples:

```clojure
{:type :literal :value 2}
{:type :symbol :name "+"}
{:type :hole :label "add value"}
{:type :call
 :fn '+
 :args [{:type :literal :value 2}
        {:type :literal :value 3}]}
```

## Built-ins

For the MVP, all callable functions are built-in and primitive.

Requirements:

- Built-ins are resolved from an explicit map
- Built-ins should be pure for the MVP
- Unknown symbols produce `:error`, not fallback behavior
- Wrong arity or invalid argument types produce `:error`

Suggested built-in table shape:

```clojure
{'+ {:apply (fn [args] ...)
     :min-arity 2}
 '* {:apply (fn [args] ...)
     :min-arity 2}}
```

## Partial Evaluation

Partial evaluation is a core product feature.

If any required subtree contains a hole, the evaluator should return `:partial`.

Examples:

- `(+ 2 ?)` -> `:partial`
- `(? 2 3)` -> `:partial`
- nested form with a hole in a descendant -> `:partial`

Partial means:

- the tree is still editable
- the UI should stay supportive
- the evaluator found no hard failure yet, only incomplete work

## Error Evaluation

Errors are real failures, not incomplete work.

Examples:

- unknown symbol
- wrong number of arguments
- invalid argument type
- malformed node shape

Errors must:

- return `:error`
- include a stable internal reason
- include a short message suitable for the status pane
- avoid leaking raw exception text to the user

## Evaluation Flow

```text
AST root
  │
  ▼
evaluate(node)
  │
  ├── literal -> success(value)
  ├── hole -> partial(reason = :incomplete)
  ├── symbol -> success(built-in-ref) or error(:unknown-symbol)
  └── call
      │
      ├── validate fn symbol
      ├── evaluate each arg eagerly
      ├── if any partial -> partial
      ├── if any error -> error
      ├── resolve built-in + validate arity + argument types
      └── apply built-in -> success(value) or error(:apply-failed)
```

## Relation To App State

The evaluator is one part of domain-state updates.

The command layer should:

1. apply the structural edit
2. update selection
3. run evaluation on the new tree
4. derive status from the result
5. return the full updated domain state

Suggested flow:

```text
User action
   │
   ▼
apply-command
   │
   ├── rewrite tree
   ├── update selection
   ├── evaluate root
   ├── derive status
   └── return full state
```

The evaluator should not know about DOM rendering or local storage.

## Testing Requirements

The evaluator lives in `.cljc` and should be covered primarily by JVM
`clojure.test`.

Minimum test cases:

- literal success
- built-in symbol resolution success
- nested form success
- nested call success
- hole produces `:partial`
- unknown symbol produces `:error`
- wrong arity produces `:error`
- invalid type produces `:error`
- malformed node produces `:error`
- evaluator never leaks raw exceptions

## Non-Goals For MVP

The evaluator is not trying to be a language runtime yet.

Do not add:

- lazy semantics
- custom user functions
- environments or scope chains beyond built-in lookup
- optimization passes
- incremental subtree caching

Keep it small. Keep it obvious.
