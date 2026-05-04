package com.example.sca.parser;

import com.example.sca.model.*;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.util.*;
import java.util.stream.Collectors;

/**
 * JavaParser AST visitor that extracts Spring components, their relationships,
 * and REST endpoints from a single compilation unit.
 */
public class ComponentVisitor extends VoidVisitorAdapter<Void> {

    /**
     * Intermediate result for a single type declaration.
     * Relationships are finalised after a full project type-map is available.
     */
    public record VisitResult(
        JavaComponent          component,
        List<RawEdge>          rawEdges,
        List<RestEndpoint>     endpoints
    ) {
        public List<ComponentRelationship> relationships(
                Map<String, ComponentType> typeMap) {
            List<ComponentRelationship> rels = rawEdges.stream()
                .map(e -> e.toRelationship(typeMap))
                .collect(Collectors.toList());
            // Add EXPOSES edges for each discovered endpoint
            for (RestEndpoint ep : endpoints) {
                ComponentRelationship r = new ComponentRelationship();
                r.setId(idFor(ep.getProjectSlug(),
                              ep.getControllerQualifiedName(),
                              "EXPOSES",
                              ep.getHttpMethod() + " " + ep.getPath()));
                r.setProjectRoot(ep.getProjectRoot());
                r.setProjectSlug(ep.getProjectSlug());
                r.setFromQualifiedName(ep.getControllerQualifiedName());
                r.setFromSimpleName(ep.getControllerSimpleName());
                r.setFromType(ComponentType.CONTROLLER);
                r.setToSimpleName(ep.getHttpMethod() + " " + ep.getPath());
                r.setToType(ComponentType.OTHER);
                r.setKind(ComponentRelationship.Kind.EXPOSES);
                r.setFieldName(ep.getHandlerMethod());
                rels.add(r);
            }
            return rels;
        }
    }

    /** Unresolved edge — toType filled in from typeMap later. */
    record RawEdge(
        String projectRoot, String projectSlug,
        String fromQn, String fromSimple, ComponentType fromType,
        String toSimple,
        ComponentRelationship.Kind kind,
        String fieldName
    ) {
        ComponentRelationship toRelationship(Map<String, ComponentType> typeMap) {
            ComponentRelationship r = new ComponentRelationship();
            r.setId(idFor(projectSlug, fromQn, kind.name(), toSimple));
            r.setProjectRoot(projectRoot);
            r.setProjectSlug(projectSlug);
            r.setFromQualifiedName(fromQn);
            r.setFromSimpleName(fromSimple);
            r.setFromType(fromType);
            r.setToSimpleName(toSimple);
            r.setToType(typeMap.getOrDefault(toSimple, ComponentType.OTHER));
            r.setKind(kind);
            r.setFieldName(fieldName);
            return r;
        }
    }

    // ── Spring stereotype annotations ─────────────────────────────────────────
    private static final Map<String, ComponentType> ANNOTATION_TYPE_MAP = Map.ofEntries(
        Map.entry("RestController",        ComponentType.CONTROLLER),
        Map.entry("Controller",            ComponentType.CONTROLLER),
        Map.entry("Service",               ComponentType.SERVICE),
        Map.entry("Repository",            ComponentType.REPOSITORY),
        Map.entry("Entity",                ComponentType.ENTITY),
        Map.entry("Component",             ComponentType.COMPONENT),
        Map.entry("Configuration",         ComponentType.CONFIGURATION),
        Map.entry("SpringBootApplication", ComponentType.CONFIGURATION),
        Map.entry("FeignClient",           ComponentType.REST_CLIENT),
        Map.entry("Aspect",                ComponentType.ASPECT)
    );

    /** Annotations that indicate an HTTP mapping; value = HTTP method string. */
    private static final Map<String, String> HTTP_MAPPING_MAP = Map.of(
        "GetMapping",     "GET",
        "PostMapping",    "POST",
        "PutMapping",     "PUT",
        "DeleteMapping",  "DELETE",
        "PatchMapping",   "PATCH",
        "RequestMapping", "ANY"
    );

    private static final Set<String> REPO_SUPERTYPES = Set.of(
        "JpaRepository", "CrudRepository", "PagingAndSortingRepository",
        "MongoRepository", "ReactiveCrudRepository"
    );

    private final String projectRoot;
    private final String projectSlug;
    private final String filePath;
    private final List<VisitResult> results = new ArrayList<>();

