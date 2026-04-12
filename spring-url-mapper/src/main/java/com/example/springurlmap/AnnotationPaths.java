package com.example.springurlmap;

import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads {@code value}, {@code path}, and {@code method} from Spring mapping annotations as strings.
 */
final class AnnotationPaths {

    private AnnotationPaths() {}

    static List<String> extractPathPatterns(AnnotationExpr ann) {
        List<String> fromValue = new ArrayList<>();
        List<String> fromPath = new ArrayList<>();

        if (ann instanceof SingleMemberAnnotationExpr sm) {
            addStringExprs(sm.getMemberValue(), fromValue);
        } else if (ann instanceof NormalAnnotationExpr na) {
            for (MemberValuePair pair : na.getPairs()) {
                String n = pair.getNameAsString();
                if ("value".equals(n)) {
                    addStringExprs(pair.getValue(), fromValue);
                } else if ("path".equals(n)) {
                    addStringExprs(pair.getValue(), fromPath);
                }
            }
        }
        // Spring: path aliases value; if both set, path is used — we prefer path when non-empty
        if (!fromPath.isEmpty()) {
            return fromPath;
        }
        if (!fromValue.isEmpty()) {
            return fromValue;
        }
        return List.of("");
    }

    static List<String> extractRequestMethods(AnnotationExpr ann) {
        String simple = ann.getNameAsString();
        if (simple.equals("GetMapping") || simple.endsWith(".GetMapping")) {
            return List.of("GET");
        }
        if (simple.equals("PostMapping") || simple.endsWith(".PostMapping")) {
            return List.of("POST");
        }
        if (simple.equals("PutMapping") || simple.endsWith(".PutMapping")) {
            return List.of("PUT");
        }
        if (simple.equals("DeleteMapping") || simple.endsWith(".DeleteMapping")) {
            return List.of("DELETE");
        }
        if (simple.equals("PatchMapping") || simple.endsWith(".PatchMapping")) {
            return List.of("PATCH");
        }
        if (!(ann instanceof NormalAnnotationExpr na)) {
            return List.of();
        }
        if (!simple.equals("RequestMapping") && !simple.endsWith(".RequestMapping")) {
            return List.of();
        }
        for (MemberValuePair pair : na.getPairs()) {
            if (!"method".equals(pair.getNameAsString())) {
                continue;
            }
            List<String> methods = new ArrayList<>();
            extractRequestMethodConstants(pair.getValue(), methods);
            return methods;
        }
        return List.of();
    }

    private static void extractRequestMethodConstants(Expression expr, List<String> out) {
        if (expr instanceof FieldAccessExpr fa) {
            out.add(fa.getNameAsString());
            return;
        }
        if (expr instanceof NameExpr ne) {
            out.add(ne.getNameAsString());
            return;
        }
        if (expr instanceof ArrayInitializerExpr arr) {
            for (Expression e : arr.getValues()) {
                extractRequestMethodConstants(e, out);
            }
        }
    }

    private static void addStringExprs(Expression expr, List<String> target) {
        if (expr instanceof StringLiteralExpr sl) {
            target.add(sl.asString());
            return;
        }
        if (expr instanceof ArrayInitializerExpr arr) {
            for (Expression e : arr.getValues()) {
                addStringExprs(e, target);
            }
        }
    }

    static boolean isMappingAnnotation(AnnotationExpr ann) {
        String s = ann.getNameAsString();
        return s.equals("RequestMapping")
                || s.endsWith(".RequestMapping")
                || s.equals("GetMapping")
                || s.endsWith(".GetMapping")
                || s.equals("PostMapping")
                || s.endsWith(".PostMapping")
                || s.equals("PutMapping")
                || s.endsWith(".PutMapping")
                || s.equals("DeleteMapping")
                || s.endsWith(".DeleteMapping")
                || s.equals("PatchMapping")
                || s.endsWith(".PatchMapping");
    }

    static boolean isWebControllerAnnotation(AnnotationExpr ann) {
        String s = ann.getNameAsString();
        return s.equals("Controller")
                || s.equals("RestController")
                || s.endsWith(".Controller")
                || s.endsWith(".RestController");
    }
}
