# spring-url-mapper

Small Java utility that scans a **Spring MVC** project for `@Controller` / `@RestController` types, builds a map of **HTTP method + URL path → Java class and method**, then lets you **search by URL** and **open the handler source in IntelliJ IDEA**.

Paths combine **type-level** and **method-level** `@RequestMapping` / `@GetMapping` / `@PostMapping` / `@PutMapping` / `@DeleteMapping` / `@PatchMapping` the same way Spring does at a basic level (string `value` / `path`, and `method` on `@RequestMapping`).

## Requirements

- JDK 17+
- Maven 3.9+ (to build)
- IntelliJ with the **Create Command-line Launcher** (`idea` on `PATH`), or set **`IDEA_BIN`** / **`IDEA_PATH`** to the `idea` executable, or use the default macOS app locations checked by the tool.

## Build

```bash
cd spring-url-mapper
mvn -q package
```

Runnable fat JAR:

`target/spring-url-mapper-1.0.0-SNAPSHOT-runner.jar`

## Run

Default project root is the **current working directory**.

```bash
# From your Spring Boot repo root
java -jar spring-url-mapper/target/spring-url-mapper-1.0.0-SNAPSHOT-runner.jar

# Or explicit root
java -jar .../spring-url-mapper-1.0.0-SNAPSHOT-runner.jar --root /path/to/spring-app

# Print map only, no prompt
java -jar .../spring-url-mapper-1.0.0-SNAPSHOT-runner.jar --print-only
```

During development you can also use:

```bash
mvn -q exec:java -Dexec.arguments="--print-only,--root,.."
```

## Interactive commands

After the URL map is printed:

1. Type a URL path, e.g. `/api/users/jane` or `api/users/jane` (query string is stripped). **↑ / ↓** recall earlier inputs (JLine). **Tab** opens **suggestions**: cached `METHOD` + path keys that match what you typed (prefix / substring), plus `..`, `quit`, `exit`, and **`open 1`**… after a search — use arrow keys in the menu when shown, then Enter to accept.
2. Enter **`..`** (only) to print the **full URL map** again — same output as at startup, from the **cached** scan (no filesystem rescan).
3. The tool lists the best-matching controller patterns (`{pathVar}` segments match any single path segment).
4. Each entry prints **REST** plus a single **`file:///absolute/path/Source.java:42`** line (from `Path#toUri()` plus `:line`). Run from **IntelliJ’s Terminal**; on **macOS and Windows** you can **click** or **Cmd/Ctrl-click** that URL to open at the line. Other terminals may wrap it in **OSC 8** (JetBrains’ terminal keeps plain `file:///` so the built-in detector works).
5. Optional: **`SPRING_URL_MAPPER_USE_IDEA_PROTOCOL=1`** uses `idea://open?…` instead; **`SPRING_URL_MAPPER_USE_IDE_HTTP=1`** uses `http://127.0.0.1:63342/api/file?…` (set **`IDEA_HTTP_PORT`** if needed; IDE must be running).
6. `open 1` still opens the first match via the `idea` command-line launcher (when configured).

Disable hyperlinks (plain text only): `SPRING_URL_MAPPER_PLAIN=1` or `-Dspring.url.mapper.plain=true`.

Force hyperlinks on (e.g. when `System.console()` is null but the terminal still supports OSC 8): `SPRING_URL_MAPPER_HYPERLINK=1`.

`quit` or `exit` leaves the program.

## Limitations

- Source-only analysis (no classpath): custom meta-annotations, constants for paths, or SpEL are not resolved.
- Non-static nested controller classes are skipped.
- Ant-style `**` patterns are only matched in a minimal way.
- Duplicate `METHOD path` keys from the scanner overwrite earlier entries in the map.
