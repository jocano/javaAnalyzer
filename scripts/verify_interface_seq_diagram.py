#!/usr/bin/env python3
"""
Smoke-test: sequence diagram does not stop at an abstract interface declaration.

Creates a minimal ``App -> Greeter (iface) -> GreeterImpl -> ping()`` setup in a
temporary tree, runs ``java-seq-diagram.py``, and asserts the PlantUML mentions
the concrete type, the ``(via Greeter)`` hop label, and a downstream call inside
the implementation.

Run from repo root:

    python3 scripts/verify_interface_seq_diagram.py

Requires: same optional deps as java-seq-diagram (tree-sitter-java for robustness).
"""
from __future__ import annotations

import re
import subprocess
import sys
import tempfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
TOOL = HERE / "java-seq-diagram.py"


def main() -> int:
    if not TOOL.is_file():
        print("error: expected", TOOL, file=sys.stderr)
        return 1

    src = """package com.ex;
public interface Greeter { void greet(); }
""".strip()
    impl = """package com.ex;
public class GreeterImpl implements Greeter {
    @Override
    public void greet() { ping(); }
    private void ping() {}
}
""".strip()
    app = """package com.ex;
public class App {
    private final Greeter greeter;
    public App(Greeter g) { this.greeter = g; }
    public void run() { greeter.greet(); }
}
""".strip()

    with tempfile.TemporaryDirectory(prefix="iface-seq-verify-") as td:
        root = Path(td) / "src/main/java/com/ex"
        root.mkdir(parents=True)
        (root / "Greeter.java").write_text(src + "\n", encoding="utf-8")
        (root / "GreeterImpl.java").write_text(impl + "\n", encoding="utf-8")
        (root / "App.java").write_text(app + "\n", encoding="utf-8")
        out_base = Path(td) / "out"
        cmd = [
            sys.executable,
            str(TOOL),
            str(root / "App.java"),
            "run",
            "--depth",
            "5",
            "--no-open",
            "--src-root",
            str(root.parent.parent),  # .../src/main/java (package root)
            "--out",
            str(out_base),
        ]
        proc = subprocess.run(cmd, capture_output=True, text=True)
        if proc.returncode != 0:
            print(proc.stdout, file=sys.stderr)
            print(proc.stderr, file=sys.stderr)
            return proc.returncode

        puml = out_base.with_suffix(".puml")
        if not puml.is_file():
            print("error: missing", puml, file=sys.stderr)
            return 1
        text = puml.read_text(encoding="utf-8")

        checks = [
            (r"GreeterImpl", "concrete implementor participant or target"),
            (r"\(via Greeter\)", "interface hop label on the arrow"),
            (r"greet\(\)", "method name preserved on the resolved edge"),
            (r"ping\(\)", "call inside implementation is traced"),
        ]
        for pat, desc in checks:
            if not re.search(pat, text):
                print("FAILED: expected", desc, "— pattern:", pat, file=sys.stderr)
                print(text, file=sys.stderr)
                return 2

        print("OK: interface call resolves to GreeterImpl; inner call to ping() appears.")
        return 0


if __name__ == "__main__":
    sys.exit(main())
