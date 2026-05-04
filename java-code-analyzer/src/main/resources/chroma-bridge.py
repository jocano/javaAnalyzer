#!/usr/bin/env python3
"""
ChromaDB bridge — called by the java-code-analyzer JAR to persist and query
an AnalyzerSnapshot using the chromadb Python client.

The Python client computes embeddings locally (all-MiniLM-L6-v2 via ONNX),
then sends embeddings + documents to ChromaDB. This avoids the need for Java
to do any vector math.

Collections managed:
  java-types   — one document per class / interface / enum
  java-beans   — one document per Spring bean
  java-wiring  — one document per directed relationship (DI injection,
                  inheritance, interface implementation, field dependency,
                  package import dependency)

Commands (JSON AnalyzerSnapshot piped to stdin; JSON result to stdout):
  upsert      <chroma_url>
  query-types  <chroma_url> <project_root> <text> <n>
  query-beans  <chroma_url> <project_root> <text> <n>
  query-wiring <chroma_url> <project_root> <text> <n>
"""
import json, re, sys
from urllib.parse import urlparse

import chromadb
from chromadb.utils.embedding_functions import DefaultEmbeddingFunction

COLLECTION_TYPES  = "java-types"
COLLECTION_BEANS  = "java-beans"
COLLECTION_WIRING = "java-wiring"
BATCH_SIZE        = 100
EF                = DefaultEmbeddingFunction()


# ── Client & collection helpers ───────────────────────────────────────────────

def get_client(url: str):
    p = urlparse(url)
    return chromadb.HttpClient(host=p.hostname, port=p.port or 8001)


def ensure_collection(client, name: str):
    try:
        return client.get_collection(name, embedding_function=EF)
    except Exception:
        return client.create_collection(name, embedding_function=EF)


def doc_id(project_root: str, *parts: str) -> str:
    prefix = re.sub(r"[^a-zA-Z0-9._-]", "-",
                    project_root.replace("\\", "/").split("/")[-1])
    body = ":".join(str(p) for p in parts)
    full = f"{prefix}:{body}"
    return full[:512]   # ChromaDB name limit is 512 chars


def delete_project(col, root: str):
    try:
        col.delete(where={"project_root": {"$eq": root}})
    except Exception:
        pass  # empty collection raises in some ChromaDB versions


def upsert_batch(col, ids, docs, metas):
    if ids:
        col.upsert(ids=ids, documents=docs, metadatas=metas)


# ── Build lookup tables ───────────────────────────────────────────────────────

def build_lookups(snapshot: dict):
    """
    Returns:
      type_info   : qn → TypeInfo dict
      stereotypes : qn → comma-joined stereotypes string (for Spring beans)
      simple_to_qn: simpleName → [qn, ...]  (for resolving unqualified names)
    """
    type_info: dict   = {}
    stereotypes: dict = {}
    simple_to_qn: dict = {}

    for t in snapshot.get("types", []):
        qn = t["qualifiedName"]
        type_info[qn] = t
        simple_to_qn.setdefault(t.get("simpleName", ""), []).append(qn)

    graph = snapshot.get("springComponentGraph", {})
    for comp in graph.get("components", []):
        qn = comp["qualifiedName"]
        stereotypes[qn] = ",".join(comp.get("stereotypes", []))

    return type_info, stereotypes, simple_to_qn


def resolve_qn(name: str, simple_to_qn: dict) -> str:
    """Try to resolve a simple or partially qualified name to a FQN."""
    if not name:
        return name
    simple = name.split(".")[-1]
    candidates = simple_to_qn.get(simple, [])
    return candidates[0] if len(candidates) == 1 else name


def stereo_label(qn: str, type_info: dict, stereotypes: dict) -> str:
    """Returns a human-readable label: stereotype (if Spring bean) or Kind."""
    s = stereotypes.get(qn)
    if s:
        return s.replace(",", "/")
    t = type_info.get(qn)
    if t:
        return t.get("kind", "CLASS")
    return "TYPE"


def simple_name(qn: str) -> str:
    return qn.split(".")[-1] if qn else qn


# ── Type documents (enriched with relationship context) ───────────────────────

