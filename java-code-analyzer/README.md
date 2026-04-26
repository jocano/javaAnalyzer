# Java Code Analyzer

A standalone tool to **query** your Java source code and **generate cross-reference matrices** (packages, classes, controllers, services, repositories, entities, etc.). It also builds a **Spring stereotype + dependency-injection graph**, exports it to JSON or databases, draws **sequence diagrams** from methods, and produces a **controller ↔ service** diagram.

Uses [JavaParser](https://javaparser.org/) to parse `.java` files—no IntelliJ required. Works with any Java project (Maven, Gradle, or plain folders).

## Build

```bash
cd java-code-analyzer
mvn package
```

Run with:

```bash
java -jar target/java-code-analyzer-1.0.0-SNAPSHOT.jar [<project-root>]
```

If you omit `<project-root>`, the current directory is used. The tool scans for all `.java` files (excluding `target`, `build`, `out`, `.git`).

**Tip:** Point `<project-root>` at the module or tree you care about (for example `../src/main/java`) if you want to avoid scanning unrelated modules in a large repo.

## Commands (interactive CLI)

| Command | Description |
|--------|-------------|
| `packages` | List all package names |
| `types <package>` | List classes/interfaces in a package (with kind and annotations) |
| `methods <package>` | List all public methods of all types in the given package |
| `methods <ClassName>` | List public methods of the given class (fully qualified name, e.g. `com.example.app.UserService`) |
| `controllers` | List types annotated with `@Controller` or `@RestController` |
| `services` | List `@Service` types |
| `repositories` | List `@Repository` types |
| `entities` | List `@Entity` types |
| `interfaces` | List all interfaces |
| `package-deps` (or `pkg-deps`) | Package dependencies: each package with the list of packages it uses (grouped and sorted by package) |
| `export-csv <dir>` | Write CSV matrices to a directory (see below) |
| `export-html <file>` | Write a single HTML report with all tables |
| `spring-beans` | List Spring stereotype beans (`@Component`, `@Service`, `@RestController`, …) discovered from annotations |
| `spring-wiring` | List DI edges (constructor / field / setter) between beans |
| `spring-controller-service-diagram` | Write **`spring-controllers-services-diagram.puml`** and **`.svg`** in the **current working directory**; SVG is rendered via [kroki.io](https://kroki.io) |
| `persist-spring-json <file>` | Save beans + wiring as JSON |
| `persist-spring-neo4j` | Upsert graph into Neo4j (`NEO4J_URI`, `NEO4J_USER`, `NEO4J_PASSWORD`, optional `NEO4J_DATABASE`) |
| `persist-spring-couchbase` | Upsert into Couchbase (`COUCHBASE_CONNECTION_STRING`, `COUCHBASE_USER`, `COUCHBASE_PASSWORD`, optional `COUCHBASE_BUCKET`) |
| `sequence` / `seq-diagram` | Generate a **PlantUML** sequence diagram for a method; always writes **`.puml`** to the current directory and **`.svg`** via kroki.io (optional `--svg <path>` to choose the SVG path). See below. |
| `help` | Show command list |
| `quit` | Exit |

In a normal terminal, the CLI uses **JLine**: **↑ / ↓** recall the last few commands; **← / →** edit; **Ctrl+D** ends input.

## Sequence diagrams (`sequence` / `seq-diagram`)

- **Output:** A `.puml` file is always written under **`user.dir`** (the directory from which you started the JVM), with a derived name from class + method (and overload hash when needed). An **SVG** with the same base name is produced via **kroki.io** (requires network access).
- **Optional:** `--svg <output.svg>` to set the SVG path explicitly.
- **Depth:** Optional trailing integer (default **10**), e.g. `sequence myMethod 5`.
- **Forms:**
  - `sequence <methodName>` — if exactly one public/protected method matches that name in the project.
  - `sequence <ClassName|FQN>` — interactive pick of method by number.
  - `sequence <FQN> <methodName>` or `sequence <SimpleClassName> <methodName>` when the class is unique.

## Spring controller ↔ service diagram

**`spring-controller-service-diagram`** builds a PlantUML **component** diagram of `@Controller` / `@RestController` and `@Service` types, with **dependency-injection** arrows between them. Files:

- `spring-controllers-services-diagram.puml`
- `spring-controllers-services-diagram.svg` (kroki.io)

Both are written to the **current working directory**.

## Cross-reference matrices (CSV)

When you run **`export-csv <dir>`**, the tool generates:

| File | Content |
|------|--------|
| `package-types.csv` | Package, type count, and list of type names |
| `type-annotation.csv` | Each type × columns for Controller, Service, Repository, Entity, etc. (1/0) |
| `package-stereotype.csv` | Each package × count of controllers, services, repositories, entities |
| `controller-service.csv` | Controller → Service (from field dependencies) |
| `service-repository.csv` | Service → Repository (from field dependencies) |

Open these in Excel, Google Sheets, or use them for further analysis.

## HTML report

**`export-html report.html`** produces one HTML file with:

- Packages and their types
- Tables of Controllers, Services, Repositories
- Controller → Service and Service → Repository dependency tables

Open in a browser for a quick overview.

## Use with IntelliJ Terminal (clickable links)

- Run the JAR from **IntelliJ's Terminal**. Each line is printed with a **`file:///...path/file.java:line`** link so IntelliJ recognizes it. **Click the link** (or Cmd+Click on macOS) to open that file at the given line. The `file:///` format is required for clickable links in IntelliJ on macOS and Windows.
- To analyze the **current project**, run from the project root:
  ```bash
  java -jar java-code-analyzer-1.0.0-SNAPSHOT.jar .
  ```
- The tool only reads `.java` files from disk; use IntelliJ Terminal so that the path:line links are clickable.

## Extending

- **More annotations**: Edit `JavaAnalyzer` / `QueryEngine` to add stereotypes (e.g. `@Bean`, custom annotations).
- **More matrices**: Add methods in `MatrixExporter` (e.g. Entity × Table, Controller × Endpoints) and call them from `Main` in the `export-csv` branch.
- **Method-level analysis**: Use JavaParser’s `MethodDeclaration` and `MethodCallExpr` in `JavaAnalyzer` to build call graphs and add them to the model and exporter.
