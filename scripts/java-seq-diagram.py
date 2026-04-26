#!/usr/bin/env python3
"""
java-seq-diagram.py
-------------------
Generate a PlantUML sequence diagram SVG with clickable source-code links
(vscode:// or idea://) from the downstream call chain of a Java method.

Spring Cloud OpenFeign: calls to ``@FeignClient`` interfaces are resolved to the
matching ``@RestController`` / ``@Controller`` handler when HTTP method and path
(line up after normalizing ``{pathVariable}`` segments) match.

Plain Java interfaces: a call through an interface type (field / parameter typed
as ``MyIface``) jumps to the **first** concrete ``implements MyIface`` class
found under ``--src-root`` that defines the method, so the diagram does not stop
at the abstract interface declaration.  The same applies when the **entry** file
is the interface itself: abstract methods are followed into the first implementor
(``BottlingService.bottle`` → ``Bottler.bottle``), not a dead end.
The diagram labels that hop with ``(via MyIface)`` and links to the **implementation**
source line.  An ``implements`` index is built with tree-sitter when available
and a regex fallback otherwise.

Fluent call chains (e.g. ``Event.builder().eventType(…).build()``) are decomposed
into separate steps: static/typed calls are traced normally; intermediate
``… .method()`` links on a builder are shown as self-calls on the caller (with
``build()`` no longer filtered out as a “utility” name).

Usage
-----
python3 java-seq-diagram.py <JavaFile.java> <methodName> [options]

Options
-------
  --src-root PATH       Source root for type resolution (repeatable).
                        Default: auto-detected from the Java file's path.
                        For multi-module projects, pass it multiple times:
                          --src-root module-a/src/main/java
                          --src-root module-b/src/main/java
  --out NAME            Output base name without extension.
                        Default: <ClassName>-<method>-seq
  --ide vscode|intellij IDE URI scheme for clickable links (default: vscode).
  --depth N             Maximum call-chain depth (default: 5).
  --nesting             Show call depth visually using activate/deactivate lifeline bars.
  --no-open             Do not auto-open the SVG in the browser after rendering.

Requirements
------------
  plantuml on PATH  →  brew install plantuml

  Recommended (Java 17–accurate parsing)::

      pip install tree-sitter tree-sitter-java

  Without these packages the script falls back to a lighter regex/brace scanner.

Examples
--------
  python3 java-seq-diagram.py BottlerService.java bottle
  python3 java-seq-diagram.py BottlerService.java bottle --ide intellij
  python3 java-seq-diagram.py BottlerService.java bottle --src-root /IdeaProjects/brewery/brewing/src/main/java
      --src-root /IdeaProjects/brewery/common/src/main/java \\
      --depth 4 --out bottler-seq

  python3 java-seq-diagram.py   /Users/jcano/IdeaProjects/brewery/brewing/src/main/java/io/spring/cloud/samples/brewery/bottling/BottlerService.java   bottle   --src-root /Users/jcano/IdeaProjects/brewery/brewing/src/main/java   --src-root /Users/jcano/IdeaProjects/brewery/common/src/main/java   --depth 4    
"""

import argparse
import re
import subprocess
import sys
import textwrap
from dataclasses import dataclass, field as dc_field
from pathlib import Path
from typing import Optional

try:
    import tree_sitter_java as _ts_java  # type: ignore
    from tree_sitter import Language as _TSLanguage, Parser as _TSParser  # type: ignore

    _TS_JAVA_LANG = _TSLanguage(_ts_java.language())
    _TS_PARSER = _TSParser(_TS_JAVA_LANG)
    _TREE_SITTER_JAVA_AVAILABLE = True
except Exception:  # pragma: no cover - optional dependency
    _TS_PARSER = None
    _TREE_SITTER_JAVA_AVAILABLE = False

# ---------------------------------------------------------------------------
# Exclusion lists – skip calls to these framework / stdlib symbols
# ---------------------------------------------------------------------------

EXCLUDED_RECEIVERS = {
    "log", "logger", "LOGGER", "LOG",
    "System", "String", "Math", "Objects", "Thread", "URI",
    "MessageBuilder", "Optional", "Arrays", "Collections",
    "Map", "List", "Set", "PROCESS_STATE",
    "Observation", "stateForProcess", "processor",
}

EXCLUDED_METHODS = {
    "if", "for", "while", "switch", "catch", "return", "new", "throw",
    "super", "this", "assert", "else", "try", "finally",
    "observe", "run", "get", "set", "put", "build", "create",
    "getOrDefault", "ifAvailable", "send", "exchange", "post",
    "equals", "hashCode", "toString", "contains", "add", "size",
    "createNotStarted", "withPayload", "sleep", "count",
    "info", "debug", "warn", "error", "format", "trace",
    "valueOf", "parseInt", "stream", "map", "filter", "collect",
    "requireNonNull", "getLogger", "getClass", "start", "stop",
    "open", "close", "flush", "append",
}


def _is_excluded_method(name: str) -> bool:
    """Return True for framework noise, getter/setter patterns, and known utility calls."""
    if name in EXCLUDED_METHODS:
        return True
    # Skip getters (getFoo), setters (setFoo), boolean getters (isFoo, hasFoo)
    if re.match(r"^(?:get|set|is|has)[A-Z]", name):
        return True
    return False


def _is_excluded_qualified_call(name: str) -> bool:
    """Like `_is_excluded_method` but keep `client.getOrder()`-style calls (Feign / HTTP APIs)."""
    if name in EXCLUDED_METHODS:
        return True
    return False


# Fluent receiver chains: allow builder-style methods excluded from normal lists (e.g. build)
_FLUENT_CHAIN_EXCLUDED = frozenset(
    {"equals", "hashCode", "toString", "getClass", "notify", "notifyAll", "wait", "clone"}
)


def _is_excluded_fluent_terminal(name: str) -> bool:
    """Filter obvious noise on `.foo().bar()` chains; keep ``build()``, ``eventType()``, etc."""
    if name in _FLUENT_CHAIN_EXCLUDED:
        return True
    return False


# ---------------------------------------------------------------------------
# Data classes
# ---------------------------------------------------------------------------

@dataclass
class Field:
    name: str
    type_name: str  # simple (unqualified) class name


@dataclass
class MethodInfo:
    name: str
    start_line: int
    body: str           # full text of method body including braces
    is_async: bool = False
    params: dict = dc_field(default_factory=dict)  # param_name -> simple type name
    # Tree-sitter: UTF-8 byte span of the executable body (block / constructor_body) in the file
    body_span: Optional[tuple[int, int]] = dc_field(default=None, repr=False)


@dataclass
class JavaClass:
    file_path: Path
    package: str
    class_name: str
    class_line: int
    fields: list    # List[Field]
    methods: dict   # method_name -> MethodInfo (first overload wins)
    # class | interface | record | enum — used to resolve abstract interface calls
    declaration_kind: str = "class"
    # Tree-sitter parse cache (same bytes used for body_span offsets)
    source_bytes: Optional[bytes] = dc_field(default=None, repr=False)
    ts_tree: Optional[object] = dc_field(default=None, repr=False)


@dataclass
class CallEdge:
    from_class: str
    from_method: str
    to_class: str
    to_method: str
    to_file: Path
    to_line: int
    is_async: bool = False
    depth: int = 0   # depth of the calling context (0 = direct call from entry method)
    # When True, this edge jumps from a Feign client call to the matched @RestController method.
    feign_hop: bool = False
    http_method: Optional[str] = None
    rest_path: Optional[str] = None
    # True when we resolved an abstract interface call to a concrete implementor
    interface_impl_hop: bool = False
    interface_name: Optional[str] = None
    # True for `.eventType().processId().build()` steps shown as self-calls on the caller
    fluent_chain_step: bool = False


@dataclass
class FeignClientRoutes:
    """Parsed @FeignClient interface: base path + per-method HTTP mapping."""

    class_name: str
    file_path: Path
    base_path: str
    # method_name -> (HTTP verb, path segment from mapping, declaration line)
    methods: dict[str, tuple[str, str, int]]


@dataclass
class RestEndpoint:
    """A Spring MVC handler discovered in source."""

    class_name: str
    method_name: str
    file_path: Path
    start_line: int
    http_method: str
    full_path: str
    path_key: tuple[str, ...]


# ---------------------------------------------------------------------------
# Feign + Spring MVC path resolution
# ---------------------------------------------------------------------------

