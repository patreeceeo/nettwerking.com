.PHONY: help run repl frontend-watch frontend-build frontend-test frontend-test-watch frontend-test-build test test-watch lint fmt fmt-check

help:
	@printf "%s\n" \
		"Available targets:" \
		"  make run             Start the backend server" \
		"  make repl            Start a Rebel Readline dev REPL for backend reload workflow" \
		"  make frontend-watch  Start shadow-cljs watch mode" \
		"  make frontend-build  Build frontend assets once" \
		"  make frontend-test       Run frontend integration tests in headless Chromium" \
		"  make frontend-test-watch Watch frontend integration tests" \
		"  make frontend-test-build Build frontend integration tests once" \
		"  make test            Run tests" \
		"  make test-watch      Watch all tests during development" \
		"  make lint            Run clj-kondo" \
		"  make fmt             Format source files" \
		"  make fmt-check       Check formatting without changing files" \
		"  make update-lockfile Run this after updating deps.edn"

run:
	clojure -M:run

repl:
	clojure -M:dev -m rebel-readline.main

frontend-watch:
	clojure -M:frontend watch app

frontend-build:
	clojure -M:frontend compile app

frontend-test:
	clojure -M:frontend compile frontend-test
	@set -eu; \
	python3 -m http.server 8022 --directory target/frontend-tests >/tmp/frontend-test-server.log 2>&1 & \
	SERVER_PID="$$!"; \
	trap 'kill "$$SERVER_PID" >/dev/null 2>&1 || true' EXIT INT TERM; \
	sleep 1; \
	DOM_DUMP="$$(chromium --headless=new --no-sandbox --disable-gpu --disable-software-rasterizer --disable-dev-shm-usage --run-all-compositor-stages-before-draw --virtual-time-budget=5000 --dump-dom "http://127.0.0.1:8022/index.html")"; \
	printf "%s\n" "$$DOM_DUMP" | python3 dev/frontend_test_status.py

frontend-test-watch:
	clojure -M:frontend watch frontend-test

frontend-test-build:
	clojure -M:frontend compile frontend-test

test:
	clojure -M:test

test-watch:
	@(find src dev test resources -type f; printf "%s\n" deps.edn shadow-cljs.edn Makefile README.md) | entr -cdr sh -c 'clear; printf "==> make test\n"; make test; printf "\n==> make frontend-test\n"; make frontend-test'

lint:
	clojure -M:lint

fmt:
	clojure -M:fmt

fmt-check:
	clojure -M:fmt-check

update-lockfile:
	nix run github:jlesquembre/clj-nix#deps-lock
