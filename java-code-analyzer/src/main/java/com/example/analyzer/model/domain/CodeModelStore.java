package com.example.analyzer.model.domain;

/**
 * Port: persistence back-end for a {@link CodeModel} snapshot.
 *
 * <p>Concrete implementations may target JSON files, a relational database,
 * or a graph database. The interface is intentionally minimal; richer query
 * capabilities belong in a separate query/repository layer.
 */
public interface CodeModelStore {

    /**
     * Persist {@code model}, replacing any existing snapshot for the same
     * {@link JavaProject#projectRoot()}.
     */
    void save(CodeModel model);

    /**
     * Load the most recently saved snapshot for the given project root.
     *
     * @param projectRoot Absolute path that was used as {@link JavaProject#projectRoot()}.
     * @return The stored model.
     * @throws java.util.NoSuchElementException if no snapshot exists for {@code projectRoot}.
     */
    CodeModel load(String projectRoot);
}