_MAPPING_KIND_TO_HTTP = {
    "GetMapping": "GET",
    "PostMapping": "POST",
    "PutMapping": "PUT",
    "DeleteMapping": "DELETE",
    "PatchMapping": "PATCH",
}


def _balanced_paren_content(s: str, open_paren_idx: int) -> tuple[str, int]:
    """Return (inner text, index just after closing ')')."""
    depth = 0
    i = open_paren_idx
    while i < len(s):
        if s[i] == "(":
            depth += 1
        elif s[i] == ")":
            depth -= 1
            if depth == 0:
                return s[open_paren_idx + 1 : i], i + 1
        i += 1
    return s[open_paren_idx + 1 :], len(s)


def _matching_brace_close(s: str, open_brace_idx: int) -> int:
    depth = 0
    i = open_brace_idx
    while i < len(s):
        if s[i] == "{":
            depth += 1
        elif s[i] == "}":
            depth -= 1
            if depth == 0:
                return i
        i += 1
    return -1


def _paths_from_mapping_inner(inner: str) -> list[str]:
    """Extract path strings from a Spring @*Mapping(…) argument list."""
    paths: list[str] = []
    inner_stripped = inner.strip()
    # Shorthand: @GetMapping("/x") or @GetMapping({"/a","/b"})
    if inner_stripped.startswith("{") and inner_stripped.endswith("}"):
        for m in re.finditer(r'["\']([^"\']+)["\']', inner_stripped):
            paths.append(m.group(1))
        if paths:
            return paths
    for m in re.finditer(
        r"(?:path|value)\s*=\s*(?:\{([^}]*)\}|[\"']([^\"']+)[\"'])",
        inner,
    ):
        if m.group(1):
            for sm in re.finditer(r'["\']([^"\']+)["\']', m.group(1)):
                paths.append(sm.group(1))
        elif m.group(2):
            paths.append(m.group(2))
    if not paths:
        qm = re.match(r'^["\']([^"\']+)["\']', inner_stripped)
        if qm:
            paths.append(qm.group(1))
    return paths


def _http_from_request_mapping_inner(inner: str) -> Optional[str]:
    m = re.search(r"method\s*=\s*(?:HttpMethod\.)?(?:RequestMethod\.)?(\w+)", inner)
    if m:
        return m.group(1).upper()
    m = re.search(r"method\s*=\s*\{([^}]+)\}", inner)
    if m:
        mm = re.search(r"(?:RequestMethod\.)?(\w+)", m.group(1))
        if mm:
            return mm.group(1).upper()
    return None


def parse_web_mapping_at(content: str, at: int) -> tuple[Optional[str], list[str], int]:
    """
    Parse @GetMapping / @PostMapping / @RequestMapping starting at ``at`` (index of '@').

    Returns (http_method or None for unconstrained RequestMapping, path list, end index).
    """
    m = re.match(
        r"@(?P<kind>GetMapping|PostMapping|PutMapping|DeleteMapping|PatchMapping|RequestMapping)\b",
        content[at:],
    )
    if not m:
        return None, [], at
    kind = m.group("kind")
    j = at + m.end()
    while j < len(content) and content[j] in " \t":
        j += 1
    if j >= len(content) or content[j] != "(":
        return None, [], at
    inner, after = _balanced_paren_content(content, j)
    if kind == "RequestMapping":
        http = _http_from_request_mapping_inner(inner) or "GET"
    else:
        http = _MAPPING_KIND_TO_HTTP[kind]
    paths = _paths_from_mapping_inner(inner)
    return http, paths, after


def _feign_base_path(content: str) -> str:
    idx = content.find("@FeignClient")
    if idx < 0:
        return ""
    j = content.find("(", idx)
    if j < 0:
        return ""
    inner, _ = _balanced_paren_content(content, j)
    for key in ("path", "value"):
        pm = re.search(rf"{key}\s*=\s*[\"']([^\"']+)[\"']", inner)
        if pm:
            return pm.group(1).strip()
    return ""


def _split_interface_members(interface_body: str) -> list[str]:
    """Split top-level interface members; ignores bodies of default/static methods via brace depth."""
    parts: list[str] = []
    i = 0
    brace = 0
    start = 0
    while i < len(interface_body):
        c = interface_body[i]
        if c == "{":
            brace += 1
        elif c == "}":
            brace -= 1
        elif c == ";" and brace == 0:
            chunk = interface_body[start : i + 1].strip()
            if chunk:
                parts.append(chunk)
            start = i + 1
        i += 1
    return parts


def parse_feign_client_file(path: Path, content: str) -> Optional[FeignClientRoutes]:
    if "@FeignClient" not in content:
        return None
    if not re.search(r"\binterface\s+\w+", content):
        return None
    im = re.search(r"\binterface\s+(\w+)\s*[^{]*\{", content)
    if not im:
        return None
    class_name = im.group(1)
    body_open = im.end() - 1
    body_close = _matching_brace_close(content, body_open)
    if body_close < 0:
        return None
    interface_body = content[body_open + 1 : body_close]
    base = _feign_base_path(content)
    methods: dict[str, tuple[str, str, int]] = {}

    for mem in _split_interface_members(interface_body):
        if re.search(r"\bdefault\s+", mem) or re.search(r"\bstatic\s+", mem):
            continue
        abs_mem_start = content.find(mem)
        if abs_mem_start < 0:
            continue
        http: Optional[str] = None
        paths: list[str] = []
        for ann in re.finditer(
            r"@(GetMapping|PostMapping|PutMapping|DeleteMapping|PatchMapping|RequestMapping)\b",
            mem,
        ):
            h, ps, _ = parse_web_mapping_at(mem, ann.start())
            if h:
                http = h
            if ps:
                paths = ps
        if not http or not paths:
            continue
        rel_path = paths[0]
        mname = _feign_simple_method_name(mem)
        if not mname:
            continue
        mline = content[: abs_mem_start].count("\n") + 1
        if mname not in methods:
            methods[mname] = (http, rel_path, mline)

    if not methods:
        return None
    return FeignClientRoutes(
        class_name=class_name,
        file_path=path,
        base_path=base,
        methods=methods,
    )


def _class_level_request_prefix(content: str, class_name: str) -> str:
    cm = re.search(rf"(?:public\s+)?class\s+{re.escape(class_name)}\b", content)
    if not cm:
        return ""
    head = content[max(0, cm.start() - 1200) : cm.start()]
    http: Optional[str] = None
    paths: list[str] = []
    for ann in re.finditer(r"@RequestMapping\b", head):
        h, ps, _ = parse_web_mapping_at(head, ann.start())
        if h:
            http = h
        if ps:
            paths = ps
    if paths:
        return paths[0]
    return ""


def _extract_controller_mappings(
    content: str, class_name: str, file_path: Path
) -> list[RestEndpoint]:
    """Collect @*Mapping metadata for each concrete method body in a controller class."""
    out: list[RestEndpoint] = []
    class_prefix = _class_level_request_prefix(content, class_name)

    class_decl = re.search(
        r"(?:public\s+)?class\s+" + re.escape(class_name) + r"\s*[^{]*\{",
        content,
    )
    if not class_decl:
        return out

    i = class_decl.end()
    class_depth = 1
    segment_start = i

    _KEYWORDS = {
        "if", "for", "while", "switch", "catch", "new", "return", "throw",
        "super", "this", "else", "try", "finally", "assert", "instanceof",
        "class", "interface", "enum", "do",
    }
    method_sig_re = re.compile(
        r"(?:@(\w+)\s+(?:@\w+\s*)*)?"
        r"(?:(?:public|private|protected)\s+)?"
        r"(?:(?:static|final|synchronized|abstract|override)\s+)*"
        r"\w[\w<>\[\]]*\s+"
        r"([a-z]\w*)\s*\(",
        re.DOTALL,
    )

    while i < len(content) and class_depth > 0:
        j = _consume_java_literal_or_comment(content, i)
        if j > i:
            i = j
            continue

        c = content[i]
        if c == "{" and class_depth == 1:
            sig_area = content[segment_start:i]
            sig_matches = list(method_sig_re.finditer(sig_area))
            if sig_matches:
                sig = sig_matches[-1]
                mname = sig.group(2)
                if mname not in _KEYWORDS:
                    abs_start = segment_start + sig.start()
                    start_line = content[:abs_start].count("\n") + 1
                    anno_region = sig_area[: sig.start()]
                    http: Optional[str] = None
                    paths: list[str] = []
                    for ann in re.finditer(
                        r"@(GetMapping|PostMapping|PutMapping|DeleteMapping|PatchMapping|RequestMapping)\b",
                        anno_region,
                    ):
                        h, ps, _ = parse_web_mapping_at(anno_region, ann.start())
                        if h:
                            http = h
                        if ps:
                            paths = ps
                    if http and paths:
                        rel = paths[0]
                        full = _join_paths(class_prefix, rel)
                        pk = _path_key(full)
                        out.append(
                            RestEndpoint(
                                class_name=class_name,
                                method_name=mname,
                                file_path=file_path,
                                start_line=start_line,
                                http_method=http,
                                full_path=full,
                                path_key=pk,
                            )
                        )
            body_text = _extract_body(content, i)
            i += len(body_text)
            segment_start = i
        elif c == "{":
            class_depth += 1
            i += 1
        elif c == "}":
            class_depth -= 1
            i += 1
        else:
            i += 1

    return out


