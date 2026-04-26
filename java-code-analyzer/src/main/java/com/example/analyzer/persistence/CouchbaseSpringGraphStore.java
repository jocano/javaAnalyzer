package com.example.analyzer.persistence;

import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.json.JsonArray;
import com.couchbase.client.java.json.JsonObject;
import com.example.analyzer.model.InjectionEdge;
import com.example.analyzer.model.SpringComponent;
import com.example.analyzer.model.SpringComponentGraph;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Persists the graph as Couchbase JSON documents (one doc per Spring bean + one index doc per project).
 */
public class CouchbaseSpringGraphStore implements AutoCloseable {

    private final Cluster cluster;
    private final com.couchbase.client.java.Collection collection;

    public CouchbaseSpringGraphStore(String connectionString, String username, String password, String bucketName) {
        this.cluster = Cluster.connect(connectionString, username, password);
        Bucket bucket = cluster.bucket(bucketName);
        bucket.waitUntilReady(Duration.ofSeconds(10));
        this.collection = bucket.defaultCollection();
    }

    public void upsertGraph(SpringComponentGraph graph) {
        String root = graph.getProjectRoot() != null ? graph.getProjectRoot() : "default";
        String safeId = Integer.toHexString(root.hashCode());

        for (SpringComponent c : graph.getComponents()) {
            List<JsonObject> outgoing = new ArrayList<>();
            for (InjectionEdge e : graph.getInjectionEdges()) {
                if (!c.getQualifiedName().equals(e.getFromQualifiedName())) {
                    continue;
                }
                JsonObject edge = JsonObject.create()
                    .put("kind", e.getKind().name())
                    .put("toQualifiedName", e.getToQualifiedName() != null ? e.getToQualifiedName() : "")
                    .put("toTypeSimpleName", e.getToTypeSimpleName() != null ? e.getToTypeSimpleName() : "")
                    .put("qualifier", e.getQualifier() != null ? e.getQualifier() : "");
                outgoing.add(edge);
            }
            JsonObject doc = JsonObject.create()
                .put("type", "spring-bean")
                .put("projectRoot", root)
                .put("qualifiedName", c.getQualifiedName())
                .put("stereotypes", stringArray(c.getStereotypes()))
                .put("sourcePath", c.getSourcePath() != null ? c.getSourcePath() : "")
                .put("lineNumber", c.getLineNumber())
                .put("analyzedAtMillis", graph.getAnalyzedAtMillis())
                .put("outgoingWiring", toJsonArray(outgoing));
            String key = "spring::" + safeId + "::" + c.getQualifiedName();
            collection.upsert(key, doc);
        }

        JsonObject summary = JsonObject.create()
            .put("type", "spring-graph-summary")
            .put("projectRoot", root)
            .put("analyzedAtMillis", graph.getAnalyzedAtMillis())
            .put("componentCount", graph.getComponents().size())
            .put("edgeCount", graph.getInjectionEdges().size());
        collection.upsert("spring::" + safeId + "::_summary", summary);
    }

    @Override
    public void close() {
        cluster.disconnect();
    }

    private static JsonArray toJsonArray(List<JsonObject> objects) {
        JsonArray arr = JsonArray.create();
        for (JsonObject o : objects) {
            arr.add(o);
        }
        return arr;
    }

    private static JsonArray stringArray(java.util.Collection<String> strings) {
        JsonArray arr = JsonArray.create();
        for (String s : strings) {
            arr.add(s);
        }
        return arr;
    }
}