    public ComponentVisitor(String projectRoot, String projectSlug, String filePath) {
        this.projectRoot = projectRoot;
        this.projectSlug = projectSlug;
        this.filePath    = filePath;
    }

    public List<VisitResult> results() { return results; }

    // ── AST visiting ─────────────────────────────────────────────────────────

    @Override
    public void visit(ClassOrInterfaceDeclaration decl, Void arg) {
        processTypeDecl(decl);
        super.visit(decl, arg);
    }

    @Override
    public void visit(EnumDeclaration decl, Void arg) {
        JavaComponent comp = baseComponent(
            decl.getFullyQualifiedName().orElse(""), decl.getNameAsString());
        comp.setComponentType(ComponentType.ENUM);
        comp.setAnnotations(annotationNames(decl.getAnnotations()));
        comp.setMethods(List.of());
        comp.setFields(Map.of());
        comp.setImplementsTypes(List.of());
        results.add(new VisitResult(comp, List.of(), List.of()));
        super.visit(decl, arg);
    }

    // ── Type declaration processing ──────────────────────────────────────────

    private void processTypeDecl(ClassOrInterfaceDeclaration decl) {
        if (!decl.getFullyQualifiedName().isPresent()) return;

        String qn     = decl.getFullyQualifiedName().get();
        String simple = decl.getNameAsString();

        List<String> annNames = annotationNames(decl.getAnnotations());
        ComponentType type    = resolveType(decl, annNames);

        JavaComponent comp = baseComponent(qn, simple);
        comp.setComponentType(type);
        comp.setAnnotations(annNames);
        comp.setMethods(extractMethods(decl));
        comp.setFields(extractFieldMap(decl));
        comp.setExtendsType(decl.getExtendedTypes().isEmpty() ? null
            : decl.getExtendedTypes().get(0).getNameAsString());
        comp.setImplementsTypes(
            decl.getImplementedTypes().stream()
                .map(t -> t.getNameAsString())
                .collect(Collectors.toList()));

        List<RawEdge>      edges     = buildEdges(decl, qn, simple, type);
        List<RestEndpoint> endpoints = (type == ComponentType.CONTROLLER)
            ? extractEndpoints(decl, qn, simple) : List.of();

        results.add(new VisitResult(comp, edges, endpoints));
    }

    private JavaComponent baseComponent(String qn, String simple) {
        JavaComponent c = new JavaComponent();
        String[] parts = qn.split("\\.");
        String pkg = parts.length > 1
            ? String.join(".", Arrays.copyOf(parts, parts.length - 1)) : "";
        c.setId(idFor(projectSlug, qn));
        c.setProjectRoot(projectRoot);
        c.setProjectSlug(projectSlug);
        c.setQualifiedName(qn);
        c.setSimpleName(simple);
        c.setPackageName(pkg);
        c.setSourcePath(filePath);
        c.setLineNumber(0);
        return c;
    }

    // ── Type resolution ──────────────────────────────────────────────────────

    private ComponentType resolveType(ClassOrInterfaceDeclaration decl,
                                      List<String> annNames) {
        if (decl.isInterface()) return ComponentType.INTERFACE;
        for (String ann : annNames) {
            String key = ann.startsWith("@") ? ann.substring(1) : ann;
            ComponentType t = ANNOTATION_TYPE_MAP.get(key);
            if (t != null) return t;
        }
        for (var ext : decl.getExtendedTypes()) {
            if (REPO_SUPERTYPES.contains(ext.getNameAsString())) {
                return ComponentType.REPOSITORY;
            }
        }
        return ComponentType.OTHER;
    }

    // ── REST endpoint extraction ──────────────────────────────────────────────