def parse_controller_file(path: Path, content: str) -> list[RestEndpoint]:
    if "@RestController" not in content and not re.search(r"@Controller\b", content):
        return []
    cm = re.search(r"(?:public\s+)?class\s+(\w+)\b", content)
    if not cm:
        return []
    return _extract_controller_mappings(content, cm.group(1), path)


def scan_feign_and_rest(src_roots: list[Path]) -> tuple[dict[str, FeignClientRoutes], list[RestEndpoint]]:
    feign: dict[str, FeignClientRoutes] = {}
    rest: list[RestEndpoint] = []
    seen_ep: set[tuple] = set()
    for root in src_roots:
        for jf in root.rglob("*.java"):
            try:
                text = jf.read_text(errors="replace")
            except OSError:
                continue
            fc = parse_feign_client_file(jf, text)
            if fc:
                feign[fc.class_name] = fc
            for ep in parse_controller_file(jf, text):
                dedupe_key = (ep.file_path, ep.method_name, ep.path_key, ep.http_method)
                if dedupe_key in seen_ep:
                    continue
                seen_ep.add(dedupe_key)
                rest.append(ep)
    return feign, rest


def _join_paths(prefix: str, segment: str) -> str:
    p = (prefix or "").strip()
    s = (segment or "").strip()
    if s.startswith("/") and not p:
        return normalize_url_path(s)
    if not p:
        return normalize_url_path(s)
    if not s:
        return normalize_url_path(p)
    return normalize_url_path(f"{p.rstrip('/')}/{s.lstrip('/')}")


def normalize_url_path(path: str) -> str:
    """Single canonical path with leading slash, no duplicate slashes."""
    segs: list[str] = []
    for part in path.replace("\\", "/").split("/"):
        if part:
            segs.append(part)
    return "/" + "/".join(segs) if segs else "/"


def _path_key(full_path: str) -> tuple[str, ...]:
    p = normalize_url_path(full_path).strip("/")
    if not p:
        return tuple()
    key: list[str] = []
    for seg in p.split("/"):
        if seg.startswith("{") and seg.endswith("}"):
            key.append("{}")
        else:
            key.append(seg)
    return tuple(key)


def _match_rest_endpoint(
    http_method: str, full_path: str, endpoints: list[RestEndpoint]
) -> Optional[RestEndpoint]:
    want = _path_key(full_path)
    hm = http_method.upper()
    candidates = [e for e in endpoints if e.http_method.upper() == hm and e.path_key == want]
    if candidates:
        return candidates[0]
    # Fallback: same path key, any method (e.g. ambiguous RequestMapping)
    loose = [e for e in endpoints if e.path_key == want]
    return loose[0] if loose else None


def resolve_feign_to_controller(
    feign_class_name: str,
    callee_method: str,
    feign_registry: dict[str, FeignClientRoutes],
    rest_endpoints: list[RestEndpoint],
) -> Optional[tuple[Path, str, str, int, str, str]]:
    """
    If (feign_class_name, callee_method) is a Feign declaration with a matching
    Spring MVC handler, return
    (controller_file, controller_class, controller_method, start_line, http_method, full_path).
    """
    routes = feign_registry.get(feign_class_name)
    if not routes:
        return None
    spec = routes.methods.get(callee_method)
    if not spec:
        return None
    http, rel_path, _ = spec
    full = _join_paths(routes.base_path, rel_path)
    ep = _match_rest_endpoint(http, full, rest_endpoints)
    if not ep:
        return None
    return (ep.file_path, ep.class_name, ep.method_name, ep.start_line, http, full)


# ---------------------------------------------------------------------------
# Tree-sitter Java (Java 17-friendly: records, text blocks, sealed, switch, …)
# ---------------------------------------------------------------------------

_TS_TYPE_DECL = frozenset(
    {"class_declaration", "interface_declaration", "record_declaration", "enum_declaration"}
)


def _ts_text(data: bytes, node) -> str:
    return data[node.start_byte : node.end_byte].decode("utf-8", errors="replace")


def _ts_line_number(content: str, byte_index: int) -> int:
    """1-based line from UTF-8 byte offset (matches encoded content)."""
    return content.encode("utf-8")[:byte_index].decode("utf-8", errors="replace").count("\n") + 1


def _ts_line_from_byte_in_file(data: bytes, byte_index: int) -> int:
    """1-based line number for a UTF-8 byte offset within ``data`` (whole source file)."""
    if byte_index <= 0:
        return 1
    head = data[:byte_index].decode("utf-8", errors="replace")
    return head.count("\n") + 1


def _ts_find_first_type_declaration(root) -> Optional[object]:
    for ch in root.children:
        if ch.type in _TS_TYPE_DECL:
            return ch
    return None


def _ts_get_type_body(type_node) -> Optional[object]:
    for ch in type_node.children:
        if ch.type in ("class_body", "interface_body", "enum_body"):
            return ch
    return None


def _ts_simple_type_name(type_node, data: bytes) -> Optional[str]:
    raw = _ts_text(data, type_node).strip()
    raw = raw.split("<", 1)[0].strip()
    raw = raw.split("[", 1)[0].strip()
    if "." in raw:
        raw = raw.rsplit(".", 1)[-1]
    return raw if raw else None


def _ts_collect_fields(type_node, data: bytes) -> list:
    body = _ts_get_type_body(type_node)
    if not body:
        return []
    out: list = []
    for ch in body.children:
        if ch.type != "field_declaration":
            continue
        typ_node = None
        for sub in ch.children:
            if sub.type in (
                "type_identifier",
                "generic_type",
                "scoped_type_identifier",
                "integral_type",
                "array_type",
            ):
                typ_node = sub
                break
        if typ_node is None:
            continue
        st = _ts_simple_type_name(typ_node, data)
        if not st:
            continue
        if not st[0].isupper() and st != "var":
            continue
        for sub in ch.children:
            if sub.type != "variable_declarator":
                continue
            for vv in sub.children:
                if vv.type == "identifier":
                    out.append(Field(name=_ts_text(data, vv), type_name=st))
                    break
    return out


def _ts_parse_formal_parameters(fp_node, data: bytes) -> dict[str, str]:
    params: dict[str, str] = {}
    if fp_node.type != "formal_parameters":
        return params
    for ch in fp_node.children:
        if ch.type != "formal_parameter":
            continue
        typ_node = None
        name_node = None
        for sub in ch.children:
            if sub.type in (
                "type_identifier",
                "generic_type",
                "scoped_type_identifier",
                "integral_type",
                "array_type",
            ):
                typ_node = sub
            elif sub.type == "identifier":
                name_node = sub
        if typ_node is None or name_node is None:
            continue
        bt = _ts_simple_type_name(typ_node, data)
        if bt and (bt[0].isupper() or bt == "var"):
            params[_ts_text(data, name_node)] = bt if bt != "var" else "Object"
    return params


def _ts_scan_modifiers_async(decl_node, data: bytes) -> bool:
    for ch in decl_node.children:
        if ch.type != "modifiers":
            continue
        if "Async" in _ts_text(data, ch):
            return True
    return False


def _ts_method_identifier_before_params(decl_node, data: bytes) -> Optional[str]:
    """Name for method_declaration / constructor_declaration / compact_constructor_declaration."""
    if decl_node.type == "compact_constructor_declaration":
        for ch in decl_node.children:
            if ch.type == "identifier":
                return _ts_text(data, ch)
        return None
    for i, ch in enumerate(decl_node.children):
        if ch.type == "formal_parameters" and i > 0:
            prev = decl_node.children[i - 1]
            if prev.type == "identifier":
                return _ts_text(data, prev)
    return None


