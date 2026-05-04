package com.example.sca.parser;

import com.example.sca.model.ComponentRelationship;
import com.example.sca.model.ComponentType;
import com.example.sca.model.JavaComponent;
import com.example.sca.model.RestEndpoint;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Scans a Java/Spring project from its root directory, parses all {@code .java}
 * files with JavaParser, and returns the collected {@link JavaComponent} and
 * {@link ComponentRelationship} instances.
 */
@Component
public class JavaProjectParser {

    private static final Logger log = LoggerFactory.getLogger(JavaProjectParser.class);

    public record ParseResult(
        String projectRoot,
        List<JavaComponent>         components,
        List<ComponentRelationship> relationships,
        List<RestEndpoint>          endpoints
    ) {}

    /**
     * Parses the Java project rooted at {@code projectRoot}.
     *
     * @param projectRoot absolute path to the project's root directory
     * @return all components and relationships discovered
     */
    public ParseResult parse(String projectRoot) throws IOException {
        Path root = Path.of(projectRoot).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Not a directory: " + root);
        }

        ParserConfiguration cfg = new ParserConfiguration();
        cfg.setLanguageLevel(ParserConfiguration.LanguageLevel.BLEEDING_EDGE);
        StaticJavaParser.setConfiguration(cfg);

        String slug = slug(root);
        List<JavaComponent>        components    = new ArrayList<>();
        List<ComponentRelationship> relationships = new ArrayList<>();

        log.info("Scanning Java files under {}", root);
        List<Path> javaFiles = collectJavaFiles(root);
        log.info("Found {} .java files", javaFiles.size());

        // ── Build a QN → ComponentType lookup table (two-pass for resolution) ──
        List<ComponentVisitor.VisitResult> raw = new ArrayList<>();
        for (Path file : javaFiles) {
            try {
                CompilationUnit cu = StaticJavaParser.parse(file);
                ComponentVisitor visitor = new ComponentVisitor(
                    root.toString(), slug, file.toString());
                visitor.visit(cu, null);
                raw.addAll(visitor.results());
            } catch (Exception e) {
                log.warn("Skipping {} — parse error: {}", file, e.getMessage());
            }
        }

        // ── Build lookup: simpleOrQn → ComponentType ─────────────────────────
        Map<String, ComponentType> typeMap =
            ComponentVisitor.buildTypeMap(raw);

        // ── Emit final components, relationships, and endpoints ───────────────
        List<RestEndpoint> endpoints = new ArrayList<>();
        for (ComponentVisitor.VisitResult vr : raw) {
            components.add(vr.component());
            relationships.addAll(vr.relationships(typeMap));
            endpoints.addAll(vr.endpoints());
        }

        log.info("Parsed {} components, {} relationships, {} endpoints from {}",
            components.size(), relationships.size(), endpoints.size(), root);
        return new ParseResult(root.toString(), components, relationships, endpoints);
    }

    private static List<Path> collectJavaFiles(Path root) throws IOException {
        List<Path> result = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(p -> p.toString().endsWith(".java"))
                .filter(p -> !p.toString().contains("/test/"))
                .filter(p -> !p.toString().contains("\\test\\"))
                .filter(p -> !p.toString().contains("/target/"))
                .filter(p -> !p.toString().contains("\\target\\"))
                .forEach(result::add);
        }
        return result;
    }

    /** Derives a short slug from the last path segment of the project root. */
    static String slug(Path root) {
        return root.getFileName().toString()
                   .replaceAll("[^a-zA-Z0-9._-]", "-");
    }
}