    /**
     * Extracts all REST endpoints from a controller class.
     *
     * <p>Algorithm:
     * <ol>
     *   <li>Read the class-level {@code @RequestMapping} path prefix.</li>
     *   <li>For each method annotated with a mapping annotation, create a
     *       {@link RestEndpoint} combining the class prefix with the method path.</li>
     * </ol>
     */
    private List<RestEndpoint> extractEndpoints(ClassOrInterfaceDeclaration decl,
                                                String controllerQn,
                                                String controllerSimple) {
        String classPath = extractClassPath(decl);
        List<RestEndpoint> eps = new ArrayList<>();

        for (MethodDeclaration m : decl.getMethods()) {
            for (AnnotationExpr ann : m.getAnnotations()) {
                String annName = ann.getNameAsString();
                String httpMethod = HTTP_MAPPING_MAP.get(annName);
                if (httpMethod == null) continue;

                String methodPath = extractAnnotationPath(ann);

                // For @RequestMapping, check if a specific HTTP method is declared
                if ("ANY".equals(httpMethod)) {
                    httpMethod = extractRequestMappingMethod(ann);
                }

                String fullPath = normalizePath(classPath, methodPath);

                RestEndpoint ep = new RestEndpoint();
                ep.setId(idFor(projectSlug, controllerQn,
                               httpMethod, fullPath + "#" + m.getNameAsString()));
                ep.setProjectRoot(projectRoot);
                ep.setProjectSlug(projectSlug);
                ep.setControllerQualifiedName(controllerQn);
                ep.setControllerSimpleName(controllerSimple);
                ep.setHttpMethod(httpMethod);
                ep.setPath(fullPath);
                ep.setHandlerMethod(m.getNameAsString());
                ep.setReturnType(m.getTypeAsString());
                ep.setParameters(extractEndpointParameters(m));
                ep.setAnnotations(annotationNames(m.getAnnotations()));
                ep.setProduces(extractAnnotationStringList(ann, "produces"));
                ep.setConsumes(extractAnnotationStringList(ann, "consumes"));
                ep.setSourcePath(filePath);
                ep.setLineNumber(m.getBegin().map(p -> p.line).orElse(0));
                eps.add(ep);
                break; // one HTTP mapping annotation per method
            }
        }
        return eps;
    }

    /** Reads the class-level {@code @RequestMapping} path, or empty string. */
    private String extractClassPath(ClassOrInterfaceDeclaration decl) {
        for (AnnotationExpr ann : decl.getAnnotations()) {
            if ("RequestMapping".equals(ann.getNameAsString())) {
                return extractAnnotationPath(ann);
            }
        }
        return "";
    }

    /**
     * Extracts the primary path/value string from any mapping annotation.
     * Handles:
     * <ul>
     *   <li>{@code @GetMapping("/path")} — single-member</li>
     *   <li>{@code @GetMapping(value="/path")} — named pair</li>
     *   <li>{@code @GetMapping(path="/path")} — named pair</li>
     *   <li>Array value: first element is used</li>
     * </ul>
     */
    private String extractAnnotationPath(AnnotationExpr ann) {
        if (ann instanceof SingleMemberAnnotationExpr sma) {
            return stringValue(sma.getMemberValue());
        }
        if (ann instanceof NormalAnnotationExpr nae) {
            for (MemberValuePair p : nae.getPairs()) {
                String name = p.getNameAsString();
                if ("value".equals(name) || "path".equals(name)) {
                    return stringValue(p.getValue());
                }
            }
        }
        return "";
    }

    /**
     * Resolves the HTTP method from a {@code @RequestMapping}'s {@code method}
     * attribute. Returns {@code "ANY"} if not specified.
     */
    private String extractRequestMappingMethod(AnnotationExpr ann) {
        if (ann instanceof NormalAnnotationExpr nae) {
            for (MemberValuePair p : nae.getPairs()) {
                if ("method".equals(p.getNameAsString())) {
                    String raw = stringValue(p.getValue());
                    // e.g. "RequestMethod.GET" → "GET"
                    if (raw.contains(".")) return raw.substring(raw.lastIndexOf('.') + 1);
                    return raw;
                }
            }
        }
        return "ANY";
    }

    /** Extracts a string list from a named annotation attribute (for produces/consumes). */
    private List<String> extractAnnotationStringList(AnnotationExpr ann, String attr) {
        if (ann instanceof NormalAnnotationExpr nae) {
            for (MemberValuePair p : nae.getPairs()) {
                if (attr.equals(p.getNameAsString())) {
                    Expression val = p.getValue();
                    if (val instanceof ArrayInitializerExpr aie) {
                        return aie.getValues().stream()
                            .map(this::stringValue)
                            .collect(Collectors.toList());
                    }
                    return List.of(stringValue(val));
                }
            }
        }
        return List.of();
    }

