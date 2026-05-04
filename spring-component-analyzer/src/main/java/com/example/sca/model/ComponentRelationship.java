package com.example.sca.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.couchbase.core.mapping.Document;
import org.springframework.data.couchbase.core.mapping.Field;
import org.springframework.data.couchbase.repository.Collection;

/**
 * Couchbase document representing a directed relationship between two
 * {@link JavaComponent} instances.
 *
 * <p>Relationship kinds:
 * <ul>
 *   <li>INJECTS_CONSTRUCTOR — component A receives B via constructor</li>
 *   <li>INJECTS_FIELD — component A has @Autowired field of type B</li>
 *   <li>INJECTS_SETTER — component A receives B via @Autowired setter</li>
 *   <li>EXTENDS — A extends B</li>
 *   <li>IMPLEMENTS — A implements interface B</li>
 *   <li>HAS_FIELD — A declares a field of type B (not necessarily Spring-managed)</li>
 * </ul>
 *
 * <p>Document ID: {@code <projectSlug>::<fromQn>::<kind>::<toSimple>}
 *
 * <p>Stored in the {@code relationships} collection.
 */
@Document
@Collection("relationships")
public class ComponentRelationship {

    public enum Kind {
        INJECTS_CONSTRUCTOR,
        INJECTS_FIELD,
        INJECTS_SETTER,
        EXTENDS,
        IMPLEMENTS,
        HAS_FIELD,
        /** Controller exposes a REST endpoint (toSimpleName = "httpMethod path"). */
        EXPOSES
    }

    @Id
    private String id;

    @Field private String projectRoot;
    @Field private String projectSlug;

    @Field private String fromQualifiedName;
    @Field private String fromSimpleName;
    @Field private ComponentType fromType;

    @Field private String toQualifiedName;   // may be null when unresolved
    @Field private String toSimpleName;
    @Field private ComponentType toType;

    @Field private Kind kind;

    /** Field name for HAS_FIELD / INJECTS_FIELD relationships. */
    @Field private String fieldName;

    public ComponentRelationship() {}

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public String getId()                               { return id; }
    public void   setId(String id)                      { this.id = id; }

    public String getProjectRoot()                      { return projectRoot; }
    public void   setProjectRoot(String r)              { this.projectRoot = r; }

    public String getProjectSlug()                      { return projectSlug; }
    public void   setProjectSlug(String s)              { this.projectSlug = s; }

    public String getFromQualifiedName()                { return fromQualifiedName; }
    public void   setFromQualifiedName(String q)        { this.fromQualifiedName = q; }

    public String getFromSimpleName()                   { return fromSimpleName; }
    public void   setFromSimpleName(String s)           { this.fromSimpleName = s; }

    public ComponentType getFromType()                  { return fromType; }
    public void          setFromType(ComponentType t)   { this.fromType = t; }

    public String getToQualifiedName()                  { return toQualifiedName; }
    public void   setToQualifiedName(String q)          { this.toQualifiedName = q; }

    public String getToSimpleName()                     { return toSimpleName; }
    public void   setToSimpleName(String s)             { this.toSimpleName = s; }

    public ComponentType getToType()                    { return toType; }
    public void          setToType(ComponentType t)     { this.toType = t; }

    public Kind getKind()                               { return kind; }
    public void setKind(Kind k)                         { this.kind = k; }

    public String getFieldName()                        { return fieldName; }
    public void   setFieldName(String f)                { this.fieldName = f; }

    @Override
    public String toString() {
        return fromSimpleName + " --[" + kind + "]--> " + toSimpleName;
    }
}
