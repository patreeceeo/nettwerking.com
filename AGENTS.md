# AGENTS.md

This document defines the default conventions for agents working in this repository.

The project is a web application with:

- Clojure on the backend
- ClojureScript on the frontend
- A strong bias toward simplicity
- Minimal dependencies
- Good local developer experience

If a future user request conflicts with this document, follow the user request.

## Core Principles

- Prefer the smallest workable solution.
- Keep the dependency graph shallow and boring.
- Choose stable, well-supported libraries over fashionable ones.
- Avoid framework sprawl.
- Favor plain data and straightforward namespaces over abstraction-heavy designs.
- Optimize for fast local feedback.
- Do not introduce build complexity unless it removes more complexity elsewhere.

## Architecture Defaults

Unless the user requests otherwise, use these defaults:

- Backend: Ring-compatible HTTP stack with a small routing library.
- Frontend: ClojureScript with React interop through a stable, established wrapper only if needed.
- Shared code: Use `.cljc` only when there is an obvious benefit.
- Persistence: Do not introduce a database layer until the app actually needs one.
- API style: Prefer simple JSON endpoints over adding GraphQL or similar layers.
- State management: Keep frontend state local and simple before introducing a dedicated state framework.

## Dependency Policy

Dependencies must be justified. Before adding one, ask:

- Does the standard library already solve this?
- Can an existing dependency solve this cleanly?
- Is this library well-supported and stable?
- Does it reduce code and maintenance enough to be worth it?

Default rule:

- Prefer fewer libraries, even if it means writing a small amount of straightforward code.

Avoid by default:

- Large full-stack frameworks
- Experimental libraries
- Thin wrappers around simple browser or JVM capabilities
- Multiple libraries that solve the same problem

## Tooling Defaults

Use the official Clojure CLI and keep the setup simple.

Preferred tooling:

- Build/deps: `deps.edn`
- Backend dev workflow: `tools.namespace` for REPL-driven reloads
- Frontend build/dev: `shadow-cljs`
- Formatting: `cljfmt`
- Linting: `clj-kondo`
- Testing: `clojure.test`

Do not introduce Leiningen, Boot, or extra task runners unless the user explicitly asks for them.

## Developer Experience Requirements

Good DX is a hard requirement. Any project setup should support:

- Backend hot reloading during development
- Frontend hot reloading during development
- Fast startup for local development
- One obvious way to run the app
- One obvious way to run tests
- One obvious way to run linting
- One obvious way to format code

When scaffolding or editing project files, prefer a small set of memorable commands, typically exposed through `deps.edn` aliases.

## Backend Guidance

Default backend expectations:

- Run the server from the REPL in development.
- Support namespace reloading without restarting the whole process where practical.
- Keep request handlers small and data-oriented.
- Keep middleware minimal.
- Prefer explicit wiring over magic lifecycle frameworks unless the app becomes large enough to justify them.

If no better reason exists, favor:

- Ring
- Reitit or a similarly small, established routing library
- `org.clojure/tools.namespace` for reload support

Do not add component systems, interceptors, or dependency injection frameworks by default.

## Frontend Guidance

Default frontend expectations:

- Use `shadow-cljs` for compilation and frontend hot reload.
- Keep the client architecture simple.
- Prefer function components and plain immutable data.
- Avoid adding a complex frontend state framework unless the app clearly needs it.

If no better reason exists, favor:

- Reagent for a minimal, stable React wrapper

Do not add a UI component framework by default. Build a small amount of app-specific UI first.

## Formatting And Linting

These are required, not optional.

- All Clojure and ClojureScript code should be formatted with `cljfmt`.
- All code should pass `clj-kondo` before being considered complete.
- New code should follow existing namespace and file naming conventions.
- Keep formatting configuration minimal and conventional.

Agents should avoid introducing style churn unrelated to the task.

## Testing Expectations

- Use `clojure.test` unless the user asks for another test library.
- Add tests for behavior, not implementation details.
- Keep tests readable and close to the code they validate.
- Do not introduce browser-heavy frontend test tooling by default.

For simple UI logic, prefer testing pure functions before adding specialized UI test infrastructure.

## Project Layout

Unless there is a strong reason to change it, prefer a layout like:

```text
src/
  app/
    backend/
    frontend/
    shared/
resources/
test/
```

Keep naming direct and unsurprising.

## Command Conventions

When creating project automation, prefer `deps.edn` aliases such as:

- `:dev` for local development support
- `:build` only if a real build step is needed
- `:test` for test execution
- `:lint` for linting
- `:fmt` for formatting

Every command should have a clear purpose and minimal surprise.

## Change Guidelines For Agents

When making changes in this repo:

- Preserve simplicity.
- Do not add dependencies casually.
- Do not add architecture that anticipates hypothetical future scale.
- Keep configuration files small and readable.
- Prefer REPL-friendly code.
- Prefer pure functions where practical.
- Add brief comments only where the code would otherwise be hard to parse.

If you believe a new dependency or layer is necessary, state the tradeoff clearly in your final response.

## Non-Goals

Do not optimize for these unless explicitly requested:

- Microservices
- Complex event-driven architectures
- Plugin systems
- Premature background job infrastructure
- Advanced SSR setups
- Heavy CSS or JS build pipelines
- Multiple runtime environments beyond what the task needs

## Decision Rule

When several valid options exist, choose the one that is:

1. Simpler
2. More stable
3. Easier to reload in development
4. Easier for the next developer to understand
