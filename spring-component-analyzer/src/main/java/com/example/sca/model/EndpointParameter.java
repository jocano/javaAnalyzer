package com.example.sca.model;

/**
 * A single parameter of a REST endpoint method, with its Spring MVC binding.
 * Stored as a nested value object inside {@link RestEndpoint}.
 */
public class EndpointParameter {

    public enum Binding {
        /** @PathVariable — bound from the URL path segment. */
        PATH_VARIABLE,
        /** @RequestParam — bound from a query string parameter. */
        REQUEST_PARAM,
        /** @RequestBody — deserialized from the request body (JSON/XML). */
        REQUEST_BODY,
        /** @RequestHeader — bound from an HTTP header. */
        REQUEST_HEADER,
        /** @ModelAttribute — bound from form data. */
        MODEL_ATTRIBUTE,
        /** No Spring binding annotation; plain Java parameter. */
        NONE
    }

    private String  name;
    private String  type;
    private Binding binding;
    private boolean required;
    private String  defaultValue;

    public EndpointParameter() {}

    public EndpointParameter(String name, String type, Binding binding,
                             boolean required, String defaultValue) {
        this.name         = name;
        this.type         = type;
        this.binding      = binding;
        this.required     = required;
        this.defaultValue = defaultValue;
    }

    public String  getName()                        { return name; }
    public void    setName(String n)                { this.name = n; }

    public String  getType()                        { return type; }
    public void    setType(String t)                { this.type = t; }

    public Binding getBinding()                     { return binding; }
    public void    setBinding(Binding b)            { this.binding = b; }

    public boolean isRequired()                     { return required; }
    public void    setRequired(boolean r)           { this.required = r; }

    public String  getDefaultValue()                { return defaultValue; }
    public void    setDefaultValue(String d)        { this.defaultValue = d; }

    @Override
    public String toString() {
        String bindingStr = binding == Binding.NONE ? "" : "@" + binding.name() + " ";
        return bindingStr + type + " " + name;
    }
}
