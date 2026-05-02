package com.example.analyzer.model.domain;

/**
 * Identity and provenance of a single analysis snapshot.
 *
 * @param projectRoot    Absolute path to the root of the scanned project.
 * @param savedAtMillis  Epoch-millis when the snapshot was persisted.
 * @param formatVersion  Schema version of the snapshot format.
 */
public record JavaProject(
        String projectRoot,
        long   savedAtMillis,
        int    formatVersion
) {}
