package com.example.springurlmap;

import java.net.URI;
import java.nio.file.Path;

/**
 * One mapped HTTP endpoint from a Spring web controller.
 *
 * @param httpMethod       e.g. GET, POST, or "*" when not constrained on the mapping
 * @param fullPath         normalized URL path (leading slash, no duplicate slashes)
 * @param qualifiedClass   fully qualified controller type name
 * @param methodName       Java method name
 * @param sourceFile       path to the .java file
 * @param line             1-based line of the handler method declaration
 */
public record EndpointMapping(
        String httpMethod,
        String fullPath,
        String qualifiedClass,
        String methodName,
        Path sourceFile,
        int line
) {
    public String key() {
        return httpMethod + " " + fullPath;
    }

    /** HTTP method plus mapped URL pattern (e.g. {@code GET /api/users/{id}}). */
    public String restOperation() {
        return httpMethod + " " + fullPath;
    }

    /** {@code file:} URI to the source file (terminals may make it clickable). */
    public String sourceFileUri() {
        Path abs = sourceFile.toAbsolutePath().normalize();
        URI uri = abs.toUri();
        return uri.toString();
    }

    /** Absolute filesystem path to the source file with {@code :line} suffix. */
    public String sourcePathWithLine() {
        return sourceFile.toAbsolutePath().normalize() + ":" + line;
    }

    /** Java handler identity (type plus method). */
    public String handlerSignature() {
        return qualifiedClass + "#" + methodName;
    }

    @Override
    public String toString() {
        return key() + " -> " + handlerSignature() + " (" + sourcePathWithLine() + ")";
    }

    /**
     * REST line plus one {@code file:///…/File.java:line} link (IntelliJ Terminal: click / Cmd-click).
     * When {@code appendHandlerSignature}, appends {@code · Class#method} after the link (full map only).
     */
    public String formatSearchResultBlock(String indent, boolean appendHandlerSignature) {
        String fileLink = IntellijOpener.openFileAtLineUrl(sourceFile, line);
        String linked = TerminalHyperlinks.link(fileLink, fileLink);
        String fileLine = appendHandlerSignature ? linked + "  ·  " + handlerSignature() : linked;
        return indent + "REST operation   " + restOperation() + System.lineSeparator()
                + indent + "Source           " + fileLine;
    }
}
