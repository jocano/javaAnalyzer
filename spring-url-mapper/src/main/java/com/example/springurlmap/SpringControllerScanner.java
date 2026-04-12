package com.example.springurlmap;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ParserConfiguration.LanguageLevel;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Walks a directory tree, parses {@code .java} files, and collects Spring MVC mappings from
 * {@code @Controller} / {@code @RestController} types.
 */
public final class SpringControllerScanner {

    private static final JavaParser JAVA_PARSER =
            new JavaParser(new ParserConfiguration().setLanguageLevel(LanguageLevel.JAVA_17));

    private SpringControllerScanner() {}

    public static Map<String, EndpointMapping> scan(Path rootDir) throws IOException {
        Map<String, EndpointMapping> map = new LinkedHashMap<>();
        if (!Files.isDirectory(rootDir)) {
            return map;
        }
        try (Stream<Path> walk = Files.walk(rootDir)) {
            walk.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.toString().replace('\\', '/').contains("/target/"))
                    .filter(Files::isRegularFile)
                    .forEach(path -> parseFile(path, map));
        }
        return map;
    }

    private static void parseFile(Path file, Map<String, EndpointMapping> sink) {
        CompilationUnit cu;
        try {
            cu = JAVA_PARSER.parse(file).getResult().orElse(null);
        } catch (IOException e) {
            return;
        }
        if (cu == null) {
            return;
        }
        for (ClassOrInterfaceDeclaration type : cu.findAll(ClassOrInterfaceDeclaration.class)) {
            if (!type.isTopLevelType() && !isStaticNested(type)) {
                continue;
            }
            if (!hasWebControllerStereotype(type)) {
                continue;
            }
            collectFromController(cu, type, file, sink);
        }
    }

    private static boolean isStaticNested(ClassOrInterfaceDeclaration type) {
        return type.isNestedType() && type.isStatic();
    }

    private static boolean hasWebControllerStereotype(ClassOrInterfaceDeclaration type) {
        return type.getAnnotations().stream().anyMatch(AnnotationPaths::isWebControllerAnnotation);
    }

    /**
     * Line of the method <em>name</em> (1-based), not the first annotation — matches what devs expect as
     * “the handler method” and aligns better with IDE navigation.
     */
    private static int handlerDeclarationLine(MethodDeclaration method) {
        return method.getName().getBegin()
                .map(p -> p.line)
                .orElseGet(() -> method.getBegin().map(p -> p.line).orElse(1));
    }

    private static void collectFromController(
            CompilationUnit cu,
            ClassOrInterfaceDeclaration type,
            Path file,
            Map<String, EndpointMapping> sink) {

        TypeMappingPrefix prefix = readTypeLevelMappings(type);
        String qName = qualifiedName(cu, type);

        for (MethodDeclaration method : type.getMethods()) {
            if (!method.isPublic()) {
                continue;
            }
            List<AnnotationExpr> mappingAnns = method.getAnnotations().stream()
                    .filter(AnnotationPaths::isMappingAnnotation)
                    .toList();
            if (mappingAnns.isEmpty()) {
                continue;
            }
            int line = handlerDeclarationLine(method);
            for (AnnotationExpr ann : mappingAnns) {
                List<String> methodPaths = AnnotationPaths.extractPathPatterns(ann);
                if (methodPaths.isEmpty()) {
                    methodPaths = List.of("");
                }
                List<String> methodVerbs = AnnotationPaths.extractRequestMethods(ann);
                List<String> verbs = effectiveVerbs(prefix.typeVerbs(), methodVerbs);

                List<String> fullPaths = UrlPaths.cartesianPaths(prefix.paths(), methodPaths);
                for (String verb : verbs) {
                    for (String path : fullPaths) {
                        EndpointMapping em = new EndpointMapping(verb, path, qName, method.getNameAsString(), file, line);
                        sink.put(em.key(), em);
                    }
                }
            }
        }
    }

    private record TypeMappingPrefix(List<String> paths, List<String> typeVerbs) {}

    private static TypeMappingPrefix readTypeLevelMappings(ClassOrInterfaceDeclaration type) {
        List<String> paths = new ArrayList<>();
        List<String> typeVerbs = new ArrayList<>();
        for (AnnotationExpr ann : type.getAnnotations()) {
            if (AnnotationPaths.isWebControllerAnnotation(ann)) {
                continue;
            }
            if (!AnnotationPaths.isMappingAnnotation(ann)) {
                continue;
            }
            List<String> p = AnnotationPaths.extractPathPatterns(ann);
            if (p.isEmpty()) {
                paths.add("");
            } else {
                paths.addAll(p);
            }
            typeVerbs.addAll(AnnotationPaths.extractRequestMethods(ann));
        }
        if (paths.isEmpty()) {
            paths = List.of("");
        }
        paths = paths.stream().map(s -> s == null ? "" : s).distinct().toList();
        return new TypeMappingPrefix(paths, typeVerbs.stream().distinct().toList());
    }

    private static List<String> effectiveVerbs(List<String> typeVerbs, List<String> methodVerbs) {
        if (!methodVerbs.isEmpty()) {
            return methodVerbs;
        }
        if (!typeVerbs.isEmpty()) {
            return typeVerbs;
        }
        return List.of("*");
    }

    private static String qualifiedName(CompilationUnit cu, ClassOrInterfaceDeclaration type) {
        Node parent = type.getParentNode().orElse(null);
        if (parent instanceof ClassOrInterfaceDeclaration outer) {
            return qualifiedName(cu, outer) + "$" + type.getNameAsString();
        }
        String pkg = cu.getPackageDeclaration().map(p -> p.getNameAsString()).orElse("");
        return pkg.isEmpty() ? type.getNameAsString() : pkg + "." + type.getNameAsString();
    }
}