def _ts_callable_body_span(decl_node, data: bytes) -> Optional[tuple[str, tuple[int, int], bool]]:
    """Return (body_source, (start_byte,end_byte), is_async) or None if abstract / no body."""
    is_async = _ts_scan_modifiers_async(decl_node, data)
    body_node = None
    for ch in decl_node.children:
        if ch.type in ("block", "constructor_body"):
            body_node = ch
            break
    if body_node is None:
        return None
    span = (body_node.start_byte, body_node.end_byte)
    src = data[span[0] : span[1]].decode("utf-8", errors="replace")
    return src, span, is_async


def _ts_iter_callable_declarations(type_node) -> list:
    body = _ts_get_type_body(type_node)
    if not body:
        return []
    decls: list = []
    if type_node.type == "enum_declaration":
        for ch in body.children:
            if ch.type != "enum_body_declarations":
                continue
            for decl in ch.children:
                if decl.type in (
                    "method_declaration",
                    "constructor_declaration",
                    "compact_constructor_declaration",
                ):
                    decls.append(decl)
        return decls
    for ch in body.children:
        if ch.type in (
            "method_declaration",
            "constructor_declaration",
            "compact_constructor_declaration",
        ):
            decls.append(ch)
    return decls


def _ts_find_body_node(tree, span: tuple[int, int]):
    target_start, target_end = span
    found = None

    def walk(n):
        nonlocal found
        if found is not None:
            return
        if n.type in ("block", "constructor_body"):
            if n.start_byte == target_start and n.end_byte == target_end:
                found = n
                return
        for c in n.children:
            walk(c)

    walk(tree.root_node)
    return found


def _ts_collect_invocations_ordered(body_node) -> list:
    invs: list = []
    stack = [body_node]
    while stack:
        n = stack.pop()
        if n.type == "method_invocation":
            invs.append(n)
        for c in reversed(n.children):
            stack.append(c)
    # Nested invocations often share the same start_byte; (end_byte) orders inner→outer.
    invs.sort(key=lambda x: (x.start_byte, x.end_byte))
    return invs


def _ts_classify_invocation(mi, data: bytes, field_map: dict[str, str]) -> Optional[tuple]:
    """
    Map a method_invocation AST node to (receiver_type|None, method_name) or None to skip.
    Mirrors legacy extract_calls heuristics where possible.
    """
    ch = mi.children
    if not ch:
        return None
    # Unqualified: identifier + argument_list
    if len(ch) == 2 and ch[0].type == "identifier" and ch[1].type == "argument_list":
        mname = _ts_text(data, ch[0])
        if _is_excluded_method(mname):
            return None
        return (None, mname)

    # Qualified ... . method (
    if len(ch) < 4:
        return None
    if ch[-1].type != "argument_list":
        return None
    method_id = ch[-2]
    if method_id.type != "identifier":
        return None
    if ch[-3].type != ".":
        return None
    recv = ch[-4]
    mname = _ts_text(data, method_id)
    if _is_excluded_qualified_call(mname):
        return None

    if recv.type == "identifier":
        recv_txt = _ts_text(data, recv)
        if recv_txt in EXCLUDED_RECEIVERS:
            return None
        receiver_type = field_map.get(recv_txt, recv_txt)
        if not receiver_type or not receiver_type[0].isupper():
            return None
        return (receiver_type, mname)

    if recv.type in ("this", "super"):
        return (None, mname)

    return None


def _ts_classify_fluent_terminal(mi, data: bytes) -> Optional[str]:
    """
    ``x().y()`` / ``a.b().c()`` where the immediate receiver is not a simple
    identifier — typical fluent builder chains (eventType, processId, build, …).
    """
    ch = mi.children
    if len(ch) < 4:
        return None
    if ch[-1].type != "argument_list":
        return None
    if ch[-2].type != "identifier":
        return None
    if ch[-3].type != ".":
        return None
    recv = ch[-4]
    if recv.type not in ("method_invocation", "field_access"):
        return None
    mname = _ts_text(data, ch[-2])
    if _is_excluded_fluent_terminal(mname):
        return None
    return mname


def _ts_classify_invocation_full(mi, data: bytes, field_map: dict[str, str]) -> Optional[tuple]:
    """
    Classify a method_invocation for tracing.

    Returns ``(receiver_type, method_name, fluent_site)``.
    ``fluent_site`` is None for normal calls. When it is an int (UTF-8 byte offset
    in the file), this is a fluent-chain step: emit a self-call edge on the
    caller’s lifeline at that offset; ``receiver_type`` is None for those rows.
    """
    simple = _ts_classify_invocation(mi, data, field_map)
    if simple is not None:
        r, m = simple
        return (r, m, None)
    ft = _ts_classify_fluent_terminal(mi, data)
    if ft is not None:
        return (None, ft, mi.start_byte)
    return None


def extract_calls_ts(body_node, data: bytes, field_map: dict[str, str]) -> list[tuple]:
    results: list[tuple] = []
    seen_span: set[tuple[int, int]] = set()
    for mi in _ts_collect_invocations_ordered(body_node):
        tup = _ts_classify_invocation_full(mi, data, field_map)
        if tup is None:
            continue
        span = (mi.start_byte, mi.end_byte)
        if span in seen_span:
            continue
        seen_span.add(span)
        results.append(tup)
    return results


def parse_java_file_tree_sitter(path: Path, content: str, data: bytes) -> Optional[JavaClass]:
    if not _TREE_SITTER_JAVA_AVAILABLE or _TS_PARSER is None:
        return None
    tree = _TS_PARSER.parse(data)
    type_node = _ts_find_first_type_declaration(tree.root_node)
    if type_node is None:
        return None

    type_id = None
    for ch in type_node.children:
        if ch.type == "identifier":
            type_id = ch
            break
    if type_id is None:
        return None
    class_name = _ts_text(data, type_id)
    class_line = _ts_line_number(content, type_node.start_byte)
    decl_kind = {
        "class_declaration": "class",
        "interface_declaration": "interface",
        "record_declaration": "record",
        "enum_declaration": "enum",
    }.get(type_node.type, "class")

    m_pack = re.search(r"^\s*package\s+([\w.]+)\s*;", content, re.MULTILINE)
    package = m_pack.group(1) if m_pack else ""

    fields = _ts_collect_fields(type_node, data)
    methods: dict[str, MethodInfo] = {}

    for decl in _ts_iter_callable_declarations(type_node):
        kind = decl.type
        if kind == "method_declaration":
            mname = _ts_method_identifier_before_params(decl, data)
        elif kind in ("constructor_declaration", "compact_constructor_declaration"):
            mname = _ts_method_identifier_before_params(decl, data) or class_name
        else:
            continue
        if not mname:
            continue
        body_info = _ts_callable_body_span(decl, data)
        if body_info is None:
            continue
        body_src, span, is_async = body_info
        start_line = _ts_line_number(content, decl.start_byte)
        params = {}
        for ch in decl.children:
            if ch.type == "formal_parameters":
                params = _ts_parse_formal_parameters(ch, data)
                break
        methods[mname] = MethodInfo(
            name=mname,
            start_line=start_line,
            body=body_src,
            is_async=is_async,
            params=params,
            body_span=span,
        )

    return JavaClass(
        file_path=path,
        package=package,
        class_name=class_name,
        class_line=class_line,
        fields=fields,
        methods=methods,
        declaration_kind=decl_kind,
        source_bytes=data,
        ts_tree=tree,
    )


def _ts_extract_implements_interface_names(super_interfaces_node, data: bytes) -> list[str]:
    names: list[str] = []
    for ch in super_interfaces_node.children:
        if ch.type != "type_list":
            continue
        for tl_ch in ch.children:
            if tl_ch.type not in ("type_identifier", "scoped_type_identifier"):
                continue
            nm = _ts_simple_type_name(tl_ch, data)
            if nm and nm[0].isupper():
                names.append(nm)
    return names


