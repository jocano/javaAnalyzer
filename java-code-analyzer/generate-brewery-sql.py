#!/usr/bin/env python3
"""
Parse brewery.json and emit INSERT statements that populate every table
defined in model-schema.sql (project, package, type, type_annotation,
type_implements, field, method, method_parameter, package_import,
spring_bean, spring_bean_stereotype, bean_injection).

Usage:
  python3 generate-brewery-sql.py
  # writes brewery-data.sql next to this script
"""

import json
import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).parent
JSON_FILE   = SCRIPT_DIR / "brewery.json"
OUT_FILE    = SCRIPT_DIR / "brewery-data.sql"
PROJECT_ROOT = "/Users/jcano/IdeaProjects/brewery"


def sq(value) -> str:
    """Wrap value in SQL single quotes, escaping embedded quotes. NULL for None."""
    if value is None:
        return "NULL"
    return "'" + str(value).replace("'", "''") + "'"

def num(value) -> str:
    """Emit integer literal or NULL."""
    if value is None:
        return "NULL"
    return str(int(value))


def main() -> None:
    data = json.loads(JSON_FILE.read_text(encoding="utf-8"))
    out  = []

    def emit(line: str = "") -> None:
        out.append(line)

    emit("-- ============================================================")
    emit("-- brewery-data.sql")
    emit("-- Generated from brewery.json by generate-brewery-sql.py")
    emit("-- Target schema: model-schema.sql")
    emit("-- ============================================================")
    emit()
    emit("BEGIN;")
    emit()

    # ── PROJECT ──────────────────────────────────────────────────────────
    emit("-- ── project ─────────────────────────────────────────────────")
    emit(
        f"INSERT INTO project (project_root, saved_at_millis, format_version) VALUES"
        f" ({sq(PROJECT_ROOT)}, {num(data.get('savedAtMillis', 0))}, {num(data.get('formatVersion', 1))});"
    )
    emit()

    # ── PACKAGE ──────────────────────────────────────────────────────────
    # Collect package names from three sources to avoid FK violations:
    #   1. packages[] array
    #   2. keys of packageImportDependencies
    #   3. values of packageImportDependencies
    #   4. packageName field on every type
    pkg_types: dict[str, list[str]] = {}
    for p in data.get("packages", []):
        pkg_types[p["name"]] = list(p.get("typeQualifiedNames", []))

    pid = data.get("packageImportDependencies", {})
    for k, vs in pid.items():
        pkg_types.setdefault(k, [])
        for v in vs:
            pkg_types.setdefault(v, [])

    for t in data.get("types", []):
        pkg_types.setdefault(t.get("packageName", ""), [])

    emit("-- ── package ─────────────────────────────────────────────────")
    for pname in pkg_types:
        emit(
            f"INSERT INTO package (qualified_name, project_root) VALUES"
            f" ({sq(pname)}, {sq(PROJECT_ROOT)});"
        )
    emit()

    # ── TYPE ─────────────────────────────────────────────────────────────
    # TypeKind: brewery.json uses CLASS / INTERFACE / ENUM; map ANNOTATION/RECORD if present
    valid_kinds = {"CLASS", "INTERFACE", "ENUM", "ANNOTATION", "RECORD"}

    emit("-- ── type ───────────────────────────────────────────────────")
    for t in data.get("types", []):
        kind = t.get("kind", "CLASS")
        if kind not in valid_kinds:
            kind = "CLASS"
        emit(
            f"INSERT INTO type"
            f" (qualified_name, simple_name, package_name, project_root, kind,"
            f"  extends_type, source_path, line_number) VALUES"
            f" ({sq(t['qualifiedName'])}, {sq(t['simpleName'])},"
            f"  {sq(t.get('packageName', ''))}, {sq(PROJECT_ROOT)}, {sq(kind)},"
            f"  {sq(t.get('extendsType'))}, {sq(t.get('sourcePath'))}, {num(t.get('lineNumber', 0))});"
        )
    emit()

    # ── TYPE_ANNOTATION ──────────────────────────────────────────────────
    emit("-- ── type_annotation ─────────────────────────────────────────")
    for t in data.get("types", []):
        qn = t["qualifiedName"]
        for ann in t.get("annotations", []):
            emit(
                f"INSERT INTO type_annotation (type_qualified_name, project_root, annotation_name) VALUES"
                f" ({sq(qn)}, {sq(PROJECT_ROOT)}, {sq(ann)});"
            )
    emit()

    # ── TYPE_IMPLEMENTS ──────────────────────────────────────────────────
    emit("-- ── type_implements ─────────────────────────────────────────")
    for t in data.get("types", []):
        qn = t["qualifiedName"]
        for iface in t.get("implementsTypes", []):
            emit(
                f"INSERT INTO type_implements (type_qualified_name, project_root, interface_name) VALUES"
                f" ({sq(qn)}, {sq(PROJECT_ROOT)}, {sq(iface)});"
            )
    emit()

    # ── FIELD ────────────────────────────────────────────────────────────
    emit("-- ── field ──────────────────────────────────────────────────")
    for t in data.get("types", []):
        qn = t["qualifiedName"]
        for fname, ftype in t.get("fieldsByName", {}).items():
            emit(
                f"INSERT INTO field (declaring_type, project_root, name, type_name) VALUES"
                f" ({sq(qn)}, {sq(PROJECT_ROOT)}, {sq(fname)}, {sq(ftype)});"
            )
    emit()

    # ── METHOD + METHOD_PARAMETER ─────────────────────────────────────────
    # method uses a BIGSERIAL PK; we provide explicit values so method_parameter
    # can reference them.  After loading, reset the sequence:
    #   PostgreSQL: SELECT setval(pg_get_serial_sequence('method','id'), MAX(id)) FROM method;
    #   H2:         ALTER SEQUENCE method_seq RESTART WITH <n+1>;
    emit("-- ── method ─────────────────────────────────────────────────")
    emit("-- ── method_parameter ────────────────────────────────────────")
    method_id = 1
    for t in data.get("types", []):
        qn = t["qualifiedName"]
        for vis, methods in [
            ("PUBLIC",    t.get("publicMethods",    [])),
            ("PROTECTED", t.get("protectedMethods", [])),
        ]:
            for m in methods:
                ret  = m.get("returnType") or "void"
                src  = m.get("sourcePath")
                mln  = m.get("lineNumber", 0)
                emit(
                    f"INSERT INTO method"
                    f" (id, declaring_type, project_root, name, visibility,"
                    f"  return_type_name, source_path, line_number) VALUES"
                    f" ({method_id}, {sq(qn)}, {sq(PROJECT_ROOT)}, {sq(m['name'])},"
                    f"  {sq(vis)}, {sq(ret)}, {sq(src)}, {num(mln)});"
                )
                for pos, ptype in enumerate(m.get("parameterTypes", [])):
                    emit(
                        f"INSERT INTO method_parameter (method_id, position, type_name) VALUES"
                        f" ({method_id}, {pos}, {sq(ptype)});"
                    )
                method_id += 1
    emit()
    emit(f"-- Reset method sequence after explicit-id inserts (last id = {method_id - 1}):")
    emit(f"-- PostgreSQL: SELECT setval(pg_get_serial_sequence('method','id'), {method_id - 1});")
    emit(f"-- H2:         ALTER SEQUENCE PUBLIC.METHOD_SEQ RESTART WITH {method_id};")
    emit()

    # ── PACKAGE_IMPORT ────────────────────────────────────────────────────
    emit("-- ── package_import ──────────────────────────────────────────")
    for from_pkg, to_list in pid.items():
        for to_pkg in to_list:
            emit(
                f"INSERT INTO package_import (from_package, to_package, project_root) VALUES"
                f" ({sq(from_pkg)}, {sq(to_pkg)}, {sq(PROJECT_ROOT)});"
            )
    emit()

    # ── SPRING_BEAN ───────────────────────────────────────────────────────
    spring       = data.get("springComponentGraph", {})
    analyzed_at  = spring.get("analyzedAtMillis", data.get("savedAtMillis", 0))

    emit("-- ── spring_bean ─────────────────────────────────────────────")
    for comp in spring.get("components", []):
        emit(
            f"INSERT INTO spring_bean"
            f" (type_qualified_name, project_root, analyzed_at_millis, source_path, line_number) VALUES"
            f" ({sq(comp['qualifiedName'])}, {sq(PROJECT_ROOT)}, {num(analyzed_at)},"
            f"  {sq(comp.get('sourcePath'))}, {num(comp.get('lineNumber', 0))});"
        )
    emit()

    # ── SPRING_BEAN_STEREOTYPE ────────────────────────────────────────────
    emit("-- ── spring_bean_stereotype ──────────────────────────────────")
    for comp in spring.get("components", []):
        qn = comp["qualifiedName"]
        for st in comp.get("stereotypes", []):
            emit(
                f"INSERT INTO spring_bean_stereotype (type_qualified_name, project_root, stereotype) VALUES"
                f" ({sq(qn)}, {sq(PROJECT_ROOT)}, {sq(st)});"
            )
    emit()

    # ── BEAN_INJECTION ────────────────────────────────────────────────────
    emit("-- ── bean_injection ──────────────────────────────────────────")
    inj_id = 1
    for edge in spring.get("injectionEdges", []):
        to_qn     = edge.get("toQualifiedName")
        to_simple = edge.get("toTypeSimpleName")
        # When only toQualifiedName is present (typical for fully resolved deps),
        # leave to_type_simple_name NULL.  External types keep their FQN in to_qualified_name.
        emit(
            f"INSERT INTO bean_injection"
            f" (id, from_qualified_name, project_root, to_qualified_name, to_type_simple_name, kind, qualifier) VALUES"
            f" ({inj_id}, {sq(edge['fromQualifiedName'])}, {sq(PROJECT_ROOT)},"
            f"  {sq(to_qn)}, {sq(to_simple)}, {sq(edge.get('kind', 'CONSTRUCTOR'))}, {sq(edge.get('qualifier'))});"
        )
        inj_id += 1
    emit()
    emit(f"-- Reset bean_injection sequence (last id = {inj_id - 1}):")
    emit(f"-- PostgreSQL: SELECT setval(pg_get_serial_sequence('bean_injection','id'), {inj_id - 1});")
    emit(f"-- H2:         ALTER SEQUENCE PUBLIC.BEAN_INJECTION_SEQ RESTART WITH {inj_id};")
    emit()

    emit("COMMIT;")

    text = "\n".join(out) + "\n"
    OUT_FILE.write_text(text, encoding="utf-8")

    types_count    = len(data.get("types", []))
    pkg_count      = len(pkg_types)
    method_count   = method_id - 1
    inj_count      = inj_id - 1
    bean_count     = len(spring.get("components", []))
    lines_written  = len(out)

    print(f"Written → {OUT_FILE}", file=sys.stderr)
    print(f"  packages        : {pkg_count}", file=sys.stderr)
    print(f"  types           : {types_count}", file=sys.stderr)
    print(f"  methods         : {method_count}", file=sys.stderr)
    print(f"  spring beans    : {bean_count}", file=sys.stderr)
    print(f"  injection edges : {inj_count}", file=sys.stderr)
    print(f"  SQL lines       : {lines_written}", file=sys.stderr)


if __name__ == "__main__":
    main()
