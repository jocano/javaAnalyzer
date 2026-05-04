# Spring Component Analyzer

A Spring Boot application that **parses Java/Spring projects**, extracts all components and their relationships, persists the results in **Couchbase**, and exposes an **interactive CLI** and a **web UI** for querying the data.

---

## Features

- Parses `.java` source files using [JavaParser](https://javaparser.org/)
- Detects Spring stereotypes: `@Controller`, `@RestController`, `@Service`, `@Repository`, `@Entity`, `@Component`, `@Configuration`, `@FeignClient`, `@Aspect`
- Extracts **REST endpoints** with HTTP method, URL path (class prefix + method path), handler method, return type, and all parameter bindings (`@PathVariable`, `@RequestParam`, `@RequestBody`, `@RequestHeader`)
- Extracts **component relationships**: constructor injection, field injection (`@Autowired`), `extends`, `implements`, field dependencies
- Persists everything to **Couchbase** in three collections: `components`, `relationships`, `endpoints`
- **Interactive shell** (Spring Shell) — type commands directly in the terminal
- **Web UI** at `http://localhost:8080` — browser-based query interface

---

## Prerequisites

| Tool | Version |
|---|---|
| Java | 21+ |
| Maven | 3.9+ |
| Couchbase Server | 7.x (Docker recommended) |

---

## Couchbase Setup

### 1. Start Couchbase in Docker

```bash
docker run -d --name couchbase \
  -p 8091-8096:8091-8096 \
  -p 11210:11210 \
  couchbase/server:enterprise
```

### 2. Initialise the cluster

Open `http://localhost:8091` in a browser and complete the setup wizard:

- Choose **"Start a new cluster"**
- Set a username and password (defaults in `application.properties`: `Administrator` / `password`)
- Accept the default service configuration

### 3. Create the bucket and collections

In the Couchbase Web Console:

1. Go to **Buckets → Add Bucket**
   - Name: `java-analyzer`
   - Memory: 256 MB (adjust as needed)

2. Go to **Data → Buckets → java-analyzer → Scopes & Collections → _default scope**
   - Add collection: `components`
   - Add collection: `relationships`
   - Add collection: `endpoints`

### 4. Create indexes (required for queries)

Open the **Query** tab in the Couchbase Web Console and run:

```sql
CREATE PRIMARY INDEX ON `java-analyzer`.`_default`.`components`;
CREATE PRIMARY INDEX ON `java-analyzer`.`_default`.`relationships`;
CREATE PRIMARY INDEX ON `java-analyzer`.`_default`.`endpoints`;

CREATE INDEX idx_comp_project   ON `java-analyzer`.`_default`.`components`(projectRoot);
CREATE INDEX idx_comp_type      ON `java-analyzer`.`_default`.`components`(projectRoot, componentType);
CREATE INDEX idx_comp_name      ON `java-analyzer`.`_default`.`components`(projectRoot, simpleName);

CREATE INDEX idx_rel_project    ON `java-analyzer`.`_default`.`relationships`(projectRoot);
CREATE INDEX idx_rel_from       ON `java-analyzer`.`_default`.`relationships`(projectRoot, fromQualifiedName);
CREATE INDEX idx_rel_to         ON `java-analyzer`.`_default`.`relationships`(projectRoot, toSimpleName);
CREATE INDEX idx_rel_from_type  ON `java-analyzer`.`_default`.`relationships`(projectRoot, fromType);
CREATE INDEX idx_rel_to_type    ON `java-analyzer`.`_default`.`relationships`(projectRoot, toType);

CREATE INDEX idx_ep_project     ON `java-analyzer`.`_default`.`endpoints`(projectRoot);
CREATE INDEX idx_ep_controller  ON `java-analyzer`.`_default`.`endpoints`(projectRoot, controllerSimpleName);
CREATE INDEX idx_ep_method      ON `java-analyzer`.`_default`.`endpoints`(projectRoot, httpMethod);
```

---

## Configuration

Edit `src/main/resources/application.properties`:

```properties
# Couchbase connection
spring.couchbase.connection-string=localhost
spring.couchbase.username=Administrator
spring.couchbase.password=your-password

# Bucket name (must exist in Couchbase before starting)
spring.data.couchbase.bucket-name=java-analyzer

# Web server port
server.port=8080
```

All properties can also be overridden via environment variables or JVM system properties:

```bash
# Environment variable (uppercase, dots → underscores)
export SPRING_COUCHBASE_PASSWORD=secret

# JVM system property
java -Dspring.couchbase.password=secret -jar spring-component-analyzer.jar
```

---

## Build

```bash
cd spring-component-analyzer
mvn clean package -DskipTests
```

The fat JAR is produced at `target/spring-component-analyzer-1.0.0-SNAPSHOT.jar`.

---

## Run

```bash
# Using Maven
mvn spring-boot:run

# Or directly with the JAR
java -jar target/spring-component-analyzer-1.0.0-SNAPSHOT.jar
```

On startup the application:
1. Connects to Couchbase
2. Starts the web server on port 8080
3. Opens an interactive shell prompt

---

## Interactive Shell (CLI)

After startup, type commands at the `shell:>` prompt.

### Analysis

| Command | Description |
|---|---|
| `analyze <projectRoot>` | Parse a Java/Spring project and persist all data to Couchbase |
| `use-project <projectRoot>` | Set the default project for subsequent commands |

```
shell:> analyze /Users/me/IdeaProjects/brewery
Analysis complete for /Users/me/IdeaProjects/brewery
  66 components
  142 relationships
  18 REST endpoints
```

### Component queries

| Command | Description |
|---|---|
| `list-components` | List all components in the current project |
| `list-components --type SERVICE` | Filter by stereotype |
| `controllers` | Shortcut for `list-components --type CONTROLLER` |
| `services` | Shortcut for `list-components --type SERVICE` |
| `repositories` | Shortcut for `list-components --type REPOSITORY` |
| `entities` | Shortcut for `list-components --type ENTITY` |
| `find <name>` | Find component by simple name (partial match supported) |

Available `--type` values: `CONTROLLER`, `SERVICE`, `REPOSITORY`, `ENTITY`, `COMPONENT`, `CONFIGURATION`, `REST_CLIENT`, `ASPECT`, `INTERFACE`, `ENUM`, `OTHER`

### REST endpoint queries

| Command | Description |
|---|---|
| `list-endpoints` | List all REST endpoints |
| `list-endpoints --method GET` | Filter by HTTP method |
| `list-endpoints --controller BeerController` | All endpoints of a controller |
| `list-endpoints --path /api/v1` | Filter by URL path fragment |
| `show-endpoint BeerController` | Full details (parameters, return type, source) |

### Relationship queries

| Command | Description |
|---|---|
| `dependencies <name>` | What does this component inject / extend / implement? |
| `dependents <name>` | What other components depend on this one? |
| `wiring` | Show controller→service and service→repository wiring |
| `wiring --layer controller-service` | Only controller→service edges |
| `wiring --layer service-repository` | Only service→repository edges |

### Admin

| Command | Description |
|---|---|
| `clear-project` | Remove all data for the current project from Couchbase |
| `help` | List all available commands |

---

## Web UI

Open `http://localhost:8080` in a browser.

### Sections

| Section | What it does |
|---|---|
| ⚡ Analyze Project | Submit a project root path to trigger analysis |
| 📋 List All | Browse all components with type badges |
| 🔍 Find by Name | Search components by partial name |
| 🏷️ By Type | Filter components by stereotype |
| 🌐 REST Endpoints | Browse endpoints; filter by method, controller, or path |
| ↘ Dependencies | What does a component depend on? |
| ↗ Dependents | What uses a given component? |
| 🔗 Spring Wiring | Controller→Service and Service→Repository wiring overview |
| 🗑️ Clear Project | Remove all Couchbase data for a project |

The project root is remembered in `localStorage` and pre-filled across all sections.

---

## REST API

All endpoints are under `/api/v1/analyzer`.

### Analysis

```
POST /api/v1/analyzer/analyze
Body: { "projectRoot": "/absolute/path/to/project" }
```

### Components

```
GET /api/v1/analyzer/components?project=<root>
GET /api/v1/analyzer/components?project=<root>&type=SERVICE
GET /api/v1/analyzer/find?project=<root>&name=BeerController
```

### REST Endpoints

```
GET /api/v1/analyzer/endpoints?project=<root>
GET /api/v1/analyzer/endpoints?project=<root>&method=GET
GET /api/v1/analyzer/endpoints?project=<root>&controller=BeerController
GET /api/v1/analyzer/endpoints?project=<root>&path=/api/v1
```

### Relationships

```
GET /api/v1/analyzer/dependencies?project=<root>&name=BeerController
GET /api/v1/analyzer/dependents?project=<root>&name=BeerService
GET /api/v1/analyzer/relationships?project=<root>
GET /api/v1/analyzer/wiring/controller-service?project=<root>
GET /api/v1/analyzer/wiring/service-repository?project=<root>
```

### Admin

```
DELETE /api/v1/analyzer/project?project=<root>
```

---

## Data Model

### Couchbase Collections

| Collection | Contents | Document ID format |
|---|---|---|
| `components` | One document per Java type | `<projectSlug>::<qualifiedName>` |
| `relationships` | One document per directed relationship | `<projectSlug>::<fromQn>::<kind>::<toName>` |
| `endpoints` | One REST endpoint per HTTP method + path | `<projectSlug>::<controllerQn>::<method>::<path>#<handler>` |

### Relationship kinds

| Kind | Meaning |
|---|---|
| `INJECTS_CONSTRUCTOR` | A injects B via constructor parameter |
| `INJECTS_FIELD` | A injects B via `@Autowired` field |
| `INJECTS_SETTER` | A injects B via setter |
| `EXTENDS` | A extends B |
| `IMPLEMENTS` | A implements interface B |
| `HAS_FIELD` | A declares a field of type B (not necessarily Spring-managed) |
| `EXPOSES` | Controller A exposes REST endpoint (method + path) |

---

## Project Structure

```
spring-component-analyzer/
├── pom.xml
└── src/main/java/com/example/sca/
    ├── SpringComponentAnalyzerApplication.java
    ├── config/
    │   └── CouchbaseConfig.java          cluster / bucket / collection wiring
    ├── model/
    │   ├── ComponentType.java            enum: CONTROLLER, SERVICE, REPOSITORY, …
    │   ├── JavaComponent.java            @Document → "components" collection
    │   ├── ComponentRelationship.java    @Document → "relationships" collection
    │   ├── RestEndpoint.java             @Document → "endpoints" collection
    │   ├── EndpointParameter.java        nested: @PathVariable, @RequestParam, …
    │   └── MethodSignature.java          nested: method name + params + return type
    ├── parser/
    │   ├── JavaProjectParser.java        walks .java files, calls visitor
    │   └── ComponentVisitor.java         JavaParser AST visitor
    ├── repository/
    │   ├── JavaComponentRepository.java  CRUD + N1QL queries for components
    │   ├── RelationshipRepository.java   CRUD + N1QL queries for relationships
    │   └── RestEndpointRepository.java   CRUD + N1QL queries for endpoints
    ├── service/
    │   └── AnalyzerService.java          orchestrates parse → persist → query
    ├── shell/
    │   └── AnalyzerShell.java            Spring Shell interactive CLI
    └── web/
        └── AnalyzerController.java       REST API  /api/v1/analyzer/…
```
