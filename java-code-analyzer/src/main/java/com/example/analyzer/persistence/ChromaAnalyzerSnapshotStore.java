package com.example.analyzer.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Persists and semantically queries the analyzer model using ChromaDB.
 *
 * <h3>How it works</h3>
 * Embedding computation requires Python's chromadb client (which bundles ONNX
 * for the {@code all-MiniLM-L6-v2} model). Rather than re-implementing that in
 * Java, this class invokes a bundled Python bridge script ({@code chroma-bridge.py})
 * via {@link ProcessBuilder}.  The bridge is extracted from the JAR's classpath
 * resources on first use and cached in {@code ~/.java-code-analyzer/}.
 *
 * <h3>Collections</h3>
 * <ul>
 *   <li>{@value #COLLECTION_TYPES}  — one document per class/interface/enum (enriched with wiring context)</li>
 *   <li>{@value #COLLECTION_BEANS}  — one document per Spring bean (with DI wiring context)</li>
 *   <li>{@value #COLLECTION_WIRING} — one document per directed relationship:
 *     DI injection, extends, implements, field dependency, package import</li>
 * </ul>
 *
 * <h3>Prerequisites</h3>
 * {@code python3} and the {@code chromadb} package must be installed:
 * <pre>pip3 install chromadb --break-system-packages</pre>
 */
public class ChromaAnalyzerSnapshotStore {

    public static final String COLLECTION_TYPES  = "java-types";
    public static final String COLLECTION_BEANS  = "java-beans";
    public static final String COLLECTION_WIRING = "java-wiring";

    private static final String BRIDGE_RESOURCE = "/chroma-bridge.py";
    private static final String CACHE_DIR        = System.getProperty("user.home")
                                                   + "/.java-code-analyzer";

    private final String       chromaUrl;
    private final ObjectMapper mapper = new ObjectMapper();

    /** Uses the ChromaDB URL from {@code application.properties} or default. */
    public ChromaAnalyzerSnapshotStore(String chromaUrl) {
        this.chromaUrl = chromaUrl;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Upserts all types and Spring beans from the snapshot into ChromaDB.
     * Existing documents for the same {@code projectRoot} are replaced first.
     *
     * @return a summary string for display
     */
    public String upsertSnapshot(AnalyzerSnapshot snapshot) throws IOException {
        String snapshotJson = mapper.writeValueAsString(snapshot);
        String resultJson   = runBridge("upsert", chromaUrl, snapshotJson);

        Map<String, Object> result = mapper.readValue(resultJson,
            new TypeReference<Map<String, Object>>() {});

        if (result.containsKey("error")) {
            throw new IOException("ChromaDB bridge error: " + result.get("error"));
        }
        int types  = ((Number) result.getOrDefault("types",  0)).intValue();
        int beans  = ((Number) result.getOrDefault("beans",  0)).intValue();
        int wiring = ((Number) result.getOrDefault("wiring", 0)).intValue();
        return String.format(
            "ChromaDB upsert complete for %s%n"
            + "  %d types   → collection '%s'%n"
            + "  %d beans   → collection '%s'%n"
            + "  %d wiring edges → collection '%s'",
            snapshot.getProjectRoot(),
            types,  COLLECTION_TYPES,
            beans,  COLLECTION_BEANS,
            wiring, COLLECTION_WIRING);
    }

    /**
     * Semantic similarity search over Java types.
     *
     * @param projectRoot filter; pass {@code null} or blank to search all projects
     * @param queryText   natural-language description of what you're looking for
     * @param nResults    max results to return
     */
    public List<ChromaQueryResult> queryTypes(String projectRoot, String queryText,
                                              int nResults) throws IOException {
        return runQuery("query-types", projectRoot, queryText, nResults);
    }

    /**
     * Semantic similarity search over Spring beans.
     *
     * @param projectRoot filter; pass {@code null} or blank to search all projects
     */
    public List<ChromaQueryResult> queryBeans(String projectRoot, String queryText,
                                              int nResults) throws IOException {
        return runQuery("query-beans", projectRoot, queryText, nResults);
    }

    /**
     * Semantic similarity search over wiring relationships.
     * <p>
     * Relationships indexed: DI injection, extends, implements, field dependency,
     * package import.  Use natural-language queries like:
     * <ul>
     *   <li>"services used by BeerController"</li>
     *   <li>"what repositories does BeerService access"</li>
     *   <li>"who injects BrewingService"</li>
     *   <li>"controllers that depend on reporting"</li>
     * </ul>
     *
     * @param projectRoot filter; pass {@code null} or blank to search all projects
     */
    public List<ChromaQueryResult> queryWiring(String projectRoot, String queryText,
                                               int nResults) throws IOException {
        return runQuery("query-wiring", projectRoot, queryText, nResults);
    }

    // ── Bridge invocation ─────────────────────────────────────────────────────

    private List<ChromaQueryResult> runQuery(String command, String projectRoot,
                                             String queryText, int nResults)
            throws IOException {
        String resultJson = runBridge(command, chromaUrl,
            projectRoot != null ? projectRoot : "",
            queryText,
            String.valueOf(nResults),
            null /* no stdin */);

        List<Map<String, Object>> rows = mapper.readValue(resultJson,
            new TypeReference<List<Map<String, Object>>>() {});

        return rows.stream().map(r -> new ChromaQueryResult(
            str(r, "id"),
            str(r, "document"),
            ((Number) r.getOrDefault("similarity", 0.0)).doubleValue(),
            (Map<String, Object>) r.getOrDefault("metadata", Map.of())
        )).toList();
    }

    /**
     * Runs {@code python3 <bridge> <args>}, optionally piping {@code stdinData}
     * to the process and returning its stdout.
     */
    private String runBridge(String... argsAndStdin) throws IOException {
        // Last element is stdin data when the previous args list ends with a null sentinel.
        // Calling convention: runBridge(cmd, arg1, ..., stdinOrNull)
        // When stdin is null (query commands), the last element is still null.
        int argCount = argsAndStdin.length - 1;
        String stdinData = argsAndStdin[argCount]; // may be null

        Path bridge  = ensureBridgeScript();
        String[] cmd = new String[argCount + 2]; // python3 + bridge + args
        cmd[0] = "python3";
        cmd[1] = bridge.toString();
        System.arraycopy(argsAndStdin, 0, cmd, 2, argCount);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(false);
        Process proc = pb.start();

        // Write stdin if provided
        if (stdinData != null) {
            try (OutputStream os = proc.getOutputStream()) {
                os.write(stdinData.getBytes(StandardCharsets.UTF_8));
            }
        } else {
            proc.getOutputStream().close();
        }

        // Read stdout
        String stdout;
        try (InputStream is = proc.getInputStream()) {
            stdout = new String(is.readAllBytes(), StandardCharsets.UTF_8).trim();
        }

        // Read stderr (for error reporting only)
        String stderr;
        try (InputStream es = proc.getErrorStream()) {
            stderr = new String(es.readAllBytes(), StandardCharsets.UTF_8).trim();
        }

        int exitCode;
        try {
            exitCode = proc.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("ChromaDB bridge process interrupted", e);
        }

        if (exitCode != 0) {
            throw new IOException("chroma-bridge.py exited with " + exitCode
                + (stderr.isBlank() ? "" : ": " + stderr));
        }
        if (stdout.isBlank()) {
            throw new IOException("chroma-bridge.py returned empty output"
                + (stderr.isBlank() ? "" : ". stderr: " + stderr));
        }
        return stdout;
    }

    // ── Bridge script extraction ──────────────────────────────────────────────

    /**
     * Extracts {@code chroma-bridge.py} from classpath resources to
     * {@code ~/.java-code-analyzer/chroma-bridge.py} on first use.
     */
    private static Path ensureBridgeScript() throws IOException {
        Path cacheDir = Path.of(CACHE_DIR);
        Files.createDirectories(cacheDir);
        Path script = cacheDir.resolve("chroma-bridge.py");

        try (InputStream src = ChromaAnalyzerSnapshotStore.class
                .getResourceAsStream(BRIDGE_RESOURCE)) {
            if (src == null) {
                throw new IOException("chroma-bridge.py not found in classpath resources");
            }
            Files.write(script, src.readAllBytes());
        }
        // Make executable on Unix-like systems
        try {
            Files.setPosixFilePermissions(script,
                PosixFilePermissions.fromString("rwxr-xr-x"));
        } catch (UnsupportedOperationException ignored) {
            // Windows — not needed
        }
        return script;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String str(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString() : "";
    }
}
