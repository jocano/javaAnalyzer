package com.example.sca.service;

import com.example.sca.model.ComponentRelationship;
import com.example.sca.model.ComponentType;
import com.example.sca.model.JavaComponent;
import com.example.sca.model.RestEndpoint;
import com.example.sca.parser.JavaProjectParser;
import com.example.sca.repository.JavaComponentRepository;
import com.example.sca.repository.RelationshipRepository;
import com.example.sca.repository.RestEndpointRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Application-level service that orchestrates parsing, persistence, and queries.
 */
@Service
public class AnalyzerService {

    private static final Logger log = LoggerFactory.getLogger(AnalyzerService.class);

    private final JavaProjectParser        parser;
    private final JavaComponentRepository  componentRepo;
    private final RelationshipRepository   relationshipRepo;
    private final RestEndpointRepository   endpointRepo;

    public AnalyzerService(JavaProjectParser parser,
                           JavaComponentRepository componentRepo,
                           RelationshipRepository  relationshipRepo,
                           RestEndpointRepository  endpointRepo) {
        this.parser           = parser;
        this.componentRepo    = componentRepo;
        this.relationshipRepo = relationshipRepo;
        this.endpointRepo     = endpointRepo;
    }

    // ── Analysis & persistence ────────────────────────────────────────────────

    /**
     * Parses the project at {@code projectRoot}, clears any previously stored
     * data for that project, then saves all components and relationships to
     * Couchbase.
     *
     * @return summary message
     */
    public String analyze(String projectRoot) throws IOException {
        JavaProjectParser.ParseResult result = parser.parse(projectRoot);

        log.info("Clearing existing data for project {}", projectRoot);
        componentRepo.deleteByProjectRoot(projectRoot);
        relationshipRepo.deleteByProjectRoot(projectRoot);
        endpointRepo.deleteByProjectRoot(projectRoot);

        log.info("Saving {} components …", result.components().size());
        componentRepo.saveAll(result.components());

        log.info("Saving {} relationships …", result.relationships().size());
        relationshipRepo.saveAll(result.relationships());

        log.info("Saving {} REST endpoints …", result.endpoints().size());
        endpointRepo.saveAll(result.endpoints());

        return String.format(
            "Analysis complete for %s%n  %d components%n  %d relationships%n  %d REST endpoints",
            projectRoot,
            result.components().size(),
            result.relationships().size(),
            result.endpoints().size());
    }

    // ── Component queries ─────────────────────────────────────────────────────

    public List<JavaComponent> listComponents(String projectRoot) {
        return componentRepo.findByProjectRoot(projectRoot);
    }

    public List<JavaComponent> listByType(String projectRoot, ComponentType type) {
        return componentRepo.findByProjectRootAndComponentType(projectRoot, type);
    }

    /**
     * Finds components whose simple name matches {@code name} exactly
     * (case-sensitive) or, if no exact match, uses a LIKE pattern.
     */
    public List<JavaComponent> findByName(String projectRoot, String name) {
        List<JavaComponent> exact =
            componentRepo.findByProjectRootAndSimpleName(projectRoot, name);
        if (!exact.isEmpty()) return exact;
        return componentRepo.findByProjectRootAndSimpleNameContaining(
            projectRoot, "%" + name + "%");
    }

    public Optional<JavaComponent> findByQualifiedName(String projectRoot, String qn) {
        return componentRepo.findById(projectRoot.split("[/\\\\]")[
            projectRoot.split("[/\\\\]").length - 1] + "::" + qn);
    }

    // ── Relationship queries ──────────────────────────────────────────────────

    /**
     * Returns all components that {@code componentName} directly depends on
     * (i.e. injects or extends or implements).
     */
    public List<ComponentRelationship> dependencies(String projectRoot,
                                                    String componentName) {
        List<JavaComponent> matches = findByName(projectRoot, componentName);
        if (matches.isEmpty()) return List.of();
        String qn = matches.get(0).getQualifiedName();
        return relationshipRepo.findByProjectRootAndFromQualifiedName(projectRoot, qn);
    }

    /**
     * Returns all components that depend on (inject / extend / implement)
     * {@code componentName}.
     */
    public List<ComponentRelationship> dependents(String projectRoot,
                                                  String componentName) {
        return relationshipRepo.findByProjectRootAndToSimpleName(
            projectRoot, componentName);
    }

    /** All relationships in a project. */
    public List<ComponentRelationship> allRelationships(String projectRoot) {
        return relationshipRepo.findByProjectRoot(projectRoot);
    }

    /** Controller → Service edges. */
    public List<ComponentRelationship> controllerToServiceEdges(String projectRoot) {
        return relationshipRepo.findControllerToServiceEdges(projectRoot);
    }

    /** Service → Repository edges. */
    public List<ComponentRelationship> serviceToRepositoryEdges(String projectRoot) {
        return relationshipRepo.findServiceToRepositoryEdges(projectRoot);
    }

    // ── Admin ─────────────────────────────────────────────────────────────────

    // ── Endpoint queries ──────────────────────────────────────────────────────

    /** All REST endpoints in a project. */
    public List<RestEndpoint> listEndpoints(String projectRoot) {
        return endpointRepo.findByProjectRoot(projectRoot);
    }

    /** Endpoints exposed by a specific controller (by simple name). */
    public List<RestEndpoint> endpointsByController(String projectRoot, String controllerName) {
        // Try exact simple-name match first
        List<RestEndpoint> exact =
            endpointRepo.findByProjectRootAndControllerSimpleName(projectRoot, controllerName);
        if (!exact.isEmpty()) return exact;
        // Fall back: search by partial name using component lookup
        List<JavaComponent> hits = findByName(projectRoot, controllerName);
        if (hits.isEmpty()) return List.of();
        return endpointRepo.findByProjectRootAndControllerQualifiedName(
            projectRoot, hits.get(0).getQualifiedName());
    }

    /** Endpoints filtered by HTTP method (GET, POST, etc.). */
    public List<RestEndpoint> endpointsByMethod(String projectRoot, String httpMethod) {
        return endpointRepo.findByProjectRootAndHttpMethod(projectRoot, httpMethod.toUpperCase());
    }

    /** Endpoints whose path contains the given fragment. */
    public List<RestEndpoint> endpointsByPath(String projectRoot, String pathFragment) {
        return endpointRepo.findByProjectRootAndPathContaining(projectRoot, pathFragment);
    }

    // ── Admin ─────────────────────────────────────────────────────────────────

    /** Removes all data for a project from Couchbase. */
    public String clearProject(String projectRoot) {
        long c = componentRepo.findByProjectRoot(projectRoot).size();
        long r = relationshipRepo.findByProjectRoot(projectRoot).size();
        long e = endpointRepo.findByProjectRoot(projectRoot).size();
        componentRepo.deleteByProjectRoot(projectRoot);
        relationshipRepo.deleteByProjectRoot(projectRoot);
        endpointRepo.deleteByProjectRoot(projectRoot);
        return String.format(
            "Cleared %d components, %d relationships, %d endpoints for %s",
            c, r, e, projectRoot);
    }
}
