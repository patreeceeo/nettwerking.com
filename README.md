# Live Core Editor

This project aims to make programming feel less like editing source files and
more like shaping live programmable material.

The first wedge is a browser-native Lisp environment with a structural editor:
users build expressions as editable trees, see immediate feedback as they
change them, and never have to drop into raw text syntax just to try an idea.

Today the codebase is still a small Clojure + ClojureScript scaffold, with:

- Clojure backend
- ClojureScript frontend
- REPL-driven backend reloads
- `shadow-cljs` frontend hot reload
- `clj-kondo` linting
- `cljfmt` formatting
- `clojure.test`

The project favors a small dependency set and a simple local workflow.

## Requirements

- Java 21 or newer
- Clojure CLI

No Node.js or npm setup is required for the current frontend scaffold.

Note: There's a lockfile that needs to be updated after adding dependencies. See Makefile.

## Commands

Use `make help` to list the available targets.

## Development

For normal development, run two processes:

1. `make repl` for backend work
2. `make frontend-watch` for frontend compilation and reload

At the REPL:

```clojure
(dev/go)
(dev/reset)
(dev/stop)
```

Open `http://localhost:8080`.

Backend reload is explicit through `tools.namespace` via `(dev/reset)`. Frontend reload is automatic through `shadow-cljs` when files under `src/app/frontend` change.

If you do not need backend reloads, use `make run` instead of `make repl`.

The frontend tooling is configured around a modern JVM. A Java 25 environment is expected to work.

## Current App

- `/` serves `resources/public/index.html`
- `/api/health` returns a small JSON health response

The frontend build is named `app` and writes assets to `resources/public/js`.

At the moment, the browser UI is still the starter scaffold. The editor MVP is
planned and documented, but not implemented yet.

## Layout

```text
.
├── AGENTS.md
├── DESIGN.md
├── ENG_REVIEW.md
├── EVALUATOR.md
├── Makefile
├── README.md
├── TODOS.md
├── deps.edn
├── shadow-cljs.edn
├── dev
│   ├── app/backend/dev.clj
│   └── user.clj
├── resources
│   └── public/index.html
├── src
│   └── app
│       ├── backend
│       │   ├── main.clj
│       │   └── server.clj
│       └── frontend
│           └── app.cljs
└── test
    └── app
        ├── backend/server_test.clj
        └── test_runner.clj
```

## Notes

- `Makefile` is the main command entrypoint for local development.
- `deps.edn` contains the underlying aliases used by the make targets.
- The `:frontend` alias pins the `shadow-cljs` toolchain versions it expects to avoid dependency drift.
- `dev/user.clj` loads the common backend REPL helpers.
- `.clj-kondo/config.edn` and `.cljfmt.edn` keep linting and formatting configuration minimal.
- `DESIGN.md`, `EVALUATOR.md`, and `ENG_REVIEW.md` should be treated as the current implementation guide for the Live Core Editor MVP.

## Status

Verified with:

```bash
make test
```
