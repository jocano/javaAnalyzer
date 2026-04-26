package com.example.analyzer.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Reads/writes {@link AnalyzerSnapshot} as pretty-printed JSON.
 */
public class JsonAnalyzerSnapshotStore {

    private final ObjectMapper mapper = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT);

    public void save(AnalyzerSnapshot snapshot, Path file) throws IOException {
        mapper.writeValue(file.toFile(), snapshot);
    }

    public AnalyzerSnapshot load(Path file) throws IOException {
        return mapper.readValue(file.toFile(), AnalyzerSnapshot.class);
    }
}
