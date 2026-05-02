package com.example.analyzer.persistence;

import com.example.analyzer.model.InjectionEdge;
import com.example.analyzer.model.MethodInfo;
import com.example.analyzer.model.SpringComponent;
import com.example.analyzer.model.SpringComponentGraph;
import com.example.analyzer.model.TypeInfo;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * Persists and loads an {@link AnalyzerSnapshot} to/from a relational database
 * whose schema is defined in {@code classpath:db/schema.sql}.
 *
 * <p>Connection is taken from the {@link DataSource} auto-configured by Spring Boot
 * via {@code spring.datasource.*} properties in {@code application.properties}.
 *
 * <p>Every {@link #save} is wrapped in a single transaction: it first deletes any
 * existing snapshot for the same {@code projectRoot} (cascade removes all children),
 * then re-inserts everything.
 */
@Component
public class SqlAnalyzerSnapshotStore {

    private final JdbcTemplate jdbc;

    public SqlAnalyzerSnapshotStore(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /** Runs the classpath schema SQL (idempotent — all statements use IF NOT EXISTS). */
    public void initSchema() {
        try (java.sql.Connection conn = jdbc.getDataSource().getConnection()) {
            org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(
                conn, new org.springframework.core.io.ClassPathResource("db/schema.sql"));
        } catch (java.sql.SQLException e) {
            throw new RuntimeException("Failed to initialise schema: " + e.getMessage(), e);
        }
    }

    // ── Save ─────────────────────────────────────────────────────────────────

    @Transactional
    public void save(AnalyzerSnapshot snapshot) {
        String root = snapshot.getProjectRoot();

        // Replace any existing snapshot for this project root (cascade deletes children)
        jdbc.update("DELETE FROM project WHERE project_root = ?", root);

        jdbc.update(
            "INSERT INTO project (project_root, saved_at_millis, format_version) VALUES (?, ?, ?)",
            root, snapshot.getSavedAtMillis(), snapshot.getFormatVersion());

        // Collect every unique package name from all sources so FK constraints are satisfied
        Set<String> allPackages = new LinkedHashSet<>();
        for (AnalyzerSnapshot.PackageRecord pr : snapshot.getPackages()) {
            allPackages.add(pr.getName() != null ? pr.getName() : "");
        }
        for (Map.Entry<String, List<String>> e : snapshot.getPackageImportDependencies().entrySet()) {
            allPackages.add(e.getKey() != null ? e.getKey() : "");
            if (e.getValue() != null) {
                for (String v : e.getValue()) { if (v != null) allPackages.add(v); }
            }
        }
        for (TypeInfo t : snapshot.getTypes()) {
            allPackages.add(t.getPackageName() != null ? t.getPackageName() : "");
        }
        for (String pkg : allPackages) {
            jdbc.update("INSERT INTO package (qualified_name, project_root) VALUES (?, ?)", pkg, root);
        }

        // Types and their child rows
        for (TypeInfo t : snapshot.getTypes()) {
            String qn   = t.getQualifiedName();
            String pkg  = t.getPackageName() != null ? t.getPackageName() : "";
            String kind = t.getKind() != null ? t.getKind().name() : "CLASS";

            jdbc.update(
                "INSERT INTO type (qualified_name, simple_name, package_name, project_root, kind, "
                + "extends_type, source_path, line_number) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                qn, t.getSimpleName(), pkg, root, kind,
                t.getExtendsType(), t.getSourcePath(), t.getLineNumber());

            for (String ann : t.getAnnotations()) {
                jdbc.update(
                    "INSERT INTO type_annotation (type_qualified_name, project_root, annotation_name) VALUES (?, ?, ?)",
                    qn, root, ann);
            }
            for (String impl : t.getImplementsTypes()) {
                jdbc.update(
                    "INSERT INTO type_implements (type_qualified_name, project_root, interface_name) VALUES (?, ?, ?)",
                    qn, root, impl);
            }
            for (Map.Entry<String, String> f : t.getFieldsByName().entrySet()) {
                jdbc.update(
                    "INSERT INTO field (declaring_type, project_root, name, type_name) VALUES (?, ?, ?, ?)",
                    qn, root, f.getKey(), f.getValue());
            }
            insertMethods(qn, root, t.getPublicMethods(),    "PUBLIC");
            insertMethods(qn, root, t.getProtectedMethods(), "PROTECTED");
        }

        // Package-level import dependencies
        for (Map.Entry<String, List<String>> e : snapshot.getPackageImportDependencies().entrySet()) {
            if (e.getKey() == null || e.getValue() == null) continue;
            for (String to : e.getValue()) {
                if (to != null) {
                    jdbc.update(
                        "INSERT INTO package_import (from_package, to_package, project_root) VALUES (?, ?, ?)",
                        e.getKey(), to, root);
                }
            }
        }

        // Spring component graph
        SpringComponentGraph graph = snapshot.getSpringComponentGraph();
        if (graph != null) {
            long analyzedAt = graph.getAnalyzedAtMillis() > 0
                ? graph.getAnalyzedAtMillis() : snapshot.getSavedAtMillis();

            for (SpringComponent comp : graph.getComponents()) {
                jdbc.update(
                    "INSERT INTO spring_bean (type_qualified_name, project_root, analyzed_at_millis, "
                    + "source_path, line_number) VALUES (?, ?, ?, ?, ?)",
                    comp.getQualifiedName(), root, analyzedAt,
                    comp.getSourcePath(), comp.getLineNumber());
                for (String st : comp.getStereotypes()) {
                    jdbc.update(
                        "INSERT INTO spring_bean_stereotype (type_qualified_name, project_root, stereotype) VALUES (?, ?, ?)",
                        comp.getQualifiedName(), root, st);
                }
            }
            for (InjectionEdge edge : graph.getInjectionEdges()) {
                jdbc.update(
                    "INSERT INTO bean_injection (from_qualified_name, project_root, to_qualified_name, "
                    + "to_type_simple_name, kind, qualifier) VALUES (?, ?, ?, ?, ?, ?)",
                    edge.getFromQualifiedName(), root,
                    edge.getToQualifiedName(), edge.getToTypeSimpleName(),
                    edge.getKind() != null ? edge.getKind().name() : "CONSTRUCTOR",
                    edge.getQualifier());
            }
        }
    }

    private void insertMethods(String typeQn, String root, List<MethodInfo> methods, String visibility) {
        if (methods == null) return;
        for (MethodInfo m : methods) {
            KeyHolder kh = new GeneratedKeyHolder();
            jdbc.update(con -> {
                PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO method (declaring_type, project_root, name, visibility, "
                    + "return_type_name, source_path, line_number) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    new String[]{"id"});
                ps.setString(1, typeQn);
                ps.setString(2, root);
                ps.setString(3, m.getName());
                ps.setString(4, visibility);
                ps.setString(5, m.getReturnType());
                ps.setString(6, m.getSourcePath());
                ps.setInt(7, m.getLineNumber());
                return ps;
            }, kh);
            long methodId = kh.getKey().longValue();
            List<String> params = m.getParameterTypes();
            for (int i = 0; i < params.size(); i++) {
                jdbc.update(
                    "INSERT INTO method_parameter (method_id, position, type_name) VALUES (?, ?, ?)",
                    methodId, i, params.get(i));
            }
        }
    }

    // ── Load ─────────────────────────────────────────────────────────────────

    public AnalyzerSnapshot load(String projectRoot) {
        List<Map<String, Object>> projRows = jdbc.queryForList(
            "SELECT saved_at_millis, format_version FROM project WHERE project_root = ?", projectRoot);
        if (projRows.isEmpty()) {
            throw new NoSuchElementException("No snapshot in database for project root: " + projectRoot);
        }

        AnalyzerSnapshot snap = new AnalyzerSnapshot();
        snap.setProjectRoot(projectRoot);
        snap.setSavedAtMillis(toLong(projRows.get(0).get("saved_at_millis")));
        snap.setFormatVersion(((Number) projRows.get(0).get("format_version")).intValue());

        // ── Pre-load child data in bulk (avoid N+1) ──────────────────────────

        // annotations: typeQn → [annotationName, ...]
        Map<String, List<String>> annsByType = new LinkedHashMap<>();
        jdbc.queryForList(
            "SELECT type_qualified_name, annotation_name FROM type_annotation WHERE project_root = ? "
            + "ORDER BY type_qualified_name, annotation_name", projectRoot)
            .forEach(r -> annsByType
                .computeIfAbsent((String) r.get("type_qualified_name"), k -> new ArrayList<>())
                .add((String) r.get("annotation_name")));

        // implements: typeQn → [interfaceName, ...]
        Map<String, List<String>> implsByType = new LinkedHashMap<>();
        jdbc.queryForList(
            "SELECT type_qualified_name, interface_name FROM type_implements WHERE project_root = ? "
            + "ORDER BY type_qualified_name, interface_name", projectRoot)
            .forEach(r -> implsByType
                .computeIfAbsent((String) r.get("type_qualified_name"), k -> new ArrayList<>())
                .add((String) r.get("interface_name")));

        // fields: typeQn → {name → typeName}  (insertion order preserved)
        Map<String, Map<String, String>> fieldsByType = new LinkedHashMap<>();
        jdbc.queryForList(
            "SELECT declaring_type, name, type_name FROM field WHERE project_root = ? "
            + "ORDER BY declaring_type, id", projectRoot)
            .forEach(r -> fieldsByType
                .computeIfAbsent((String) r.get("declaring_type"), k -> new LinkedHashMap<>())
                .put((String) r.get("name"), (String) r.get("type_name")));

        // method parameters: methodId → [typeName at position 0, 1, ...]
        Map<Long, List<String>> paramsByMethod = new LinkedHashMap<>();
        jdbc.queryForList(
            "SELECT mp.method_id, mp.position, mp.type_name "
            + "FROM method_parameter mp JOIN method m ON mp.method_id = m.id "
            + "WHERE m.project_root = ? ORDER BY mp.method_id, mp.position", projectRoot)
            .forEach(r -> paramsByMethod
                .computeIfAbsent(toLong(r.get("method_id")), k -> new ArrayList<>())
                .add((String) r.get("type_name")));

        // methods grouped by type and visibility
        Map<String, List<MethodInfo>> publicByType    = new LinkedHashMap<>();
        Map<String, List<MethodInfo>> protectedByType = new LinkedHashMap<>();
        jdbc.queryForList(
            "SELECT id, declaring_type, name, visibility, return_type_name, source_path, line_number "
            + "FROM method WHERE project_root = ? ORDER BY declaring_type, id", projectRoot)
            .forEach(r -> {
                MethodInfo m = new MethodInfo();
                String typeQn = (String) r.get("declaring_type");
                m.setDeclaringTypeQualifiedName(typeQn);
                m.setName((String) r.get("name"));
                m.setReturnType((String) r.get("return_type_name"));
                m.setSourcePath((String) r.get("source_path"));
                m.setLineNumber(r.get("line_number") != null ? ((Number) r.get("line_number")).intValue() : 0);
                paramsByMethod.getOrDefault(toLong(r.get("id")), List.of())
                    .forEach(p -> m.getParameterTypes().add(p));
                if ("PROTECTED".equals(r.get("visibility"))) {
                    protectedByType.computeIfAbsent(typeQn, k -> new ArrayList<>()).add(m);
                } else {
                    publicByType.computeIfAbsent(typeQn, k -> new ArrayList<>()).add(m);
                }
            });

        // ── Types ────────────────────────────────────────────────────────────

        Map<String, List<String>> pkgToTypes = new LinkedHashMap<>();  // rebuilt from type rows
        List<TypeInfo> types = new ArrayList<>();
        jdbc.queryForList(
            "SELECT qualified_name, simple_name, package_name, kind, extends_type, source_path, line_number "
            + "FROM type WHERE project_root = ? ORDER BY qualified_name", projectRoot)
            .forEach(r -> {
                TypeInfo t  = new TypeInfo();
                String qn   = (String) r.get("qualified_name");
                String pkgN = (String) r.get("package_name");
                t.setQualifiedName(qn);
                t.setSimpleName((String) r.get("simple_name"));
                t.setPackageName(pkgN);
                try { t.setKind(TypeInfo.Kind.valueOf((String) r.get("kind"))); }
                catch (Exception ignored) { t.setKind(TypeInfo.Kind.CLASS); }
                t.setExtendsType((String) r.get("extends_type"));
                t.setSourcePath((String) r.get("source_path"));
                t.setLineNumber(r.get("line_number") != null ? ((Number) r.get("line_number")).intValue() : 0);

                annsByType.getOrDefault(qn,   List.of()).forEach(a -> t.getAnnotations().add(a));
                implsByType.getOrDefault(qn,  List.of()).forEach(i -> t.getImplementsTypes().add(i));
                Map<String, String> fields = fieldsByType.get(qn);
                if (fields != null) t.getFieldsByName().putAll(fields);
                publicByType.getOrDefault(qn,    List.of()).forEach(m -> t.getPublicMethods().add(m));
                protectedByType.getOrDefault(qn, List.of()).forEach(m -> t.getProtectedMethods().add(m));

                pkgToTypes.computeIfAbsent(pkgN != null ? pkgN : "", k -> new ArrayList<>()).add(qn);
                types.add(t);
            });
        snap.setTypes(types);

        // ── Packages ─────────────────────────────────────────────────────────

        // Start from packages in the DB, then fill typeQualifiedNames from what we built above
        List<AnalyzerSnapshot.PackageRecord> packages = new ArrayList<>();
        jdbc.queryForList(
            "SELECT qualified_name FROM package WHERE project_root = ? ORDER BY qualified_name", projectRoot)
            .forEach(r -> {
                String name = (String) r.get("qualified_name");
                AnalyzerSnapshot.PackageRecord pr = new AnalyzerSnapshot.PackageRecord();
                pr.setName(name);
                pr.setTypeQualifiedNames(new ArrayList<>(pkgToTypes.getOrDefault(name, List.of())));
                packages.add(pr);
            });
        snap.setPackages(packages);

        // ── Package imports ───────────────────────────────────────────────────

        Map<String, List<String>> pid = new LinkedHashMap<>();
        jdbc.queryForList(
            "SELECT from_package, to_package FROM package_import WHERE project_root = ? "
            + "ORDER BY from_package, to_package", projectRoot)
            .forEach(r -> pid
                .computeIfAbsent((String) r.get("from_package"), k -> new ArrayList<>())
                .add((String) r.get("to_package")));
        snap.setPackageImportDependencies(pid);

        // ── Spring graph ─────────────────────────────────────────────────────

        SpringComponentGraph graph = new SpringComponentGraph();
        graph.setProjectRoot(projectRoot);

        Map<String, Set<String>> stereoByBean = new LinkedHashMap<>();
        jdbc.queryForList(
            "SELECT type_qualified_name, stereotype FROM spring_bean_stereotype WHERE project_root = ?",
            projectRoot)
            .forEach(r -> stereoByBean
                .computeIfAbsent((String) r.get("type_qualified_name"), k -> new LinkedHashSet<>())
                .add((String) r.get("stereotype")));

        long maxAnalyzed = 0;
        for (Map<String, Object> row : jdbc.queryForList(
            "SELECT type_qualified_name, analyzed_at_millis, source_path, line_number "
            + "FROM spring_bean WHERE project_root = ? ORDER BY type_qualified_name", projectRoot)) {
            SpringComponent comp = new SpringComponent();
            String qn = (String) row.get("type_qualified_name");
            comp.setQualifiedName(qn);
            comp.setSourcePath((String) row.get("source_path"));
            comp.setLineNumber(row.get("line_number") != null ? ((Number) row.get("line_number")).intValue() : 0);
            comp.setStereotypes(stereoByBean.getOrDefault(qn, new LinkedHashSet<>()));
            long at = toLong(row.get("analyzed_at_millis"));
            if (at > maxAnalyzed) maxAnalyzed = at;
            graph.getComponents().add(comp);
        }
        graph.setAnalyzedAtMillis(maxAnalyzed > 0 ? maxAnalyzed : snap.getSavedAtMillis());

        jdbc.queryForList(
            "SELECT from_qualified_name, to_qualified_name, to_type_simple_name, kind, qualifier "
            + "FROM bean_injection WHERE project_root = ? ORDER BY id", projectRoot)
            .forEach(r -> {
                InjectionEdge edge = new InjectionEdge();
                edge.setFromQualifiedName((String) r.get("from_qualified_name"));
                edge.setToQualifiedName((String) r.get("to_qualified_name"));
                edge.setToTypeSimpleName((String) r.get("to_type_simple_name"));
                try { edge.setKind(InjectionEdge.Kind.valueOf((String) r.get("kind"))); }
                catch (Exception ignored) { edge.setKind(InjectionEdge.Kind.CONSTRUCTOR); }
                edge.setQualifier((String) r.get("qualifier"));
                graph.getInjectionEdges().add(edge);
            });

        snap.setSpringComponentGraph(graph);
        return snap;
    }

    /** Returns the qualified names of all project roots that have a snapshot in the database. */
    public List<String> listProjectRoots() {
        return jdbc.queryForList(
            "SELECT project_root FROM project ORDER BY project_root", String.class);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static long toLong(Object obj) {
        if (obj instanceof Long l)   return l;
        if (obj instanceof Number n) return n.longValue();
        return 0L;
    }
}