def _merge_impl_indices(
    a: dict[str, list[tuple[Path, str]]], b: dict[str, list[tuple[Path, str]]]
) -> dict[str, list[tuple[Path, str]]]:
    merged: dict[str, list[tuple[Path, str]]] = {}
    for src in (a, b):
        for iface, pairs in src.items():
            merged.setdefault(iface, []).extend(pairs)
    for k in list(merged.keys()):
        merged[k] = sorted(set(merged[k]), key=lambda x: (str(x[0]), x[1]))
    return merged


def _build_implementation_index_regex(src_roots: list[Path]) -> dict[str, list[tuple[Path, str]]]:
    """
    Best-effort ``implements`` scan when tree-sitter is unavailable.

    Splits the file at each top-level ``class|record`` declaration and parses
    the ``implements`` clause up to the opening ``{`` of that type's body,
    ignoring angle-bracket nesting.  Accurate for typical Spring/service classes;
    prefer tree-sitter for complex inner types.
    """
    out: dict[str, list[tuple[Path, str]]] = {}
    type_head = re.compile(
        r"^\s*(?:@\w+(?:\([^)]*\))?\s+)*(?:(?:public|protected|private)\s+)?"
        r"(?:(?:abstract|final|sealed|non-sealed|static)\s+)*"
        r"(?:class|record|enum)\s+(\w+)\b",
        re.MULTILINE,
    )

    def clause_upto_body_brace(tail: str) -> Optional[str]:
        i = 0
        depth = 0
        while i < len(tail):
            j = _consume_java_literal_or_comment(tail, i)
            if j > i:
                i = j
                continue
            c = tail[i]
            if c == "<":
                depth += 1
            elif c == ">":
                depth = max(0, depth - 1)
            elif c == "{" and depth == 0:
                return tail[:i]
            i += 1
        return None

    for root in src_roots:
        for jf in root.rglob("*.java"):
            try:
                text = jf.read_text(encoding="utf-8", errors="replace")
            except OSError:
                continue
            if "implements" not in text:
                continue
            matches = list(type_head.finditer(text))
            resolved = jf.resolve()
            for idx, m in enumerate(matches):
                impl_cls = m.group(1)
                block_start = m.start()
                block_end = matches[idx + 1].start() if idx + 1 < len(matches) else len(text)
                segment = text[block_start:block_end]
                imp = re.search(r"\bimplements\b\s*", segment)
                if not imp:
                    continue
                tail = segment[imp.end() :]
                upto = clause_upto_body_brace(tail)
                if upto is None:
                    continue
                clause_flat = re.sub(r"<[^>]*>", "", upto)
                for part in clause_flat.split(","):
                    part = part.strip()
                    if not part:
                        continue
                    tok = part.split()[-1]
                    tok = tok.split(".")[-1].strip()
                    if tok and tok[0].isupper():
                        out.setdefault(tok, []).append((resolved, impl_cls))

    for k in list(out.keys()):
        out[k] = sorted(set(out[k]), key=lambda x: (str(x[0]), x[1]))
    return out


def build_implementation_index(src_roots: list[Path]) -> dict[str, list[tuple[Path, str]]]:
    """
    Scan sources for ``class|record … implements IFace`` and map each
    interface simple name to ``(source file, implementing type simple name)``.

    Uses tree-sitter when available, plus a regex fallback so interface
    resolution still works without the optional dependency.
    """
    out: dict[str, list[tuple[Path, str]]] = {}
    if _TREE_SITTER_JAVA_AVAILABLE and _TS_PARSER is not None:
        for root in src_roots:
            for jf in root.rglob("*.java"):
                try:
                    data = jf.read_bytes()
                except OSError:
                    continue
                tree = _TS_PARSER.parse(data)
                resolved = jf.resolve()

                def walk(n):
                    if n.type in ("class_declaration", "record_declaration", "enum_declaration"):
                        impl_cls = None
                        for ch in n.children:
                            if ch.type == "identifier":
                                impl_cls = _ts_text(data, ch)
                                break
                        if not impl_cls:
                            for c in n.children:
                                walk(c)
                            return
                        for ch in n.children:
                            if ch.type != "super_interfaces":
                                continue
                            for nm in _ts_extract_implements_interface_names(ch, data):
                                out.setdefault(nm, []).append((resolved, impl_cls))
                    for c in n.children:
                        walk(c)

                walk(tree.root_node)

        for k in list(out.keys()):
            out[k] = sorted(set(out[k]), key=lambda x: (str(x[0]), x[1]))

    rx = _build_implementation_index_regex(src_roots)
    return _merge_impl_indices(out, rx)


def resolve_interface_to_implementation(
    callee_class: JavaClass,
    callee_method: str,
    callee_info: Optional[MethodInfo],
    impl_index: dict[str, list[tuple[Path, str]]],
    get_class,
) -> Optional[tuple[Path, str, int]]:
    """
    Abstract call on interface type → first scanned class/record/enum that
    implements the interface and defines ``callee_method`` with a body.

    Default methods on the interface (present in ``methods``) are not redirected.
    """
    if callee_info is not None:
        return None
    if callee_class.declaration_kind != "interface":
        return None
    for path, _impl_simple in impl_index.get(callee_class.class_name, []):
        jc = get_class(path)
        mi = jc.methods.get(callee_method)
        if mi is None:
            continue
        return (path, jc.class_name, mi.start_line)
    return None


# ---------------------------------------------------------------------------
# Java source parsing (regex fallback)
# ---------------------------------------------------------------------------

def _consume_java_literal_or_comment(content: str, i: int) -> int:
    """
    If content[i] starts a string literal, character literal, //, or /* comment,
    return the index just past that construct; otherwise return i.
    """
    n = len(content)
    if i >= n:
        return i
    if content[i] == '"':
        i += 1
        while i < n:
            if content[i] == "\\":
                i += 2
                continue
            if content[i] == '"':
                return i + 1
            i += 1
        return n
    if content[i] == "'":
        i += 1
        while i < n:
            if content[i] == "\\":
                i += 2
                continue
            if content[i] == "'":
                return i + 1
            i += 1
        return n
    if i + 1 < n and content[i : i + 2] == "//":
        nl = content.find("\n", i)
        return nl + 1 if nl >= 0 else n
    if i + 1 < n and content[i : i + 2] == "/*":
        end = content.find("*/", i + 2)
        return end + 2 if end >= 0 else n
    return i


def _find_matching_close_paren(content: str, open_paren_idx: int) -> int:
    """Return index of `)` matching `(` at open_paren_idx, or -1."""
    if open_paren_idx >= len(content) or content[open_paren_idx] != "(":
        return -1
    depth = 0
    k = open_paren_idx
    n = len(content)
    while k < n:
        j = _consume_java_literal_or_comment(content, k)
        if j > k:
            k = j
            continue
        ch = content[k]
        if ch == "(":
            depth += 1
        elif ch == ")":
            depth -= 1
            if depth == 0:
                return k
        k += 1
    return -1


def _feign_simple_method_name(member: str) -> Optional[str]:
    """Interface member ending with `... name(...) ;` — tolerate `)` inside annotations."""
    mm = re.search(r"\b([a-z]\w*)\s*\(", member)
    if not mm:
        return None
    close = _find_matching_close_paren(member, mm.end() - 1)
    if close < 0:
        return None
    if not member[close + 1 :].lstrip().startswith(";"):
        return None
    return mm.group(1)


def _extract_body(content: str, open_brace_pos: int) -> str:
    """Return text from open_brace_pos to its matching closing brace."""
    depth = 0
    i = open_brace_pos
    while i < len(content):
        c = content[i]
        if c == '{':
            depth += 1
        elif c == '}':
            depth -= 1
            if depth == 0:
                return content[open_brace_pos : i + 1]
        i += 1
    return content[open_brace_pos:]


