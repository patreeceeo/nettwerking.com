#!/usr/bin/env python3

import re
import sys


def main() -> int:
    html = sys.stdin.read()
    status_match = re.search(r'data-test-status="([^"]+)"', html)
    fail_match = re.search(r'data-failures="([^"]+)"', html)
    error_match = re.search(r'data-errors="([^"]+)"', html)

    if not status_match:
        return 1

    status = status_match.group(1)
    failures = fail_match.group(1) if fail_match else "0"
    errors = error_match.group(1) if error_match else "0"

    print(f"frontend-test status={status} failures={failures} errors={errors}")
    return 0 if status == "passed" else 1


if __name__ == "__main__":
    raise SystemExit(main())