def type_document(t: dict, type_info: dict, stereotypes: dict,
                  injects_map: dict, injected_by_map: dict) -> str:
    qn   = t["qualifiedName"]
    kind = t.get("kind", "CLASS")
    lines = []

    # Header: stereotype (if bean) + kind + name
    stereo = stereotypes.get(qn, "")
    header = f"{stereo + ' ' if stereo else ''}{kind} {qn}"
    lines.append(header)

    if t.get("annotations"):
        lines.append("annotations: " + ", ".join(t["annotations"]))
    if t.get("extendsType"):
        lines.append("extends: " + t["extendsType"])
    if t.get("implementsTypes"):
        lines.append("implements: " + ", ".join(t["implementsTypes"]))

    # Fields — show type name for each field
    fields = t.get("fieldsByName", {})
    if fields:
        lines.append("fields: " + ", ".join(
            f"{v} {k}" for k, v in fields.items()))

    # DI wiring context (enrichment)
    out = injects_map.get(qn, [])
    inn = injected_by_map.get(qn, [])
    if out:
        out_labels = [f"{stereo_label(to, type_info, stereotypes)} {simple_name(to)}"
                      for to in out]
        lines.append("injects: " + ", ".join(out_labels))
    if inn:
        inn_labels = [f"{stereo_label(frm, type_info, stereotypes)} {simple_name(frm)}"
                      for frm in inn]
        lines.append("injected by: " + ", ".join(inn_labels))

    # Methods
    all_methods = t.get("publicMethods", []) + t.get("protectedMethods", [])
    if all_methods:
        sigs = [m["name"] + "(" + ", ".join(m.get("parameterTypes", [])) + ")"
                for m in all_methods]
        lines.append("methods: " + ", ".join(sigs))

    return "\n".join(lines)


# ── Bean documents ─────────────────────────────────────────────────────────────

def bean_document(comp: dict, type_info: dict, stereotypes: dict,
                  injects_map: dict, injected_by_map: dict) -> str:
    qn     = comp["qualifiedName"]
    stereo = ",".join(comp.get("stereotypes", []))
    lines  = [f"{stereo} {qn}" if stereo else qn]

    # Method signatures from full type info
    t = type_info.get(qn, {})
    if t.get("annotations"):
        lines.append("annotations: " + ", ".join(t["annotations"]))

    out = injects_map.get(qn, [])
    inn = injected_by_map.get(qn, [])
    if out:
        out_labels = [f"{stereo_label(to, type_info, stereotypes)} {simple_name(to)}"
                      for to in out]
        lines.append("injects: " + ", ".join(out_labels))
    if inn:
        inn_labels = [f"{stereo_label(frm, type_info, stereotypes)} {simple_name(frm)}"
                      for frm in inn]
        lines.append("injected by: " + ", ".join(inn_labels))

    all_methods = t.get("publicMethods", []) + t.get("protectedMethods", [])
    if all_methods:
        sigs = [m["name"] + "(" + ", ".join(m.get("parameterTypes", [])) + ")"
                for m in all_methods[:20]]   # cap at 20 methods
        lines.append("methods: " + ", ".join(sigs))

    return "\n".join(lines)


# ── Wiring documents ──────────────────────────────────────────────────────────

