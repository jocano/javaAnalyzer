package com.example.analyzer.persistence;

import java.util.Map;

/**
 * A single result returned by a ChromaDB similarity query.
 *
 * @param id         The document ID ({@code "<project>:<qualifiedName>"}).
 * @param document   The stored text description of the Java element.
 * @param similarity Cosine-like similarity score (higher = more similar; 1 − L2 distance).
 * @param metadata   Arbitrary key/value pairs stored alongside the document.
 */
public record ChromaQueryResult(
        String              id,
        String              document,
        double              similarity,
        Map<String, Object> metadata
) {
    /** Convenience accessor: {@code metadata.get("qualified_name")} as a String. */
    public String qualifiedName() {
        Object v = metadata.get("qualified_name");
        return v != null ? v.toString() : id;
    }

    /** Convenience accessor: {@code metadata.get("source_path")} as a String. */
    public String sourcePath() {
        Object v = metadata.get("source_path");
        return v != null ? v.toString() : "";
    }

    /** Convenience accessor: {@code metadata.get("line_number")} as an int. */
    public int lineNumber() {
        Object v = metadata.get("line_number");
        if (v instanceof Number n) return n.intValue();
        return 0;
    }
}
