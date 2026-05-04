package com.example.sca.web;

import com.example.sca.model.ComponentRelationship;
import com.example.sca.model.ComponentType;
import com.example.sca.model.JavaComponent;
import com.example.sca.model.RestEndpoint;
import com.example.sca.service.AnalyzerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * REST API for the Spring Component Analyzer web UI.
 *
 * <p>All endpoints are under {@code /api/v1/analyzer}.
 */
@RestController
@RequestMapping("/api/v1/analyzer")
public class AnalyzerController {

    private final AnalyzerService analyzerService;

    public AnalyzerController(AnalyzerService analyzerService) {
        this.analyzerService = analyzerService;
    }

    // ── Analysis ─────────────────────────────────────────────────────────────

    /** POST /api/v1/analyzer/analyze  body: {"projectRoot": "/path/..."} */
    @PostMapping("/analyze")
    public ResponseEntity<?> analyze(@RequestBody Map<String, String> body) {
        String root = body.get("projectRoot");
        if (root == null || root.isBlank()) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "projectRoot is required"));
        }
        try {
            String summary = analyzerService.analyze(root);
            return ResponseEntity.ok(Map.of("message", summary));
        } catch (IOException e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("error", e.getMessage()));
        }
    }

    // ── Component queries ─────────────────────────────────────────────────────

    /** GET /api/v1/analyzer/components?project=...&type=... */
    @GetMapping("/components")
    public List<JavaComponent> listComponents(
            @RequestParam String project,
            @RequestParam(required = false) String type) {
        if (type != null && !type.isBlank()) {
            return analyzerService.listByType(project,
                ComponentType.valueOf(type.toUpperCase()));
        }
        return analyzerService.listComponents(project);
    }

    /** GET /api/v1/analyzer/find?project=...&name=... */
    @GetMapping("/find")
    public List<JavaComponent> find(
            @RequestParam String project,
            @RequestParam String name) {
        return analyzerService.findByName(project, name);
    }

    // ── Relationship queries ──────────────────────────────────────────────────

    /** GET /api/v1/analyzer/dependencies?project=...&name=... */
    @GetMapping("/dependencies")
    public List<ComponentRelationship> dependencies(
            @RequestParam String project,
            @RequestParam String name) {
        return analyzerService.dependencies(project, name);
    }

    /** GET /api/v1/analyzer/dependents?project=...&name=... */
    @GetMapping("/dependents")
    public List<ComponentRelationship> dependents(
            @RequestParam String project,
            @RequestParam String name) {
        return analyzerService.dependents(project, name);
    }

    /** GET /api/v1/analyzer/relationships?project=... */
    @GetMapping("/relationships")
    public List<ComponentRelationship> allRelationships(
            @RequestParam String project) {
        return analyzerService.allRelationships(project);
    }

    /** GET /api/v1/analyzer/wiring/controller-service?project=... */
    @GetMapping("/wiring/controller-service")
    public List<ComponentRelationship> controllerToService(
            @RequestParam String project) {
        return analyzerService.controllerToServiceEdges(project);
    }

    /** GET /api/v1/analyzer/wiring/service-repository?project=... */
    @GetMapping("/wiring/service-repository")
    public List<ComponentRelationship> serviceToRepository(
            @RequestParam String project) {
        return analyzerService.serviceToRepositoryEdges(project);
    }

    // ── Endpoint queries ──────────────────────────────────────────────────────

    /**
     * GET /api/v1/analyzer/endpoints?project=...
     *              [&controller=...] [&method=GET] [&path=/api/...]
     */
    @GetMapping("/endpoints")
    public List<RestEndpoint> listEndpoints(
            @RequestParam String project,
            @RequestParam(required = false) String controller,
            @RequestParam(required = false) String method,
            @RequestParam(required = false) String path) {
        if (controller != null && !controller.isBlank()) {
            return analyzerService.endpointsByController(project, controller);
        }
        if (method != null && !method.isBlank()) {
            return analyzerService.endpointsByMethod(project, method);
        }
        if (path != null && !path.isBlank()) {
            return analyzerService.endpointsByPath(project, path);
        }
        return analyzerService.listEndpoints(project);
    }

    // ── Admin ─────────────────────────────────────────────────────────────────

    /** DELETE /api/v1/analyzer/project?project=... */
    @DeleteMapping("/project")
    public ResponseEntity<Map<String, String>> clearProject(
            @RequestParam String project) {
        String msg = analyzerService.clearProject(project);
        return ResponseEntity.ok(Map.of("message", msg));
    }
}