def _extract_class_methods(content: str) -> dict:  # noqa: C901
    """
    Extract method declarations at class-body level ONLY (brace depth == 1).

    The broad-regex approach mistakenly treats method *calls* inside bodies as
    declarations (e.g. ``bottleWithCircuitBreaker(wort, processId)`` looks like
    a method signature to a regex).  Depth tracking avoids this completely.
    """
    _KEYWORDS = {
        "if", "for", "while", "switch", "catch", "new", "return", "throw",
        "super", "this", "else", "try", "finally", "assert", "instanceof",
        "class", "interface", "enum", "do",
    }
    # method name must start with lowercase to filter out constructors / inner classes
    method_sig_re = re.compile(
        r"(?:@(\w+)\s+(?:@\w+\s*)*)?"
        r"(?:(?:public|private|protected)\s+)?"
        r"(?:(?:static|final|synchronized|abstract|override)\s+)*"
        r"\w[\w<>\[\]]*\s+"       # return type — MUST start with \w (not whitespace)
        r"([a-z]\w*)\s*\(",       # method name — lowercase start rules out constructors
        re.DOTALL,
    )

    methods: dict[str, MethodInfo] = {}

    # Locate the class body opening brace
    class_decl = re.search(
        r"(?:class|interface|@interface|enum|record)\s+\w+[^{]*\{", content
    )
    if not class_decl:
        return methods

    i = class_decl.end()   # right after the opening '{' of the class
    class_depth = 1        # we are at class-body level
    segment_start = i      # start of current class-level text segment

    while i < len(content) and class_depth > 0:
        j = _consume_java_literal_or_comment(content, i)
        if j > i:
            i = j
            continue

        c = content[i]

        if c == "{" and class_depth == 1:
            # This '{' opens a member (method, static block, inner class…) at class level.
            # Inspect the preceding text segment for a method signature.
            sig_area = content[segment_start:i]
            sig_matches = list(method_sig_re.finditer(sig_area))
            if sig_matches:
                sig = sig_matches[-1]   # last match = closest to '{'
                mname = sig.group(2)
                if mname not in _KEYWORDS and mname not in methods:
                    abs_start = segment_start + sig.start()
                    start_line = content[:abs_start].count("\n") + 1
                    annotation = sig.group(1) or ""
                    body = _extract_body(content, i)

                    # Parse parameter types from the text between '(' and '{'
                    # e.g. "Wort wort, String processId" → {wort: Wort}
                    params: dict[str, str] = {}
                    param_text = content[segment_start + sig.end() - 1 : i]  # from '(' to '{'
                    # strip to just the parameter list (up to closing ')')
                    close_paren = param_text.find(")")
                    if close_paren > 0:
                        param_list = param_text[1:close_paren]  # skip leading '('
                        for param in param_list.split(","):
                            # handle generics: List<Foo> items → type=List, name=items
                            parts = param.strip().split()
                            if len(parts) >= 2:
                                raw_type = re.split(r"[<\[]", parts[-2])[0]
                                pname = parts[-1].lstrip("@")
                                if raw_type and raw_type[0].isupper():
                                    params[pname] = raw_type

                    methods[mname] = MethodInfo(
                        name=mname,
                        start_line=start_line,
                        body=body,
                        is_async="Async" in annotation,
                        params=params,
                    )

            # Skip the entire block so nested '{' / '}' don't confuse depth tracking
            body_text = _extract_body(content, i)
            i += len(body_text)
            segment_start = i
            # class_depth stays at 1 — nested braces were consumed by _extract_body

        elif c == "{":
            class_depth += 1
            i += 1
        elif c == "}":
            class_depth -= 1
            i += 1
        else:
            i += 1

    return methods


def _infer_declaration_kind_regex(content: str) -> str:
    m = re.search(r"\b(interface|class|record|enum)\s+\w+", content)
    if not m:
        return "class"
    return {"interface": "interface", "class": "class", "record": "record", "enum": "enum"}.get(
        m.group(1), "class"
    )


def _parse_java_file_regex(path: Path, content: str) -> JavaClass:
    """Legacy regex/brace scanner (optional when tree-sitter is unavailable)."""

    # package declaration
    m = re.search(r"^\s*package\s+([\w.]+)\s*;", content, re.MULTILINE)
    package = m.group(1) if m else ""

    # class / interface / enum / record name and line
    m = re.search(
        r"(?:public\s+|private\s+|protected\s+)?"
        r"(?:abstract\s+|final\s+|sealed\s+|non-sealed\s+)?"
        r"(?:class|interface|@interface|enum|record)\s+(\w+)",
        content,
    )
    class_name = m.group(1) if m else path.stem
    class_line = (content[: m.start()].count("\n") + 1) if m else 1

    # private [final] TypeName fieldName;
    fields: list[Field] = []
    for fm in re.finditer(
        r"\bprivate\b\s+(?:final\s+)?"
        r"((?:\w[\w$]*(?:<[^>]*>)?(?:\[\])?)\s+)"  # type
        r"(\w[\w$]*)\s*;",                          # name
        content,
    ):
        raw_type = fm.group(1).strip()
        simple_type = re.split(r"[<\[\s]", raw_type)[0]
        if simple_type and simple_type[0].isupper():
            fields.append(Field(name=fm.group(2), type_name=simple_type))

    methods = _extract_class_methods(content)

    return JavaClass(
        file_path=path,
        package=package,
        class_name=class_name,
        class_line=class_line,
        fields=fields,
        methods=methods,
        declaration_kind=_infer_declaration_kind_regex(content),
    )


def parse_java_file(path: Path) -> JavaClass:
    """
    Parse a ``.java`` file. Uses **tree-sitter-java** when installed (Java 17-friendly);
    falls back to the internal regex/brace scanner otherwise.
    """
    content = path.read_text(encoding="utf-8", errors="replace")
    data = content.encode("utf-8")
    if _TREE_SITTER_JAVA_AVAILABLE:
        try:
            jc = parse_java_file_tree_sitter(path, content, data)
            if jc is not None:
                return jc
        except Exception:
            pass
    return _parse_java_file_regex(path, content)


# ---------------------------------------------------------------------------
# Call extraction from a method body
# ---------------------------------------------------------------------------

def _strip_noise(body: str) -> str:
    """Remove string literals and comments to avoid false-positive matches."""
    body = re.sub(r'"(?:[^"\\]|\\.)*"', '""', body)
    body = re.sub(r"'(?:[^'\\]|\\.)*'", "''", body)
    body = re.sub(r"//[^\n]*", "", body)
    body = re.sub(r"/\*.*?\*/", "", body, flags=re.DOTALL)
    return body


def extract_calls(
    body: str,
    field_map: dict[str, str],
    *,
    java_class: Optional[JavaClass] = None,
    method_info: Optional[MethodInfo] = None,
) -> list[tuple]:
    """
    Return a list of ``(receiver_type | None, method_name, fluent_site)`` in source order.

    ``receiver_type`` is None → local / self call; otherwise a simple type name.
    ``fluent_site`` is None for normal calls, or a UTF-8 **byte offset in the full
    source file** for fluent-chain steps (``… .eventType().build()``) that should
    appear as self-call edges without traversing into another type.

    Tree-sitter sets ``fluent_site`` as a UTF-8 byte offset in the file.

    Regex fallback encodes fluent steps as ``fluent_site <= -100000`` (see trace).
    """
    if (
        java_class is not None
        and method_info is not None
        and java_class.ts_tree is not None
        and java_class.source_bytes is not None
        and method_info.body_span is not None
    ):
        body_node = _ts_find_body_node(java_class.ts_tree, method_info.body_span)
        if body_node is not None:
            return extract_calls_ts(body_node, java_class.source_bytes, field_map)

    clean = _strip_noise(body)
    # (char_offset_in_clean, payload) sorted for source order
    scored: list[tuple[int, tuple]] = []
    seen: set[tuple] = set()

    # qualified calls: receiver.method(
    for m in re.finditer(r"\b(\w+)\.(\w+)\s*\(", clean):
        recv, mname = m.group(1), m.group(2)
        if recv in EXCLUDED_RECEIVERS or _is_excluded_qualified_call(mname):
            continue
        receiver_type = field_map.get(recv, recv)
        if not receiver_type or not receiver_type[0].isupper():
            continue
        key = (receiver_type, mname, None)
        if key not in seen:
            seen.add(key)
            scored.append((m.start(), key))

    # unqualified calls: methodName(  (not preceded by . or word char)
    for m in re.finditer(r"(?<![.\w])([a-z]\w*)\s*\(", clean):
        mname = m.group(1)
        if _is_excluded_method(mname):
            continue
        key = (None, mname, None)
        if key not in seen:
            seen.add(key)
            scored.append((m.start(), key))

    # Fluent tails without tree-sitter: foo().bar().baz() — ").method("
    for m in re.finditer(r"\)\s*\.(\w+)\s*\(", clean):
        mname = m.group(1)
        if _is_excluded_fluent_terminal(mname):
            continue
        line_in_body = clean[: m.start()].count("\n")
        fluent_site = -(100000 + line_in_body)
        key = (None, mname, fluent_site)
        if key not in seen:
            seen.add(key)
            scored.append((m.start(), key))

    scored.sort(key=lambda x: x[0])
    out = [t for _, t in scored]
    # Avoid duplicate edges when ").processId(" also matches unqualified "processId(" (same name).
    fluent_local_names = {t[1] for t in out if t[0] is None and t[2] is not None}
    if fluent_local_names:
        out = [
            t
            for t in out
            if not (t[0] is None and t[2] is None and t[1] in fluent_local_names)
        ]
    return out


