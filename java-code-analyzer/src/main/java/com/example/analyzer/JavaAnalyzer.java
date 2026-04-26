package com.example.analyzer;

import com.example.analyzer.model.MethodInfo;
import com.example.analyzer.model.PackageInfo;
import com.example.analyzer.model.ProjectModel;
import com.example.analyzer.model.TypeInfo;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Parses Java files with JavaParser and fills a ProjectModel.
 */
public class JavaAnalyzer {

    private static final String[] STEREOTYPE_ANNOTATIONS = {
        "Controller", "RestController", "Service", "Repository", "Component",
        "Entity", "Configuration", "Bean"
    };

    public static ProjectModel analyze(Path projectRoot, List<Path> javaFiles) {
        ProjectModel model = new ProjectModel();
        model.setProjectRoot(projectRoot.toAbsolutePath().toString());

        for (Path file : javaFiles) {
            try {
                parseFile(file, model);
            } catch (Exception e) {
                System.err.println("Parse error " + file + ": " + e.getMessage());
            }
        }
        return model;
    }

    private static void parseFile(Path file, ProjectModel model) {
        CompilationUnit cu;
        try {
            cu = StaticJavaParser.parse(file);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        Optional<String> pkg = cu.getPackageDeclaration().map(pd -> pd.getNameAsString());
        String packageName = pkg.orElse("");

        String absolutePath = file.toAbsolutePath().toString();

        for (TypeDeclaration<?> typeDecl : cu.getTypes()) {
            TypeInfo info = new TypeInfo();
            info.setPackageName(packageName);
            info.setSimpleName(typeDecl.getNameAsString());
            info.setQualifiedName(packageName.isEmpty() ? typeDecl.getNameAsString() : packageName + "." + typeDecl.getNameAsString());
            info.setSourcePath(absolutePath);
            typeDecl.getRange().ifPresent(r -> info.setLineNumber(r.begin.line));

            if (typeDecl instanceof ClassOrInterfaceDeclaration coi) {
                info.setKind(coi.isInterface() ? TypeInfo.Kind.INTERFACE : TypeInfo.Kind.CLASS);
                coi.getExtendedTypes().stream()
                    .map(t -> toQualifiedName(t))
                    .filter(s -> !s.isEmpty())
                    .findFirst()
                    .ifPresent(info::setExtendsType);
                coi.getImplementedTypes().stream()
                    .map(t -> toQualifiedName(t))
                    .forEach(info.getImplementsTypes()::add);
            } else {
                info.setKind(TypeInfo.Kind.CLASS);
            }

            typeDecl.getAnnotations().forEach(a -> {
                String name = a.getNameAsString();
                info.getAnnotations().add(name);
            });

            for (FieldDeclaration fd : typeDecl.getFields()) {
                Type t = fd.getCommonType();
                String typeName = (t instanceof ClassOrInterfaceType cit) ? cit.getNameAsString() : t.toString();
                if (typeName == null || typeName.isEmpty()) continue;
                typeName = typeName.replaceAll("<.*>", "").trim();
                if (!typeName.equals("void") && !typeName.equals("int") && !typeName.equals("long")
                    && !typeName.equals("boolean") && !typeName.equals("double") && !typeName.equals("float")) {
                    info.getFieldTypes().add(typeName);
                    for (VariableDeclarator v : fd.getVariables()) {
                        info.getFieldsByName().put(v.getNameAsString(), typeName);
                    }
                }
            }

            if (typeDecl instanceof RecordDeclaration rd) {
                for (Parameter p : rd.getParameters()) {
                    Type rt = p.getType();
                    String typeName = (rt instanceof ClassOrInterfaceType cit) ? cit.getNameAsString() : rt.toString();
                    if (typeName == null || typeName.isEmpty()) continue;
                    typeName = typeName.replaceAll("<.*>", "").trim();
                    if (!typeName.equals("void") && !typeName.equals("int") && !typeName.equals("long")
                        && !typeName.equals("boolean") && !typeName.equals("double") && !typeName.equals("float")) {
                        info.getFieldsByName().put(p.getNameAsString(), typeName);
                        info.getFieldTypes().add(typeName);
                    }
                }
            }

            for (MethodDeclaration md : typeDecl.getMethods()) {
                if (md.isPublic()) {
                    info.getPublicMethods().add(buildMethodInfo(info.getQualifiedName(), absolutePath, md));
                } else if (md.isProtected()) {
                    info.getProtectedMethods().add(buildMethodInfo(info.getQualifiedName(), absolutePath, md));
                }
            }

            model.getTypesByQualifiedName().put(info.getQualifiedName(), info);

            model.getPackages().computeIfAbsent(packageName, PackageInfo::new)
                .getTypeQualifiedNames().add(info.getQualifiedName());
        }
    }

    private static MethodInfo buildMethodInfo(String declaringTypeFqn, String absolutePath, MethodDeclaration md) {
        MethodInfo mi = new MethodInfo();
        mi.setDeclaringTypeQualifiedName(declaringTypeFqn);
        mi.setSourcePath(absolutePath);
        md.getRange().ifPresent(r -> mi.setLineNumber(r.begin.line));
        mi.setName(md.getNameAsString());
        Type ret = md.getType();
        mi.setReturnType(ret == null ? "void" : stripGenerics(ret.toString()));
        md.getParameters().forEach(p -> mi.getParameterTypes().add(stripGenerics(p.getType().toString())));
        return mi;
    }

    private static String toQualifiedName(ClassOrInterfaceType t) {
        return t.getNameWithScope();
    }

    private static String stripGenerics(String typeName) {
        if (typeName == null) return "";
        return typeName.replaceAll("<[^>]*>", "").trim();
    }
}
