package com.example.analyzer.spring;

import com.example.analyzer.model.InjectionEdge;
import com.example.analyzer.model.ProjectModel;
import com.example.analyzer.model.SpringComponent;
import com.example.analyzer.model.SpringComponentGraph;
import com.example.analyzer.model.TypeInfo;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Extracts Spring stereotype components and DI relationships (constructor, field, setter).
 */
public final class SpringComponentAnalyzer {

    private static final Set<String> STEREOTYPE_SHORT_NAMES = Set.of(
        "Controller", "RestController", "Service", "Repository", "Component", "Configuration", "Entity"
    );

    private static final Set<String> SKIP_SIMPLE_TYPES = Set.of(
        "String", "Boolean", "Byte", "Short", "Integer", "Long", "Float", "Double", "Character",
        "Number", "Object", "Class", "Optional", "void", "Logger", "Void"
    );

    private SpringComponentAnalyzer() {
    }

    public static SpringComponentGraph analyze(ProjectModel model, List<Path> javaFiles) {
        Map<String, List<String>> simpleToQualified = buildSimpleNameIndex(model);
        SpringComponentGraph graph = new SpringComponentGraph();
        graph.setProjectRoot(model.getProjectRoot());

        for (Path file : javaFiles) {
            try {
                CompilationUnit cu = StaticJavaParser.parse(file);
                String pkg = cu.getPackageDeclaration().map(p -> p.getNameAsString()).orElse("");
                for (TypeDeclaration<?> td : cu.getTypes()) {
                    LinkedHashSet<String> stereotypes = extractStereotypes(td);
                    if (stereotypes.isEmpty()) {
                        continue;
                    }
                    String beanFqn = qualified(pkg, td.getNameAsString());
                    SpringComponent sc = new SpringComponent();
                    sc.setQualifiedName(beanFqn);
                    sc.getStereotypes().addAll(stereotypes);
                    sc.setSourcePath(file.toAbsolutePath().toString());
                    td.getBegin().ifPresent(pos -> sc.setLineNumber(pos.line));
                    graph.getComponents().add(sc);

                    if (td instanceof RecordDeclaration rec) {
                        for (Parameter p : rec.getParameters()) {
                            pushDependencyEdges(
                                beanFqn,
                                p.getType(),
                                cu,
                                pkg,
                                simpleToQualified,
                                graph,
                                InjectionEdge.Kind.CONSTRUCTOR,
                                qualifierOn(p)
                            );
                        }
                    } else if (td instanceof ClassOrInterfaceDeclaration coi && !coi.isInterface()) {
                        collectConstructorWiring(coi, beanFqn, cu, pkg, simpleToQualified, graph);
                        collectFieldWiring(coi, beanFqn, cu, pkg, simpleToQualified, graph);
                        collectSetterWiring(coi, beanFqn, cu, pkg, simpleToQualified, graph);
                    }
                }
            } catch (Exception e) {
                System.err.println("Spring wiring scan error " + file + ": " + e.getMessage());
            }
        }
        return graph;
    }

    private static Map<String, List<String>> buildSimpleNameIndex(ProjectModel model) {
        Map<String, List<String>> map = new HashMap<>();
        for (TypeInfo t : model.getTypesByQualifiedName().values()) {
            map.computeIfAbsent(t.getSimpleName(), k -> new ArrayList<>()).add(t.getQualifiedName());
        }
        for (List<String> list : map.values()) {
            list.sort(String::compareTo);
        }
        return map;
    }

    private static String qualified(String pkg, String simple) {
        return pkg.isEmpty() ? simple : pkg + "." + simple;
    }

