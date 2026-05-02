#!/usr/bin/env bash
# Open a file in IntelliJ from an idea:// URL (no OS protocol registration required).
#
# Usage:
#   ./idea-open-from-url.sh 'idea://open?file=/abs/path/File.java&line=26'
#
# Requires:
#   - python3
#   - IntelliJ command-line launcher named `idea` on PATH (Tools > Create Command-line Launcher…),
#     or set IDEA_BIN to the full path of the launcher.
#
# Exit codes: 0 ok, 2 bad URL scheme, 3 missing file=, 4 file missing, 5 no idea binary

exec python3 - "$@" <<'PY'
import os
import shutil
import subprocess
import sys
import urllib.parse


def main() -> int:
    if len(sys.argv) < 2:
        print(
            "Usage: idea-open-from-url.sh 'idea://open?file=/path&line=26'",
            file=sys.stderr,
        )
        return 1
    raw = sys.argv[1].strip()
    u = urllib.parse.urlsplit(raw)
    if u.scheme != "idea":
        print("Expected idea:// URL, got scheme: " + repr(u.scheme), file=sys.stderr)
        return 2
    q = urllib.parse.parse_qs(u.query, keep_blank_values=True)
    files = q.get("file", [])
    if not files:
        print("Missing file= in query string", file=sys.stderr)
        return 3
    path = urllib.parse.unquote(files[0])
    line_val = (q.get("line", [""])[0] or "").strip()

    if not os.path.isfile(path):
        print("File not found: " + path, file=sys.stderr)
        return 4

    idea = os.environ.get("IDEA_BIN") or shutil.which("idea") or shutil.which("idea64")
    if not idea:
        print(
            "No IntelliJ launcher found. Add `idea` to PATH (Tools > Create Command-line "
            "Launcher…) or set IDEA_BIN to the launcher path.",
            file=sys.stderr,
        )
        return 5

    if line_val.isdigit():
        subprocess.run([idea, "--line", line_val, path], check=False)
    else:
        subprocess.run([idea, path], check=False)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
PY
