package com.example.sca.model;

import java.util.List;

/**
 * Lightweight representation of a method found during parsing.
 * Stored as a nested object inside {@link JavaComponent}.
 */
public class MethodSignature {

    private String name;
    private String returnType;
    private List<String> parameterTypes;
    private String visibility;       // PUBLIC, PROTECTED, PACKAGE, PRIVATE
    private List<String> annotations;

    public MethodSignature() {}

    public MethodSignature(String name, String returnType,
                           List<String> parameterTypes,
                           String visibility,
                           List<String> annotations) {
        this.name           = name;
        this.returnType     = returnType;
        this.parameterTypes = parameterTypes;
        this.visibility     = visibility;
        this.annotations    = annotations;
    }

    public String getName()                   { return name; }
    public void   setName(String name)        { this.name = name; }

    public String getReturnType()             { return returnType; }
    public void   setReturnType(String r)     { this.returnType = r; }

    public List<String> getParameterTypes()           { return parameterTypes; }
    public void         setParameterTypes(List<String> p) { this.parameterTypes = p; }

    public String getVisibility()             { return visibility; }
    public void   setVisibility(String v)     { this.visibility = v; }

    public List<String> getAnnotations()              { return annotations; }
    public void         setAnnotations(List<String> a){ this.annotations = a; }

    @Override
    public String toString() {
        return visibility + " " + returnType + " " + name
               + "(" + String.join(", ", parameterTypes) + ")";
    }
}
