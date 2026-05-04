package com.example.sca.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.couchbase.core.mapping.Document;
import org.springframework.data.couchbase.core.mapping.Field;
import org.springframework.data.couchbase.repository.Collection;

import java.util.List;

/**
 * Couchbase document representing a single REST endpoint — one HTTP
 * method + path combination exposed by a Spring {@code @Controller} or
 * {@code @RestController}.
 *
 * <p>Document ID: {@code <projectSlug>::<controllerQn>::<httpMethod>::<path>}
 *
 * <p>Stored in the {@code endpoints} collection.
 *
 * <h3>Relationship to other documents</h3>
 * <ul>
 *   <li>The owning controller is referenced by {@link #controllerQualifiedName}.</li>
 *   <li>A {@link ComponentRelationship} of kind {@code EXPOSES} is also stored
 *       from the controller to each endpoint to make the relationship queryable
 *       via the relationships collection.</li>
 * </ul>
 */
@Document
@Collection("endpoints")
public class RestEndpoint {

    @Id
    private String id;

    @Field private String projectRoot;
    @Field private String projectSlug;

    /** Qualified name of the owning controller class. */
    @Field private String controllerQualifiedName;
    /** Simple name of the owning controller class. */
    @Field private String controllerSimpleName;

    /** HTTP method: GET, POST, PUT, DELETE, PATCH, or ANY. */
    @Field private String httpMethod;

    /**
     * Full URL path (class-level prefix + method-level path).
     * Example: {@code /api/v1/beers/{beerId}}
     */
    @Field private String path;

    /** Java method name that handles the request. */
    @Field private String handlerMethod;

    /** Declared return type of the handler method. */
    @Field private String returnType;

    /** All parameters of the handler method, with Spring MVC binding metadata. */
    @Field private List<EndpointParameter> parameters;

    /** Media types this endpoint produces (from {@code produces} attribute). */
    @Field private List<String> produces;

    /** Media types this endpoint consumes (from {@code consumes} attribute). */
    @Field private List<String> consumes;

    /** All annotations on the handler method. */
    @Field private List<String> annotations;

    @Field private String sourcePath;
    @Field private int    lineNumber;

    public RestEndpoint() {}

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public String getId()                               { return id; }
    public void   setId(String id)                      { this.id = id; }

    public String getProjectRoot()                      { return projectRoot; }
    public void   setProjectRoot(String r)              { this.projectRoot = r; }

    public String getProjectSlug()                      { return projectSlug; }
    public void   setProjectSlug(String s)              { this.projectSlug = s; }

    public String getControllerQualifiedName()          { return controllerQualifiedName; }
    public void   setControllerQualifiedName(String q)  { this.controllerQualifiedName = q; }

    public String getControllerSimpleName()             { return controllerSimpleName; }
    public void   setControllerSimpleName(String s)     { this.controllerSimpleName = s; }

    public String getHttpMethod()                       { return httpMethod; }
    public void   setHttpMethod(String m)               { this.httpMethod = m; }

    public String getPath()                             { return path; }
    public void   setPath(String p)                     { this.path = p; }

    public String getHandlerMethod()                    { return handlerMethod; }
    public void   setHandlerMethod(String m)            { this.handlerMethod = m; }

    public String getReturnType()                       { return returnType; }
    public void   setReturnType(String r)               { this.returnType = r; }

    public List<EndpointParameter> getParameters()                      { return parameters; }
    public void                    setParameters(List<EndpointParameter> p) { this.parameters = p; }

    public List<String> getProduces()                   { return produces; }
    public void         setProduces(List<String> p)     { this.produces = p; }

    public List<String> getConsumes()                   { return consumes; }
    public void         setConsumes(List<String> c)     { this.consumes = c; }

    public List<String> getAnnotations()                { return annotations; }
    public void         setAnnotations(List<String> a)  { this.annotations = a; }

    public String getSourcePath()                       { return sourcePath; }
    public void   setSourcePath(String sp)              { this.sourcePath = sp; }

    public int  getLineNumber()                         { return lineNumber; }
    public void setLineNumber(int ln)                   { this.lineNumber = ln; }

    @Override
    public String toString() {
        return httpMethod + " " + path + "  [" + controllerSimpleName + "#" + handlerMethod + "]";
    }
}
