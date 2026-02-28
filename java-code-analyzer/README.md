# Java Code Analyzer

A standalone tool to **query** your Java source code and **generate cross-reference matrices** (packages, classes, controllers, services, repositories, entities, etc.). Uses [JavaParser](https://javaparser.org/) to parse `.java` files—no IntelliJ required. Works with any Java project (Maven, Gradle, or plain folders).

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

## Commands (interactive CLI)

| Command | Description |
|--------|-------------|
| `packages` | List all package names |
| `types <package>` | List classes/interfaces in a package (with kind and annotations) |
| `methods <package>` | List all public methods of all types in the given package |
| `methods <ClassName>` | List public methods of the given class (use fully qualified name, e.g. `com.example.app.UserService`) |
| `controllers` | List types annotated with `@Controller` or `@RestController` |
| `services` | List `@Service` types |
| `repositories` | List `@Repository` types |
| `entities` | List `@Entity` types |
| `interfaces` | List all interfaces |
| `package-deps` (or `pkg-deps`) | Package dependencies: each package with the list of packages it uses (grouped and sorted by package) |
| `export-csv <dir>` | Write CSV matrices to a directory (see below) |
| `export-html <file>` | Write a single HTML report with all tables |
| `help` | Show command list |
| `quit` | Exit |

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