# ---------------------------------------------------------------------------
# Project index
# ---------------------------------------------------------------------------

def build_index(src_roots: list[Path]) -> dict[str, Path]:
    """Maps simple class name → Path.  First match across roots wins."""
    index: dict[str, Path] = {}
    for root in src_roots:
        for jf in root.rglob("*.java"):
            if jf.stem not in index:
                index[jf.stem] = jf
    return index


def auto_detect_roots(java_file: Path) -> list[Path]:
    """
    Walk up from java_file looking for src/main/java roots.
    Also checks sibling module directories to support multi-module projects.
    """
    roots: list[Path] = []
    for p in java_file.parents:
        candidate = p / "src" / "main" / "java"
        if candidate.is_dir():
            roots.append(candidate)
            # sibling modules (e.g. ../common/src/main/java)
            for sibling in p.parent.iterdir():
                if sibling.is_dir() and sibling != p:
                    sib_candidate = sibling / "src" / "main" / "java"
                    if sib_candidate.is_dir() and sib_candidate not in roots:
                        roots.append(sib_candidate)
            break
    return roots or [java_file.parent]


# ---------------------------------------------------------------------------
# Call-graph traversal (DFS, preserves call order)
# ---------------------------------------------------------------------------

def trace(
    entry_file: Path,
    entry_method: str,
    index: dict[str, Path],
    max_depth: int,
    feign_registry: dict[str, FeignClientRoutes],
    rest_endpoints: list[RestEndpoint],
    impl_index: dict[str, list[tuple[Path, str]]],
) -> tuple[dict, list]:
    """
    DFS traversal of the call graph starting from entry_method in entry_file.

    Returns:
      participants – OrderedDict-like {class_name: JavaClass} in encounter order
      edges        – list[CallEdge] in traversal / call order
    """
    participants: dict[str, JavaClass] = {}
    edges: list[CallEdge] = []
    visited: set[tuple[str, str]] = set()
    _class_cache: dict[Path, JavaClass] = {}

    def get_class(path: Path) -> JavaClass:
        if path not in _class_cache:
            _class_cache[path] = parse_java_file(path)
        return _class_cache[path]

    def visit(java_class: JavaClass, method_name: str, depth: int) -> None:
        if depth > max_depth:
            return

        method_info = java_class.methods.get(method_name)

        # Entry (or inbound) visit on an abstract interface method: there is no body in
        # ``methods`` — jump to the first concrete ``implements`` class before visited/body logic.
        if not method_info and java_class.declaration_kind == "interface":
            impl_res = resolve_interface_to_implementation(
                java_class,
                method_name,
                None,
                impl_index,
                get_class,
            )
            if impl_res:
                tpath, _, _ = impl_res
                visit(get_class(tpath), method_name, depth)
            return

        if (java_class.class_name, method_name) in visited:
            return
        visited.add((java_class.class_name, method_name))

        if java_class.class_name not in participants:
            participants[java_class.class_name] = java_class

        if not method_info:
            return

        field_map = {f.name: f.type_name for f in java_class.fields}
        field_map.update(method_info.params)  # also resolve calls on method parameters
        calls = extract_calls(
            method_info.body,
            field_map,
            java_class=java_class,
            method_info=method_info,
        )

        for receiver_type, callee_method, fluent_site in calls:
            if fluent_site is not None:
                sb = java_class.source_bytes or b""
                if fluent_site <= -100000:
                    # Regex fallback: line index within method body → approximate file line
                    lib = -(fluent_site + 100000)
                    line = max(1, method_info.start_line + lib)
                elif sb:
                    line = _ts_line_from_byte_in_file(sb, fluent_site)
                else:
                    line = max(1, method_info.start_line)
                edges.append(
                    CallEdge(
                        from_class=java_class.class_name,
                        from_method=method_name,
                        to_class=java_class.class_name,
                        to_method=callee_method,
                        to_file=java_class.file_path,
                        to_line=line,
                        is_async=False,
                        depth=depth,
                        fluent_chain_step=True,
                    )
                )
                continue

            if receiver_type is None:
                callee_class = java_class
            else:
                callee_path = index.get(receiver_type)
                if callee_path is None:
                    continue
                callee_class = get_class(callee_path)

            feign_res = resolve_feign_to_controller(
                callee_class.class_name,
                callee_method,
                feign_registry,
                rest_endpoints,
            )
            if feign_res:
                tpath, tcls, tmeth, tline, thttp, tfull = feign_res
                tgt_jc = get_class(tpath)
                edges.append(
                    CallEdge(
                        from_class=java_class.class_name,
                        from_method=method_name,
                        to_class=tgt_jc.class_name,
                        to_method=tmeth,
                        to_file=tgt_jc.file_path,
                        to_line=tline,
                        is_async=False,
                        depth=depth,
                        feign_hop=True,
                        http_method=thttp,
                        rest_path=tfull,
                    )
                )
                if tgt_jc.class_name not in participants:
                    participants[tgt_jc.class_name] = tgt_jc
                visit(tgt_jc, tmeth, depth + 1)
                continue

            if callee_class.class_name in feign_registry:
                fr = feign_registry[callee_class.class_name]
                if callee_method in fr.methods:
                    # Declared Feign operation but no matching Spring handler in scanned roots.
                    continue

            callee_info = callee_class.methods.get(callee_method)

            impl_res = resolve_interface_to_implementation(
                callee_class,
                callee_method,
                callee_info,
                impl_index,
                get_class,
            )
            if impl_res:
                tpath, _tcls, tline = impl_res
                tgt_jc = get_class(tpath)
                tgt_mi = tgt_jc.methods.get(callee_method)
                edges.append(
                    CallEdge(
                        from_class=java_class.class_name,
                        from_method=method_name,
                        to_class=tgt_jc.class_name,
                        to_method=callee_method,
                        to_file=tgt_jc.file_path,
                        to_line=tline,
                        is_async=tgt_mi.is_async if tgt_mi else False,
                        depth=depth,
                        interface_impl_hop=True,
                        interface_name=callee_class.class_name,
                    )
                )
                if tgt_jc.class_name not in participants:
                    participants[tgt_jc.class_name] = tgt_jc
                visit(tgt_jc, callee_method, depth + 1)
                continue

            callee_line = callee_info.start_line if callee_info else 1
            is_async = callee_info.is_async if callee_info else False

            edges.append(
                CallEdge(
                    from_class=java_class.class_name,
                    from_method=method_name,
                    to_class=callee_class.class_name,
                    to_method=callee_method,
                    to_file=callee_class.file_path,
                    to_line=callee_line,
                    is_async=is_async,
                    depth=depth,
                )
            )

            if callee_class.class_name not in participants:
                participants[callee_class.class_name] = callee_class

            visit(callee_class, callee_method, depth + 1)

    entry_class = get_class(entry_file)
    participants[entry_class.class_name] = entry_class
    visit(entry_class, entry_method, 0)

    return participants, edges


# ---------------------------------------------------------------------------
# IDE URI generation
# ---------------------------------------------------------------------------

def make_link(file_path: Path, line: int, ide: str) -> str:
    abs_path = str(file_path.resolve())
    if ide == "intellij":
        # JetBrains Toolbox handler (macOS / Linux)
        return f"idea://open?file={abs_path}&line={line}"
    # VS Code
    return f"vscode://file/{abs_path}:{line}:1"


# ---------------------------------------------------------------------------
# PlantUML generation
# ---------------------------------------------------------------------------

