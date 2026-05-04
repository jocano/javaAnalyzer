package com.example.sca.shell;

import com.example.sca.model.ComponentRelationship;
import com.example.sca.model.ComponentType;
import com.example.sca.model.EndpointParameter;
import com.example.sca.model.JavaComponent;
import com.example.sca.model.RestEndpoint;
import com.example.sca.service.AnalyzerService;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Interactive Spring Shell CLI for the Spring Component Analyzer.
 *
 * <p>Start the application normally; the shell prompt appears automatically.
 * Type {@code help} at the prompt to see all available commands.
 *
 * <h3>Available commands</h3>
 * <ul>
 *   <li>{@code analyze}         — parse a project and store results in Couchbase</li>
 *   <li>{@code list-components} — list all (or filtered) components for a project</li>
 *   <li>{@code find}            — find a component by name</li>
 *   <li>{@code dependencies}    — what does a component use?</li>
 *   <li>{@code dependents}      — what uses a component?</li>
 *   <li>{@code wiring}          — show controller→service or service→repository wiring</li>
 *   <li>{@code clear-project}   — remove all data for a project</li>
 * </ul>
 */
@ShellComponent
public class AnalyzerShell {

    private final AnalyzerService analyzerService;

    /** Most-recently analyzed / set project root (used as default). */
    private String currentProject = "";

    public AnalyzerShell(AnalyzerService analyzerService) {
        this.analyzerService = analyzerService;
    }

    // ── analyze ───────────────────────────────────────────────────────────────