def wiring_documents(snapshot: dict, type_info: dict,
                     stereotypes: dict, simple_to_qn: dict):
    """
    Yields (id, document, metadata) tuples for every relationship found in
    the snapshot: DI injection, inheritance, interface implementation, field
    dependency, and package import dependency.
    """
    root  = snapshot["projectRoot"]
    graph = snapshot.get("springComponentGraph", {})

    # ── 1. Spring DI injection edges ─────────────────────────────────────────
    for e in graph.get("injectionEdges", []):
        frm    = e.get("fromQualifiedName", "")
        to_qn  = e.get("toQualifiedName") or ""
        to_s   = e.get("toTypeSimpleName") or ""
        kind_s = (e.get("kind") or "CONSTRUCTOR").lower().replace("_", " ")

        to_display = to_qn or to_s
        to_label   = stereo_label(to_display, type_info, stereotypes)
        frm_label  = stereo_label(frm, type_info, stereotypes)

        doc = (
            f"{frm_label} {simple_name(frm)} uses {to_label} {simple_name(to_display)}"
            f" [{kind_s} injection]\n"
            f"{simple_name(frm)} depends on {simple_name(to_display)}\n"
            f"wiring: {frm_label.lower()} → {to_label.lower()}"
        )
        meta = {
            "project_root":     root,
            "relation":         "injects",
            "injection_kind":   kind_s,
            "from_qn":          frm,
            "from_simple":      simple_name(frm),
            "from_stereotype":  frm_label,
            "to_qn":            to_qn,
            "to_simple":        simple_name(to_display),
            "to_stereotype":    to_label,
        }
        yield doc_id(root, frm, "injects", to_display), doc, meta

    # ── 2. Inheritance (extends) ──────────────────────────────────────────────
    for t in snapshot.get("types", []):
        qn  = t["qualifiedName"]
        ext = t.get("extendsType", "")
        if not ext or ext in ("Object", "java.lang.Object"):
            continue

        frm_label = stereo_label(qn, type_info, stereotypes)
        to_label  = stereo_label(
            resolve_qn(ext, simple_to_qn), type_info, stereotypes)

        doc = (
            f"{frm_label} {simple_name(qn)} extends {ext}\n"
            f"{simple_name(qn)} inherits from {ext}\n"
            f"inheritance: {frm_label.lower()} → {ext}"
        )
        meta = {
            "project_root":    root,
            "relation":        "extends",
            "injection_kind":  "",
            "from_qn":         qn,
            "from_simple":     simple_name(qn),
            "from_stereotype": frm_label,
            "to_qn":           resolve_qn(ext, simple_to_qn),
            "to_simple":       simple_name(ext),
            "to_stereotype":   to_label,
        }
        yield doc_id(root, qn, "extends", ext), doc, meta

    # ── 3. Interface implementation (implements) ──────────────────────────────
    for t in snapshot.get("types", []):
        qn    = t["qualifiedName"]
        impls = t.get("implementsTypes", [])
        for iface in impls:
            frm_label = stereo_label(qn, type_info, stereotypes)
            doc = (
                f"{frm_label} {simple_name(qn)} implements {iface}\n"
                f"{simple_name(qn)} provides implementation of {iface}\n"
                f"implementation: {frm_label.lower()} → interface {iface}"
            )
            meta = {
                "project_root":    root,
                "relation":        "implements",
                "injection_kind":  "",
                "from_qn":         qn,
                "from_simple":     simple_name(qn),
                "from_stereotype": frm_label,
                "to_qn":           resolve_qn(iface, simple_to_qn),
                "to_simple":       simple_name(iface),
                "to_stereotype":   "INTERFACE",
            }
            yield doc_id(root, qn, "implements", iface), doc, meta

    # ── 4. Field dependencies ─────────────────────────────────────────────────
    for t in snapshot.get("types", []):
        qn     = t["qualifiedName"]
        fields = t.get("fieldsByName", {})
        frm_label = stereo_label(qn, type_info, stereotypes)
        for field_name, field_type in fields.items():
            to_label = stereo_label(
                resolve_qn(field_type, simple_to_qn), type_info, stereotypes)
            doc = (
                f"{frm_label} {simple_name(qn)} has field {field_name} of type {field_type}\n"
                f"{simple_name(qn)} depends on {field_type} via field {field_name}\n"
                f"field dependency: {frm_label.lower()} → {to_label.lower()} {field_type}"
            )
            meta = {
                "project_root":    root,
                "relation":        "has_field",
                "injection_kind":  "",
                "from_qn":         qn,
                "from_simple":     simple_name(qn),
                "from_stereotype": frm_label,
                "to_qn":           resolve_qn(field_type, simple_to_qn),
                "to_simple":       simple_name(field_type),
                "to_stereotype":   to_label,
                "field_name":      field_name,
            }
            yield doc_id(root, qn, "field", field_name, field_type), doc, meta

    # ── 5. Package import dependencies ────────────────────────────────────────
    for from_pkg, to_pkgs in snapshot.get("packageImportDependencies", {}).items():
        for to_pkg in (to_pkgs or []):
            doc = (
                f"package {from_pkg} imports package {to_pkg}\n"
                f"{from_pkg} depends on {to_pkg}\n"
                f"package dependency: {from_pkg} → {to_pkg}"
            )
            meta = {
                "project_root":    root,
                "relation":        "package_import",
                "injection_kind":  "",
                "from_qn":         from_pkg,
                "from_simple":     simple_name(from_pkg),
                "from_stereotype": "PACKAGE",
                "to_qn":           to_pkg,
                "to_simple":       simple_name(to_pkg),
                "to_stereotype":   "PACKAGE",
            }
            yield doc_id(root, from_pkg, "imports", to_pkg), doc, meta


# ── Main upsert ───────────────────────────────────────────────────────────────

