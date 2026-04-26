package com.example.analyzer.persistence;

import com.example.analyzer.model.SpringComponentGraph;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Persists {@link SpringComponentGraph} as pretty-printed JSON (document-oriented backup / interchange).
 */
public class JsonSpringGraphStore {

    private final ObjectMapper mapper = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT);

    public void save(SpringComponentGraph graph, Path file) throws IOException {
        mapper.writeValue(file.toFile(), graph);
    }

    public SpringComponentGraph load(Path file) throws IOException {
        return mapper.readValue(file.toFile(), SpringComponentGraph.class);
    }
}
