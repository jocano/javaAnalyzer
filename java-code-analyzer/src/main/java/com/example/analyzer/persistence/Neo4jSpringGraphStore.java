package com.example.analyzer.persistence;

import com.example.analyzer.model.InjectionEdge;
import com.example.analyzer.model.SpringComponent;
import com.example.analyzer.model.SpringComponentGraph;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Persists the Spring wiring graph into Neo4j (nodes: {@code :SpringBean}, relationships: {@code :WIRES}).
 * <p>
 * Environment / URI: pass {@code uri} like {@code neo4j://localhost:7687}, plus auth.
 */
public class Neo4jSpringGraphStore implements AutoCloseable {

    private final Driver driver;
    private final String database;

    public Neo4jSpringGraphStore(String uri, String user, String password, String database) {
        this.driver = GraphDatabase.driver(uri, AuthTokens.basic(user, password));
        this.database = database == null || database.isBlank() ? "neo4j" : database;
    }

    /**
     * Replaces all nodes/relationships tagged with {@code projectRoot} for idempotent re-imports.
     */
    public void upsertGraph(SpringComponentGraph graph) {
        String root = graph.getProjectRoot() != null ? graph.getProjectRoot() : "";
        Set<String> beanFqns = new HashSet<>();
        for (SpringComponent c : graph.getComponents()) {
            beanFqns.add(c.getQualifiedName());
        }

        try (Session session = driver.session(SessionConfig.forDatabase(database))) {
            session.executeWrite(tx -> {
                tx.run(
                    "MATCH (n:SpringBean {projectRoot: $root}) DETACH DELETE n",
                    Map.of("root", root)
                );
                tx.run(
                    "MATCH (n:DependencyType {projectRoot: $root}) DETACH DELETE n",
                    Map.of("root", root)
                );
                return null;
            });

            session.executeWrite(tx -> {
                for (SpringComponent c : graph.getComponents()) {
                    Map<String, Object> m = new HashMap<>();
                    m.put("fqn", c.getQualifiedName());
                    m.put("root", root);
                    m.put("stereotypes", new java.util.ArrayList<>(c.getStereotypes()));
                    m.put("path", c.getSourcePath() != null ? c.getSourcePath() : "");
                    m.put("line", c.getLineNumber());
                    m.put("ts", graph.getAnalyzedAtMillis());
                    tx.run(
                        """
                            MERGE (n:SpringBean {fqn: $fqn, projectRoot: $root})
                            SET n.stereotypes = $stereotypes,
                                n.sourcePath = $path,
                                n.lineNumber = $line,
                                n.analyzedAt = $ts
                            """,
                        m
                    );
                }
                return null;
            });

            session.executeWrite(tx -> {
                for (InjectionEdge e : graph.getInjectionEdges()) {
                    String toFqn = e.getToQualifiedName();
                    String toSimple = e.getToTypeSimpleName() != null ? e.getToTypeSimpleName() : "";
                    boolean targetIsBean = toFqn != null && beanFqns.contains(toFqn);

                    if (toFqn != null && !toFqn.isBlank()) {
                        Map<String, Object> p = new HashMap<>();
                        p.put("from", e.getFromQualifiedName());
                        p.put("to", toFqn);
                        p.put("root", root);
                        p.put("kind", e.getKind().name());
                        p.put("qual", e.getQualifier() != null ? e.getQualifier() : "");
                        if (targetIsBean) {
                            tx.run(
                                """
                                    MATCH (a:SpringBean {fqn: $from, projectRoot: $root})
                                    MATCH (b:SpringBean {fqn: $to, projectRoot: $root})
                                    MERGE (a)-[r:WIRES]->(b)
                                    SET r.kind = $kind, r.qualifier = $qual
                                    """,
                                p
                            );
                        } else {
                            tx.run(
                                """
                                    MATCH (a:SpringBean {fqn: $from, projectRoot: $root})
                                    MERGE (b:DependencyType {fqn: $to, projectRoot: $root})
                                    MERGE (a)-[r:WIRES]->(b)
                                    SET r.kind = $kind, r.qualifier = $qual
                                    """,
                                p
                            );
                        }
                    } else if (!toSimple.isBlank()) {
                        Map<String, Object> p = new HashMap<>();
                        p.put("from", e.getFromQualifiedName());
                        p.put("simple", toSimple);
                        p.put("root", root);
                        p.put("kind", e.getKind().name());
                        p.put("qual", e.getQualifier() != null ? e.getQualifier() : "");
                        tx.run(
                            """
                                MATCH (a:SpringBean {fqn: $from, projectRoot: $root})
                                MERGE (b:DependencyType {simpleName: $simple, projectRoot: $root})
                                MERGE (a)-[r:WIRES]->(b)
                                SET r.kind = $kind, r.qualifier = $qual, r.unresolved = true
                                """,
                            p
                        );
                    }
                }
                return null;
            });
        }
    }

    @Override
    public void close() {
        driver.close();
    }
}