    private static LinkedHashSet<String> extractStereotypes(TypeDeclaration<?> td) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (AnnotationExpr ae : td.getAnnotations()) {
            String name = ae.getNameAsString();
            String shortName = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1) : name;
            if (STEREOTYPE_SHORT_NAMES.contains(shortName)) {
                out.add(shortName);
            }
        }
        return out;
    }

    private static void collectConstructorWiring(
        ClassOrInterfaceDeclaration coi,
        String beanFqn,
        CompilationUnit cu,
        String pkg,
        Map<String, List<String>> index,
        SpringComponentGraph graph
    ) {
        List<ConstructorDeclaration> ctors = coi.getConstructors();
        if (ctors.isEmpty()) {
            return;
        }
        ConstructorDeclaration chosen;
        if (ctors.size() == 1) {
            chosen = ctors.get(0);
        } else {
            chosen = null;
            for (ConstructorDeclaration c : ctors) {
                if (hasAutowired(c.getAnnotations())) {
                    chosen = c;
                    break;
                }
            }
            if (chosen == null) {
                return;
            }
        }
        for (Parameter p : chosen.getParameters()) {
            pushDependencyEdges(beanFqn, p.getType(), cu, pkg, index, graph, InjectionEdge.Kind.CONSTRUCTOR, qualifierOn(p));
        }
    }

    private static void collectFieldWiring(
        ClassOrInterfaceDeclaration coi,
        String beanFqn,
        CompilationUnit cu,
        String pkg,
        Map<String, List<String>> index,
        SpringComponentGraph graph
    ) {
        for (FieldDeclaration fd : coi.getFields()) {
            if (!fd.getAnnotations().stream().anyMatch(SpringComponentAnalyzer::isFieldInjectionAnnotation)) {
                continue;
            }
            String q = qualifierFromAnnotations(fd.getAnnotations());
            for (var v : fd.getVariables()) {
                pushDependencyEdges(beanFqn, fd.getCommonType(), cu, pkg, index, graph, InjectionEdge.Kind.FIELD, q);
            }
        }
    }

    private static void collectSetterWiring(
        ClassOrInterfaceDeclaration coi,
        String beanFqn,
        CompilationUnit cu,
        String pkg,
        Map<String, List<String>> index,
        SpringComponentGraph graph
    ) {
        for (MethodDeclaration md : coi.getMethods()) {
            if (!md.isPublic() || md.getParameters().size() != 1) {
                continue;
            }
            if (!hasAutowired(md.getAnnotations())) {
                continue;
            }
            Parameter p = md.getParameter(0);
            pushDependencyEdges(
                beanFqn,
                p.getType(),
                cu,
                pkg,
                index,
                graph,
                InjectionEdge.Kind.SETTER,
                qualifierOn(p)
            );
        }
    }

    private static boolean hasAutowired(List<AnnotationExpr> anns) {
        return anns.stream().anyMatch(SpringComponentAnalyzer::isAutowiredLike);
    }

    private static boolean isAutowiredLike(AnnotationExpr ae) {
        String n = shortName(ae.getNameAsString());
        return "Autowired".equals(n) || "Inject".equals(n);
    }

    private static boolean isFieldInjectionAnnotation(AnnotationExpr ae) {
        String n = shortName(ae.getNameAsString());
        return "Autowired".equals(n) || "Inject".equals(n) || "Resource".equals(n);
    }

    private static String shortName(String name) {
        return name.contains(".") ? name.substring(name.lastIndexOf('.') + 1) : name;
    }

    private static String qualifierOn(Parameter p) {
        return p.getAnnotations().stream()
            .filter(a -> "Qualifier".equals(shortName(a.getNameAsString())))
            .map(SpringComponentAnalyzer::annotationStringValue)
            .filter(s -> s != null && !s.isEmpty())
            .findFirst()
            .orElse(null);
    }

    private static String qualifierFromAnnotations(List<AnnotationExpr> anns) {
        return anns.stream()
            .filter(a -> "Qualifier".equals(shortName(a.getNameAsString())))
            .map(SpringComponentAnalyzer::annotationStringValue)
            .filter(s -> s != null && !s.isEmpty())
            .findFirst()
            .orElse(null);
    }

    private static String annotationStringValue(AnnotationExpr ae) {
        if (ae instanceof SingleMemberAnnotationExpr sm) {
            return stripQuotes(sm.getMemberValue().toString());
        }
        if (ae instanceof NormalAnnotationExpr ne) {
            return ne.getPairs().stream()
                .filter(p -> "value".equals(p.getNameAsString()))
                .map(p -> stripQuotes(p.getValue().toString()))
                .findFirst()
                .orElse(null);
        }
        return null;
    }

    private static String stripQuotes(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        if (t.length() >= 2 && t.startsWith("\"") && t.endsWith("\"")) {
            return t.substring(1, t.length() - 1);
        }
        return t;
    }

    private static void pushDependencyEdges(
        String fromBean,
        Type type,
        CompilationUnit cu,
        String pkg,
        Map<String, List<String>> index,
        SpringComponentGraph graph,
        InjectionEdge.Kind kind,
        String qualifier
    ) {
        for (Type leaf : flattenInjectableTypes(type)) {
            Optional<String> resolved = resolveType(leaf, cu, pkg, index);
            InjectionEdge edge = new InjectionEdge();
            edge.setFromQualifiedName(fromBean);
            edge.setKind(kind);
            edge.setQualifier(qualifier);
            if (resolved.isPresent()) {
                edge.setToQualifiedName(resolved.get());
            } else {
                edge.setToTypeSimpleName(simpleNameFromType(leaf));
            }
            graph.getInjectionEdges().add(edge);
        }
    }

    /** For {@code List<A>, B} return A and B; for a single type return singleton list. */
    private static List<Type> flattenInjectableTypes(Type type) {
        List<Type> out = new ArrayList<>();
        Type t = unwrapArray(type);
        if (t == null || t.isPrimitiveType()) {
            return out;
        }
        if (t.isClassOrInterfaceType()) {
            ClassOrInterfaceType cit = t.asClassOrInterfaceType();
            Optional<com.github.javaparser.ast.NodeList<Type>> args = cit.getTypeArguments();
            if (args.isPresent() && !args.get().isEmpty()) {
                for (Type arg : args.get()) {
                    out.addAll(flattenInjectableTypes(arg));
                }
                return out;
            }
        }
        out.add(t);
        return out;
    }

    private static Type unwrapArray(Type type) {
        Type t = type;
        while (t != null && t.isArrayType()) {
            t = t.asArrayType().getComponentType();
        }
        return t;
    }

    private static String simpleNameFromType(Type type) {
        if (type == null || !type.isClassOrInterfaceType()) {
            return "";
        }
        return type.asClassOrInterfaceType().getNameAsString();
    }

    private static Optional<String> resolveType(
        Type type,
        CompilationUnit cu,
        String pkg,
        Map<String, List<String>> index
    ) {
        if (type == null || !type.isClassOrInterfaceType()) {
            return Optional.empty();
        }
        ClassOrInterfaceType cit = type.asClassOrInterfaceType();
        if (cit.getTypeArguments().isPresent() && !cit.getTypeArguments().get().isEmpty()) {
            return Optional.empty();
        }
        String simple = cit.getNameAsString();
        if (SKIP_SIMPLE_TYPES.contains(simple)) {
            return Optional.empty();
        }
        return resolveSimpleName(simple, cu, pkg, index);
    }

    private static Optional<String> resolveSimpleName(
        String simple,
        CompilationUnit cu,
        String pkg,
        Map<String, List<String>> index
    ) {
        for (var imp : cu.getImports()) {
            if (imp.isAsterisk() || imp.isStatic()) {
                continue;
            }
            String fqn = imp.getNameAsString();
            if (fqn.endsWith("." + simple)) {
                return Optional.of(fqn);
            }
        }
        List<String> cands = index.get(simple);
        if (cands == null || cands.isEmpty()) {
            return Optional.empty();
        }
        if (cands.size() == 1) {
            return Optional.of(cands.get(0));
        }
        if (!pkg.isEmpty()) {
            String inPkg = pkg + "." + simple;
            if (cands.contains(inPkg)) {
                return Optional.of(inPkg);
            }
        }
        return Optional.of(cands.get(0));
    }
}