    /**
     * Extracts the string value from an AST expression.
     * For arrays, returns the first element.
     */
    private String stringValue(Expression expr) {
        if (expr instanceof StringLiteralExpr s)  return s.asString();
        if (expr instanceof ArrayInitializerExpr a && !a.getValues().isEmpty()) {
            return stringValue(a.getValues().get(0));
        }
        // FieldAccessExpr, NameExpr, etc.
        return expr.toString().replace("\"", "");
    }

    /** Combines a class-level prefix and a method-level path into one URL path. */
    private String normalizePath(String prefix, String method) {
        String p = prefix.endsWith("/")   ? prefix.substring(0, prefix.length() - 1)   : prefix;
        String m = method.startsWith("/") ? method : (method.isEmpty() ? "" : "/" + method);
        String full = p + m;
        return full.isEmpty() ? "/" : full;
    }

    /** Extracts endpoint parameters with Spring MVC binding metadata. */
    private List<EndpointParameter> extractEndpointParameters(MethodDeclaration m) {
        List<EndpointParameter> params = new ArrayList<>();
        for (com.github.javaparser.ast.body.Parameter p : m.getParameters()) {
            String name = p.getNameAsString();
            String type = stripGenerics(p.getTypeAsString());

            EndpointParameter.Binding binding  = EndpointParameter.Binding.NONE;
            boolean required    = true;
            String  defaultVal  = null;

            for (AnnotationExpr pa : p.getAnnotations()) {
                switch (pa.getNameAsString()) {
                    case "PathVariable"   -> binding = EndpointParameter.Binding.PATH_VARIABLE;
                    case "RequestParam"   -> {
                        binding = EndpointParameter.Binding.REQUEST_PARAM;
                        required   = extractBooleanAttr(pa, "required", true);
                        defaultVal = extractStringAttr(pa, "defaultValue");
                    }
                    case "RequestBody"    -> binding = EndpointParameter.Binding.REQUEST_BODY;
                    case "RequestHeader"  -> binding = EndpointParameter.Binding.REQUEST_HEADER;
                    case "ModelAttribute" -> binding = EndpointParameter.Binding.MODEL_ATTRIBUTE;
                    default -> {}
                }
            }
            // Skip servlet infrastructure params (HttpServletRequest, etc.)
            if (!isServletParam(type)) {
                params.add(new EndpointParameter(name, type, binding, required, defaultVal));
            }
        }
        return params;
    }

    private boolean extractBooleanAttr(AnnotationExpr ann, String attr, boolean def) {
        if (ann instanceof NormalAnnotationExpr nae) {
            for (MemberValuePair p : nae.getPairs()) {
                if (attr.equals(p.getNameAsString())) {
                    return !"false".equalsIgnoreCase(p.getValue().toString());
                }
            }
        }
        return def;
    }

    private String extractStringAttr(AnnotationExpr ann, String attr) {
        if (ann instanceof NormalAnnotationExpr nae) {
            for (MemberValuePair p : nae.getPairs()) {
                if (attr.equals(p.getNameAsString())) {
                    return stringValue(p.getValue());
                }
            }
        }
        return null;
    }

    private static final Set<String> SERVLET_PARAMS = Set.of(
        "HttpServletRequest", "HttpServletResponse", "HttpSession",
        "Principal", "Locale", "InputStream", "OutputStream",
        "Reader", "Writer", "WebRequest", "NativeWebRequest"
    );

    private boolean isServletParam(String type) { return SERVLET_PARAMS.contains(type); }

    // ── Method signatures ────────────────────────────────────────────────────

    private List<MethodSignature> extractMethods(ClassOrInterfaceDeclaration decl) {
        List<MethodSignature> list = new ArrayList<>();
        for (MethodDeclaration m : decl.getMethods()) {
            if (m.getModifiers().contains(Modifier.privateModifier())) continue;
            list.add(new MethodSignature(
                m.getNameAsString(),
                m.getTypeAsString(),
                m.getParameters().stream()
                    .map(p -> p.getTypeAsString())
                    .collect(Collectors.toList()),
                m.getAccessSpecifier().asString().toUpperCase(),
                annotationNames(m.getAnnotations())
            ));
        }
        return list;
    }

    // ── Field map ─────────────────────────────────────────────────────────────

    private Map<String, String> extractFieldMap(ClassOrInterfaceDeclaration decl) {
        Map<String, String> map = new LinkedHashMap<>();
        for (FieldDeclaration fd : decl.getFields()) {
            String rawType = stripGenerics(fd.getElementType().asString());
            if (isPrimitive(rawType)) continue;
            fd.getVariables().forEach(v -> map.put(v.getNameAsString(), rawType));
        }
        return map;
    }