    @ShellMethod(
        key  = "analyze",
        value = "Parse a Java/Spring project and persist components + relationships in Couchbase.\n"
              + "  Usage: analyze <projectRoot>\n"
              + "  Example: analyze /Users/me/IdeaProjects/brewery")
    public String analyze(
            @ShellOption(help = "Absolute path to the project root directory")
            String projectRoot) {
        try {
            currentProject = projectRoot;
            return analyzerService.analyze(projectRoot);
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    // ── use-project ───────────────────────────────────────────────────────────

    @ShellMethod(
        key   = "use-project",
        value = "Set the default project root used by subsequent commands.")
    public String useProject(
            @ShellOption(help = "Absolute path to the project root directory")
            String projectRoot) {
        currentProject = projectRoot;
        return "Default project set to: " + currentProject;
    }

    // ── list-components ───────────────────────────────────────────────────────

    @ShellMethod(
        key   = "list-components",
        value = "List components for a project.\n"
              + "  Options: --type CONTROLLER|SERVICE|REPOSITORY|ENTITY|COMPONENT|...\n"
              + "           --project <root>  (defaults to last analyzed project)")
    public String listComponents(
            @ShellOption(defaultValue = ShellOption.NULL, help = "Filter by stereotype")
            String type,
            @ShellOption(defaultValue = ShellOption.NULL, help = "Project root (overrides default)")
            String project) {

        String root = resolve(project);
        if (root == null) return noProject();

        List<JavaComponent> components = (type != null)
            ? analyzerService.listByType(root, ComponentType.valueOf(type.toUpperCase()))
            : analyzerService.listComponents(root);

        if (components.isEmpty()) return "No components found. Run 'analyze' first.";

        return components.stream()
            .map(c -> String.format("  %-12s  %s", c.getComponentType(), c.getQualifiedName()))
            .collect(Collectors.joining("\n",
                String.format("%-12s  %s%n%s%n", "TYPE", "QUALIFIED NAME",
                              "─".repeat(80)),
                String.format("%n  Total: %d", components.size())));
    }

    // ── find ──────────────────────────────────────────────────────────────────

    @ShellMethod(
        key   = "find",
        value = "Find a component by simple name (supports partial match).\n"
              + "  Example: find BeerController\n"
              + "           find Beer --project /path/to/project")
    public String find(
            @ShellOption(help = "Simple class name or partial name") String name,
            @ShellOption(defaultValue = ShellOption.NULL, help = "Project root") String project) {

        String root = resolve(project);
        if (root == null) return noProject();

        List<JavaComponent> hits = analyzerService.findByName(root, name);
        if (hits.isEmpty()) return "Not found: " + name;

        return hits.stream()
            .map(c -> formatComponent(c))
            .collect(Collectors.joining("\n\n"));
    }

    // ── dependencies ──────────────────────────────────────────────────────────

    @ShellMethod(
        key   = "dependencies",
        value = "Show what a component depends on (injects, extends, implements).\n"
              + "  Example: dependencies BeerController")
    public String dependencies(
            @ShellOption(help = "Simple class name") String name,
            @ShellOption(defaultValue = ShellOption.NULL, help = "Project root") String project) {

        String root = resolve(project);
        if (root == null) return noProject();

        List<ComponentRelationship> edges =
            analyzerService.dependencies(root, name);
        if (edges.isEmpty()) return name + " has no recorded dependencies.";

        return formatEdges(name + " depends on:", edges);
    }

    // ── dependents ────────────────────────────────────────────────────────────

    @ShellMethod(
        key   = "dependents",
        value = "Show what other components depend on (inject) a given component.\n"
              + "  Example: dependents BeerService")
    public String dependents(
            @ShellOption(help = "Simple class name") String name,
            @ShellOption(defaultValue = ShellOption.NULL, help = "Project root") String project) {

        String root = resolve(project);
        if (root == null) return noProject();

        List<ComponentRelationship> edges =
            analyzerService.dependents(root, name);
        if (edges.isEmpty()) return "Nothing depends on " + name + ".";

        return formatEdges("Components that use " + name + ":", edges);
    }

    // ── wiring ────────────────────────────────────────────────────────────────

    @ShellMethod(
        key   = "wiring",
        value = "Show high-level Spring wiring: controller→service or service→repository.\n"
              + "  Options: --layer controller-service | service-repository | all (default: all)")
    public String wiring(
            @ShellOption(defaultValue = "all",
                         help = "Layer: controller-service | service-repository | all")
            String layer,
            @ShellOption(defaultValue = ShellOption.NULL, help = "Project root") String project) {

        String root = resolve(project);
        if (root == null) return noProject();

        StringBuilder sb = new StringBuilder();
        if ("all".equalsIgnoreCase(layer) || "controller-service".equalsIgnoreCase(layer)) {
            List<ComponentRelationship> cs = analyzerService.controllerToServiceEdges(root);
            sb.append("Controller → Service\n").append("─".repeat(50)).append("\n");
            if (cs.isEmpty()) sb.append("  (none)\n");
            else cs.forEach(e -> sb.append("  ")
                                   .append(e.getFromSimpleName())
                                   .append("  →  ")
                                   .append(e.getToSimpleName())
                                   .append("  [")
                                   .append(e.getKind())
                                   .append("]\n"));
        }
        if ("all".equalsIgnoreCase(layer) || "service-repository".equalsIgnoreCase(layer)) {
            List<ComponentRelationship> sr = analyzerService.serviceToRepositoryEdges(root);
            sb.append("\nService → Repository\n").append("─".repeat(50)).append("\n");
            if (sr.isEmpty()) sb.append("  (none)\n");
            else sr.forEach(e -> sb.append("  ")
                                   .append(e.getFromSimpleName())
                                   .append("  →  ")
                                   .append(e.getToSimpleName())
                                   .append("  [")
                                   .append(e.getKind())
                                   .append("]\n"));
        }
        return sb.toString().trim();
    }

    // ── controllers ───────────────────────────────────────────────────────────

    @ShellMethod(key = "controllers", value = "List all controllers.")
    public String controllers(
            @ShellOption(defaultValue = ShellOption.NULL) String project) {
        return listComponents("CONTROLLER", project);
    }

    @ShellMethod(key = "services", value = "List all services.")
    public String services(
            @ShellOption(defaultValue = ShellOption.NULL) String project) {
        return listComponents("SERVICE", project);
    }

    @ShellMethod(key = "repositories", value = "List all repositories.")
    public String repositories(
            @ShellOption(defaultValue = ShellOption.NULL) String project) {
        return listComponents("REPOSITORY", project);
    }

    @ShellMethod(key = "entities", value = "List all JPA entities.")
    public String entities(
            @ShellOption(defaultValue = ShellOption.NULL) String project) {
        return listComponents("ENTITY", project);
    }

    // ── list-endpoints ────────────────────────────────────────────────────────

    @ShellMethod(
        key   = "list-endpoints",
        value = "List all REST endpoints discovered in the project.\n"
              + "  Options: --controller <name>   filter by controller simple name\n"
              + "           --method GET|POST|...  filter by HTTP method\n"
              + "           --path <fragment>       filter by URL path substring\n"
              + "  Example: list-endpoints\n"
              + "           list-endpoints --method GET\n"
              + "           list-endpoints --controller BeerController")
    public String listEndpoints(
            @ShellOption(defaultValue = ShellOption.NULL,
                         help = "Filter by controller simple name") String controller,
            @ShellOption(defaultValue = ShellOption.NULL,
                         help = "Filter by HTTP method (GET, POST, PUT, DELETE, PATCH)") String method,
            @ShellOption(defaultValue = ShellOption.NULL,
                         help = "Filter by URL path fragment") String path,
            @ShellOption(defaultValue = ShellOption.NULL,
                         help = "Project root (overrides default)") String project) {

        String root = resolve(project);
        if (root == null) return noProject();

        List<RestEndpoint> eps;
        if (controller != null && !controller.isBlank()) {
            eps = analyzerService.endpointsByController(root, controller);
        } else if (method != null && !method.isBlank()) {
            eps = analyzerService.endpointsByMethod(root, method);
        } else if (path != null && !path.isBlank()) {
            eps = analyzerService.endpointsByPath(root, path);
        } else {
            eps = analyzerService.listEndpoints(root);
        }

        if (eps.isEmpty()) return "No endpoints found. Run 'analyze' first.";

        return eps.stream()
            .map(this::formatEndpointRow)
            .collect(Collectors.joining("\n",
                String.format("%-7s  %-40s  %-30s  %s%n%s%n",
                              "METHOD", "PATH", "CONTROLLER", "HANDLER",
                              "─".repeat(100)),
                String.format("%n  Total: %d", eps.size())));
    }

    @ShellMethod(
        key   = "show-endpoint",
        value = "Show full details of REST endpoints for a controller method.\n"
              + "  Example: show-endpoint BeerController")
    public String showEndpoint(
            @ShellOption(help = "Controller simple name or partial name") String name,
            @ShellOption(defaultValue = ShellOption.NULL, help = "Project root") String project) {

        String root = resolve(project);
        if (root == null) return noProject();

        List<RestEndpoint> eps = analyzerService.endpointsByController(root, name);
        if (eps.isEmpty()) return "No endpoints found for controller: " + name;

        return eps.stream()
            .map(this::formatEndpointDetail)
            .collect(Collectors.joining("\n\n"));
    }

    // ── clear-project ─────────────────────────────────────────────────────────

    @ShellMethod(
        key   = "clear-project",
        value = "Remove all persisted data for a project from Couchbase.")
    public String clearProject(
            @ShellOption(defaultValue = ShellOption.NULL, help = "Project root") String project) {
        String root = resolve(project);
        if (root == null) return noProject();
        return analyzerService.clearProject(root);
    }

    // ── Formatting helpers ────────────────────────────────────────────────────

    private String formatComponent(JavaComponent c) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-12s  %s%n", c.getComponentType(), c.getQualifiedName()));
        sb.append(String.format("  source: %s:%d%n", c.getSourcePath(), c.getLineNumber()));
        if (c.getAnnotations() != null && !c.getAnnotations().isEmpty()) {
            sb.append("  annotations: ").append(c.getAnnotations()).append("\n");
        }
        if (c.getExtendsType() != null) {
            sb.append("  extends: ").append(c.getExtendsType()).append("\n");
        }
        if (c.getImplementsTypes() != null && !c.getImplementsTypes().isEmpty()) {
            sb.append("  implements: ").append(c.getImplementsTypes()).append("\n");
        }
        if (c.getMethods() != null && !c.getMethods().isEmpty()) {
            sb.append("  methods:\n");
            c.getMethods().forEach(m -> sb.append("    ").append(m).append("\n"));
        }
        return sb.toString().trim();
    }

    private String formatEdges(String header, List<ComponentRelationship> edges) {
        return edges.stream()
            .map(e -> String.format("  %-25s  %-22s  →  %-25s  [%s]",
                                    e.getFromSimpleName(),
                                    e.getFromType(),
                                    e.getToSimpleName(),
                                    e.getKind()))
            .collect(Collectors.joining("\n",
                header + "\n" + "─".repeat(80) + "\n", ""));
    }

    private String formatEndpointRow(RestEndpoint ep) {
        return String.format("%-7s  %-40s  %-30s  %s",
            ep.getHttpMethod(),
            ep.getPath(),
            ep.getControllerSimpleName(),
            ep.getHandlerMethod());
    }

    private String formatEndpointDetail(RestEndpoint ep) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%s %s%n", ep.getHttpMethod(), ep.getPath()));
        sb.append(String.format("  controller : %s%n", ep.getControllerQualifiedName()));
        sb.append(String.format("  handler    : %s → %s%n",
            ep.getHandlerMethod(), ep.getReturnType()));
        if (ep.getProduces() != null && !ep.getProduces().isEmpty())
            sb.append(String.format("  produces   : %s%n", ep.getProduces()));
        if (ep.getConsumes() != null && !ep.getConsumes().isEmpty())
            sb.append(String.format("  consumes   : %s%n", ep.getConsumes()));
        if (ep.getParameters() != null && !ep.getParameters().isEmpty()) {
            sb.append("  parameters :\n");
            ep.getParameters().forEach(p -> {
                String bind = p.getBinding() == EndpointParameter.Binding.NONE
                    ? "" : "  @" + p.getBinding().name();
                String req  = p.isRequired() ? "" : " (optional)";
                String def  = p.getDefaultValue() != null
                    ? " [default: " + p.getDefaultValue() + "]" : "";
                sb.append(String.format("    %-30s  %s%s%s%n",
                    p.getType() + " " + p.getName(), bind, req, def));
            });
        }
        if (ep.getSourcePath() != null)
            sb.append(String.format("  source     : %s:%d%n",
                ep.getSourcePath(), ep.getLineNumber()));
        return sb.toString().trim();
    }

    private String resolve(String explicit) {
        if (explicit != null && !explicit.isBlank()) return explicit;
        return currentProject.isBlank() ? null : currentProject;
    }

    private String noProject() {
        return "No project set. Run 'analyze <projectRoot>' or 'use-project <projectRoot>' first.";
    }
}