def generate_puml(
    participants: dict[str, JavaClass],
    edges: list[CallEdge],
    entry_class: str,
    entry_method: str,
    ide: str,
    show_nesting: bool = False,
) -> str:
    """
    Produce PlantUML source.

    Link format used on every element:
      [[<full-uri>{<FileName.java:line>} <short-label>]]

      · full-uri   – hidden (used when clicked)
      · tooltip    – shown on hover: just FileName:line
      · short-label – displayed in the diagram: ClassName or methodName()

    Types parsed as interfaces use ``participant Name <<interface>>`` so PlantUML
    renders the «interface» stereotype on the lifeline head.

    When show_nesting=True, activate/deactivate lifeline bars are emitted
    to visually convey which calls are nested inside which.
    """
    out: list[str] = ["@startuml"]
    out.append(f"title {entry_class}.{entry_method}()")
    out.append("")
    out.append("skinparam sequenceMessageAlign center")
    out.append("skinparam responseMessageBelowArrow true")
    out.append("skinparam BoxPadding 10")
    out.append("")

    # Participants – label is the class name; link goes to the class declaration.
    # Interfaces use the PlantUML «interface» stereotype so they read differently from classes.
    for cname, jc in participants.items():
        link = make_link(jc.file_path, jc.class_line, ide)
        tooltip = f"{jc.file_path.name}:{jc.class_line}"
        if jc.declaration_kind == "interface":
            out.append(f'participant {cname} <<interface>> [[{link}{{{tooltip}}}]]')
        else:
            out.append(f'participant {cname} [[{link}{{{tooltip}}}]]')

    out.append("")

    if show_nesting:
        # Emit activate/deactivate to show call depth visually.
        # Edges arrive in DFS order with depth = depth of the calling context.
        # We maintain a stack of (class_name, edge_depth); before each edge we
        # pop (and deactivate) everything at a depth level that has already
        # "returned" by the time this call is made.
        depth_stack: list[tuple[str, int]] = []

        for edge in edges:
            # Unwind activations for calls that have already returned
            while depth_stack and depth_stack[-1][1] >= edge.depth:
                cls, _ = depth_stack.pop()
                out.append(f"deactivate {cls}")

            link = make_link(edge.to_file, edge.to_line, ide)
            tooltip = f"{edge.to_file.name}:{edge.to_line}"
            label = f"{edge.to_method}()"
            if edge.feign_hop and edge.http_method and edge.rest_path:
                label = f"{label} ({edge.http_method} {edge.rest_path})"
            if edge.interface_impl_hop and edge.interface_name:
                label = f"{label} (via {edge.interface_name})"
            arrow = "->>" if edge.is_async else "->"
            out.append(
                f"{edge.from_class} {arrow} {edge.to_class}"
                f": [[{link}{{{tooltip}}} {label}]]"
            )
            out.append(f"activate {edge.to_class}")
            depth_stack.append((edge.to_class, edge.depth))

        # Close all remaining activations
        while depth_stack:
            cls, _ = depth_stack.pop()
            out.append(f"deactivate {cls}")

    else:
        # Flat view – deduplicated by (from, to, method), call order preserved
        seen: set[tuple] = set()
        for edge in edges:
            key = (edge.from_class, edge.to_class, edge.to_method)
            if key in seen:
                continue
            seen.add(key)

            link = make_link(edge.to_file, edge.to_line, ide)
            tooltip = f"{edge.to_file.name}:{edge.to_line}"
            label = f"{edge.to_method}()"
            if edge.feign_hop and edge.http_method and edge.rest_path:
                label = f"{label} ({edge.http_method} {edge.rest_path})"
            if edge.interface_impl_hop and edge.interface_name:
                label = f"{label} (via {edge.interface_name})"
            arrow = "->>" if edge.is_async else "->"
            out.append(
                f"{edge.from_class} {arrow} {edge.to_class}"
                f": [[{link}{{{tooltip}}} {label}]]"
            )

    out.append("")
    out.append("@enduml")
    return "\n".join(out)


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main() -> None:
    parser = argparse.ArgumentParser(
        description="Generate a sequence diagram SVG with clickable source links.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=textwrap.dedent("""\
            Examples:
              python3 java-seq-diagram.py BottlerService.java bottle
              python3 java-seq-diagram.py BottlerService.java bottle --ide intellij
              python3 java-seq-diagram.py BottlerService.java bottle \\
                  --src-root /IdeaProjects/brewery/brewing/src/main/java \\
                  --src-root /IdeaProjects/brewery/common/src/main/java \\
                  --depth 4 --out bottler-seq
        """),
    )
    parser.add_argument("java_file", help="Java source file containing the entry method")
    parser.add_argument("method", help="Entry method name (e.g. bottle)")
    parser.add_argument(
        "--src-root",
        action="append",
        dest="src_roots",
        metavar="PATH",
        help=(
            "Source root for type resolution. Repeat for multi-module projects. "
            "Default: auto-detected from the Java file's path."
        ),
    )
    parser.add_argument(
        "--out",
        metavar="NAME",
        help="Output base name without extension (default: <Class>-<method>-seq)",
    )
    parser.add_argument(
        "--ide",
        choices=["vscode", "intellij"],
        default="vscode",
        help="URI scheme for clickable links (default: vscode)",
    )
    parser.add_argument(
        "--depth",
        type=int,
        default=5,
        help="Max call-chain depth (default: 5)",
    )
    parser.add_argument(
        "--nesting",
        action="store_true",
        help="Show call depth using activate/deactivate lifeline bars (default: flat view)",
    )
    parser.add_argument(
        "--no-open",
        action="store_true",
        help="Do not auto-open the SVG in the browser after rendering",
    )
    args = parser.parse_args()

    java_file = Path(args.java_file).resolve()
    if not java_file.exists():
        print(f"Error: file not found: {java_file}", file=sys.stderr)
        sys.exit(1)

    # Source roots — explicit roots are merged with auto-detected ones so that
    # --src-root is additive (you can narrow or extend without listing everything)
    auto_roots = auto_detect_roots(java_file)
    if args.src_roots:
        explicit = [Path(r).resolve() for r in args.src_roots]
        # start with explicit (higher priority), then add any auto-detected not already listed
        seen_roots: set[Path] = set(explicit)
        src_roots = explicit + [r for r in auto_roots if r not in seen_roots]
    else:
        src_roots = auto_roots

    print(f"Source roots ({len(src_roots)}):")
    for r in src_roots:
        print(f"  {r}")

    if not _TREE_SITTER_JAVA_AVAILABLE:
        print(
            "Note: fluent builder chains (.eventType().build()) and reliable Java 17 parsing "
            "need tree-sitter (pip install tree-sitter tree-sitter-java). "
            "Regex mode approximates fluent tails from ).method( patterns.",
            file=sys.stderr,
        )

    print("Building project index…")
    index = build_index(src_roots)
    print(f"  Indexed {len(index)} Java files")

    feign_registry, rest_endpoints = scan_feign_and_rest(src_roots)
    print(f"  Feign clients: {len(feign_registry)}, Spring MVC endpoints: {len(rest_endpoints)}")

    impl_index = build_implementation_index(src_roots)
    n_links = sum(len(v) for v in impl_index.values())
    print(f"  Interface implements-index: {len(impl_index)} interface types, {n_links} implementor links")

    # Parse entry class to get its real class name for the output filename
    entry_class_obj = parse_java_file(java_file)
    out_base = args.out or f"{entry_class_obj.class_name}-{args.method}-seq"

    print(f"Tracing {entry_class_obj.class_name}.{args.method}()  (max depth={args.depth})…")
    participants, edges = trace(
        java_file,
        args.method,
        index,
        args.depth,
        feign_registry,
        rest_endpoints,
        impl_index,
    )
    print(f"  {len(participants)} participants, {len(edges)} call edges")

    if not edges:
        print(
            "Warning: no call edges found. Check that the method name is correct "
            "and the source roots cover all referenced classes.",
            file=sys.stderr,
        )

    puml = generate_puml(participants, edges, entry_class_obj.class_name, args.method, args.ide, show_nesting=args.nesting)

    out_puml = Path(out_base).with_suffix(".puml")
    out_puml.write_text(puml)
    print(f"Written: {out_puml}")

    # Render to SVG
    result = subprocess.run(
        ["plantuml", "-tsvg", str(out_puml)],
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        print("PlantUML render error:", file=sys.stderr)
        print(result.stderr or result.stdout, file=sys.stderr)
        print(f"The .puml file is still at {out_puml} for manual rendering.", file=sys.stderr)
        sys.exit(1)

    out_svg = Path(out_base).with_suffix(".svg")
    link_count = out_svg.read_text().count("href=")
    print(f"SVG written: {out_svg}  ({link_count} clickable href anchors)")

    if not args.no_open:
        import platform
        if platform.system() == "Darwin":
            subprocess.run(["open", str(out_svg)])


if __name__ == "__main__":
    main()
