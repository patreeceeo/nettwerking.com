.PHONY: help run repl frontend-watch frontend-build test lint fmt fmt-check

help:
	@printf "%s\n" \
		"Available targets:" \
		"  make run             Start the backend server" \
		"  make repl            Start a dev REPL for backend reload workflow" \
		"  make frontend-watch  Start shadow-cljs watch mode" \
		"  make frontend-build  Build frontend assets once" \
		"  make test            Run tests" \
		"  make lint            Run clj-kondo" \
		"  make fmt             Format source files" \
		"  make fmt-check       Check formatting without changing files"
		"  make update-lockfile Run this after updating deps.edn"

run:
	clojure -M:run

repl:
	clojure -M:dev

frontend-watch:
	clojure -M:frontend watch app

frontend-build:
	clojure -M:frontend compile app

test:
	clojure -M:test

lint:
	clojure -M:lint

fmt:
	clojure -M:fmt

fmt-check:
	clojure -M:fmt-check

update-lockfile:
	nix run github:jlesquembre/clj-nix#deps-lock