    // ── Relationship edges ────────────────────────────────────────────────────

    private List<RawEdge> buildEdges(ClassOrInterfaceDeclaration decl,
                                     String qn, String simple, ComponentType type) {
        List<RawEdge> edges = new ArrayList<>();

        if (!decl.isInterface() && !decl.getExtendedTypes().isEmpty()) {
            String parent = decl.getExtendedTypes().get(0).getNameAsString();
            if (!"Object".equals(parent))
                edges.add(edge(qn, simple, type, parent, ComponentRelationship.Kind.EXTENDS, null));
        }
        decl.getImplementedTypes().forEach(iface ->
            edges.add(edge(qn, simple, type, iface.getNameAsString(),
                           ComponentRelationship.Kind.IMPLEMENTS, null)));

        List<ConstructorDeclaration> ctors = decl.getConstructors();
        if (!ctors.isEmpty()) {
            ConstructorDeclaration ctor = ctors.stream()
                .max(Comparator.comparingInt(c -> c.getParameters().size()))
                .orElse(ctors.get(0));
            if (!ctor.getParameters().isEmpty()) {
                ctor.getParameters().forEach(p -> {
                    String pType = stripGenerics(p.getTypeAsString());
                    if (!isPrimitive(pType))
                        edges.add(edge(qn, simple, type, pType,
                                       ComponentRelationship.Kind.INJECTS_CONSTRUCTOR,
                                       p.getNameAsString()));
                });
            }
        }
        for (FieldDeclaration fd : decl.getFields()) {
            boolean isAutowired = fd.getAnnotations().stream()
                .anyMatch(a -> "Autowired".equals(a.getNameAsString())
                            || "Inject".equals(a.getNameAsString()));
            String rawType = stripGenerics(fd.getElementType().asString());
            if (isAutowired && !isPrimitive(rawType)) {
                fd.getVariables().forEach(v ->
                    edges.add(edge(qn, simple, type, rawType,
                                   ComponentRelationship.Kind.INJECTS_FIELD,
                                   v.getNameAsString())));
            } else if (!isPrimitive(rawType)) {
                fd.getVariables().forEach(v ->
                    edges.add(edge(qn, simple, type, rawType,
                                   ComponentRelationship.Kind.HAS_FIELD,
                                   v.getNameAsString())));
            }
        }
        return edges;
    }

    private RawEdge edge(String fromQn, String fromSimple, ComponentType fromType,
                         String toSimple, ComponentRelationship.Kind kind, String fieldName) {
        return new RawEdge(projectRoot, projectSlug,
                           fromQn, fromSimple, fromType, toSimple, kind, fieldName);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    static List<String> annotationNames(Iterable<AnnotationExpr> anns) {
        List<String> names = new ArrayList<>();
        anns.forEach(a -> names.add("@" + a.getNameAsString()));
        return names;
    }

    static String stripGenerics(String type) {
        int lt = type.indexOf('<');
        return lt > 0 ? type.substring(0, lt).trim() : type.trim();
    }

    private static final Set<String> PRIMITIVES = Set.of(
        "void", "int", "long", "double", "float", "boolean", "char", "byte",
        "short", "String", "Integer", "Long", "Double", "Float", "Boolean",
        "Char", "Byte", "Short", "Object", "Class"
    );
    static boolean isPrimitive(String type) { return PRIMITIVES.contains(type); }

    static String idFor(String slug, String qualifiedName) {
        return slug + "::" + qualifiedName;
    }
    static String idFor(String slug, String fromQn, String kind, String toSimple) {
        String raw = slug + "::" + fromQn + "::" + kind + "::" + toSimple;
        return raw.length() > 250 ? raw.substring(0, 250) : raw;
    }

    public static Map<String, ComponentType> buildTypeMap(List<VisitResult> results) {
        Map<String, ComponentType> map = new HashMap<>();
        for (VisitResult vr : results) {
            JavaComponent c = vr.component();
            map.put(c.getSimpleName(), c.getComponentType());
            String[] parts = c.getQualifiedName().split("\\.");
            if (parts.length > 0)
                map.putIfAbsent(parts[parts.length - 1], c.getComponentType());
        }
        return map;
    }
}
