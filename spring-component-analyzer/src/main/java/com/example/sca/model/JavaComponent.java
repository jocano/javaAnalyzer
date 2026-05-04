package com.example.sca.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.couchbase.core.mapping.Document;
import org.springframework.data.couchbase.core.mapping.Field;
import org.springframework.data.couchbase.repository.Collection;

import java.util.List;
import java.util.Map;

/**
 * Couchbase document representing a single Java type (class, interface, enum)
 * found during project analysis.
 *
 * <p>Document ID: {@code <projectSlug>::<qualifiedName>}
 *
 * <p>Stored in the {@code components} collection.
 */
@Document
@Collection("components")
public class JavaComponent {

    /** Document ID: {@code <projectSlug>::<qualifiedName>} */
    @Id
    private String id;

    /** Absolute path of the project root that was analyzed. */
    @Field
    private String projectRoot;

    /** Unique slug derived from the project root (last path segment). */
    @Field
    private String projectSlug;

    /** Fully qualified class name. */
    @Field
    private String qualifiedName;

    /** Simple class name. */
    @Field
    private String simpleName;

    /** Package name. */
    @Field
    private String packageName;

    /** Spring/Java stereotype. */
    @Field
    private ComponentType componentType;

    /** All annotations found on the type declaration, e.g. "@RestController". */
    @Field
    private List<String> annotations;

    /** Source file path (absolute). */
    @Field
    private String sourcePath;

    /** Line number of the type declaration. */
    @Field
    private int lineNumber;

    /** Superclass simple name (null if extends Object or nothing). */
    @Field
    private String extendsType;

    /** Implemented interface simple names. */
    @Field
    private List<String> implementsTypes;

    /**
     * Fields declared in this type.
     * Key: field name, Value: declared type (generics-stripped simple name).
     */
    @Field
    private Map<String, String> fields;

    /** Public method signatures. */
    @Field
    private List<MethodSignature> methods;

    public JavaComponent() {}

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public String getId()                         { return id; }
    public void   setId(String id)                { this.id = id; }

    public String getProjectRoot()                { return projectRoot; }
    public void   setProjectRoot(String r)        { this.projectRoot = r; }

    public String getProjectSlug()                { return projectSlug; }
    public void   setProjectSlug(String s)        { this.projectSlug = s; }

    public String getQualifiedName()              { return qualifiedName; }
    public void   setQualifiedName(String qn)     { this.qualifiedName = qn; }

    public String getSimpleName()                 { return simpleName; }
    public void   setSimpleName(String sn)        { this.simpleName = sn; }

    public String getPackageName()                { return packageName; }
    public void   setPackageName(String p)        { this.packageName = p; }

    public ComponentType getComponentType()           { return componentType; }
    public void          setComponentType(ComponentType t) { this.componentType = t; }

    public List<String> getAnnotations()                  { return annotations; }
    public void         setAnnotations(List<String> a)    { this.annotations = a; }

    public String getSourcePath()                 { return sourcePath; }
    public void   setSourcePath(String sp)        { this.sourcePath = sp; }

    public int  getLineNumber()                   { return lineNumber; }
    public void setLineNumber(int ln)             { this.lineNumber = ln; }

    public String getExtendsType()                { return extendsType; }
    public void   setExtendsType(String e)        { this.extendsType = e; }

    public List<String> getImplementsTypes()              { return implementsTypes; }
    public void         setImplementsTypes(List<String> i){ this.implementsTypes = i; }

    public Map<String, String> getFields()                    { return fields; }
    public void                setFields(Map<String, String> f){ this.fields = f; }

    public List<MethodSignature> getMethods()                     { return methods; }
    public void                  setMethods(List<MethodSignature> m){ this.methods = m; }
}
