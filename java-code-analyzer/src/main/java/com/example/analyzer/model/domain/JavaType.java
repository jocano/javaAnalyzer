package com.example.analyzer.model.domain;

import java.util.List;

/**
 * Complete structural metadata for a Java type declaration (class, interface, enum, etc.).
 *
 * <p>Cross-references to other types ({@code extendsTypeName}, {@code implementsTypeNames},
 * field/method type names) are stored as raw strings rather than object references so that
 * external or unresolved types can be represented without requiring them to exist in the model.
 *
 * @param qualifiedName       Fully qualified name (unique identifier within a project snapshot).
 * @param simpleName          Unqualified class name.
 * @param packageName         Owning package qualified name (may be {@code ""} for the default package).
 * @param kind                Structural kind (class, interface, enum …).
 * @param annotations         Simple annotation names applied to the type declaration (e.g. {@code "Service"}).
 * @param extendsTypeName     Simple or qualified supertype name, or {@code null}.
 * @param implementsTypeNames Simple or qualified names of implemented interfaces.
 * @param fields              Declared fields.
 * @param methods             Declared public and protected methods.
 * @param sourcePath          Absolute path to the source file, or {@code null} if unavailable.
 * @param lineNumber          1-based line of the type declaration, or {@code 0} if unknown.
 */
public record JavaType(
        String           qualifiedName,
        String           simpleName,
        String           packageName,
        TypeKind         kind,
        List<String>     annotations,
        String           extendsTypeName,
        List<String>     implementsTypeNames,
        List<JavaField>  fields,
        List<JavaMethod> methods,
        String           sourcePath,
        int              lineNumber
) {}