def upsert(url: str, snapshot: dict) -> dict:
    root   = snapshot["projectRoot"]
    client = get_client(url)

    types_col  = ensure_collection(client, COLLECTION_TYPES)
    beans_col  = ensure_collection(client, COLLECTION_BEANS)
    wiring_col = ensure_collection(client, COLLECTION_WIRING)

    for col in (types_col, beans_col, wiring_col):
        delete_project(col, root)

    # Build shared lookups
    type_info, stereotypes, simple_to_qn = build_lookups(snapshot)

    # Build DI maps for enriched type/bean docs
    graph  = snapshot.get("springComponentGraph", {})
    injects_map: dict    = {}   # from_qn → [to_qn_or_simple, ...]
    injected_by_map: dict = {}  # to_qn   → [from_qn, ...]
    for e in graph.get("injectionEdges", []):
        frm   = e.get("fromQualifiedName", "")
        to_qn = e.get("toQualifiedName") or e.get("toTypeSimpleName") or ""
        if frm and to_qn:
            injects_map.setdefault(frm, []).append(to_qn)
            injected_by_map.setdefault(to_qn, []).append(frm)

    # ── Upsert types ──────────────────────────────────────────────────────────
    ids, docs, metas = [], [], []
    for t in snapshot.get("types", []):
        qn = t["qualifiedName"]
        ids.append(doc_id(root, qn))
        docs.append(type_document(t, type_info, stereotypes,
                                  injects_map, injected_by_map))
        metas.append({
            "project_root":   root,
            "qualified_name": qn,
            "simple_name":    t.get("simpleName", ""),
            "package_name":   t.get("packageName", ""),
            "kind":           t.get("kind", "CLASS"),
            "stereotypes":    stereotypes.get(qn, ""),
            "source_path":    t.get("sourcePath", ""),
            "line_number":    t.get("lineNumber", 0),
        })
        if len(ids) == BATCH_SIZE:
            upsert_batch(types_col, ids, docs, metas)
            ids, docs, metas = [], [], []
    upsert_batch(types_col, ids, docs, metas)
    type_count = len(snapshot.get("types", []))

    # ── Upsert beans ──────────────────────────────────────────────────────────
    ids, docs, metas = [], [], []
    for comp in graph.get("components", []):
        qn     = comp["qualifiedName"]
        stereo = ",".join(comp.get("stereotypes", []))
        t      = type_info.get(qn, {})
        ids.append(doc_id(root, qn))
        docs.append(bean_document(comp, type_info, stereotypes,
                                  injects_map, injected_by_map))
        metas.append({
            "project_root":   root,
            "qualified_name": qn,
            "simple_name":    simple_name(qn),
            "stereotypes":    stereo,
            "source_path":    comp.get("sourcePath", ""),
            "line_number":    comp.get("lineNumber", 0),
        })
        if len(ids) == BATCH_SIZE:
            upsert_batch(beans_col, ids, docs, metas)
            ids, docs, metas = [], [], []
    upsert_batch(beans_col, ids, docs, metas)
    bean_count = len(graph.get("components", []))

    # ── Upsert wiring ─────────────────────────────────────────────────────────
    ids, docs, metas = [], [], []
    wiring_count = 0
    for wid, wdoc, wmeta in wiring_documents(snapshot, type_info,
                                             stereotypes, simple_to_qn):
        ids.append(wid)
        docs.append(wdoc)
        metas.append(wmeta)
        wiring_count += 1
        if len(ids) == BATCH_SIZE:
            upsert_batch(wiring_col, ids, docs, metas)
            ids, docs, metas = [], [], []
    upsert_batch(wiring_col, ids, docs, metas)

    return {"status": "ok",
            "types":  type_count,
            "beans":  bean_count,
            "wiring": wiring_count}


# ── Query ─────────────────────────────────────────────────────────────────────

def query_collection(url: str, collection_name: str, project_root: str,
                     text: str, n: int) -> list:
    client = get_client(url)
    try:
        col = client.get_collection(collection_name, embedding_function=EF)
    except Exception:
        return []

    kwargs: dict = dict(query_texts=[text], n_results=n,
                        include=["documents", "distances", "metadatas"])
    if project_root:
        kwargs["where"] = {"project_root": {"$eq": project_root}}

    results = col.query(**kwargs)
    out = []
    for i, rid in enumerate(results["ids"][0]):
        dist = results["distances"][0][i]
        out.append({
            "id":         rid,
            "document":   results["documents"][0][i],
            "similarity": round(1.0 - dist / 2.0, 4),
            "metadata":   results["metadatas"][0][i],
        })
    return out


# ── Entry point ───────────────────────────────────────────────────────────────

if __name__ == "__main__":
    cmd = sys.argv[1] if len(sys.argv) > 1 else ""

    if cmd == "upsert":
        url      = sys.argv[2]
        snapshot = json.load(sys.stdin)
        print(json.dumps(upsert(url, snapshot)))

    elif cmd == "query-types":
        url, pr, text, n = sys.argv[2], sys.argv[3], sys.argv[4], int(sys.argv[5])
        print(json.dumps(query_collection(url, COLLECTION_TYPES, pr, text, n)))

    elif cmd == "query-beans":
        url, pr, text, n = sys.argv[2], sys.argv[3], sys.argv[4], int(sys.argv[5])
        print(json.dumps(query_collection(url, COLLECTION_BEANS, pr, text, n)))

    elif cmd == "query-wiring":
        url, pr, text, n = sys.argv[2], sys.argv[3], sys.argv[4], int(sys.argv[5])
        print(json.dumps(query_collection(url, COLLECTION_WIRING, pr, text, n)))

    else:
        json.dump({"error": f"unknown command: {cmd}"}, sys.stderr)
        sys.exit(1)
