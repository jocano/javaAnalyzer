package com.example.analyzer.seq;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Renders PlantUML to SVG via https://kroki.io.
 */
public final class PlantUmlSvgExporter {

    private static final URI KROKI_PLANTUML_SVG_ENDPOINT = URI.create("https://kroki.io/plantuml/svg");

    private PlantUmlSvgExporter() {}

    /**
     * Sends PlantUML source to Kroki and writes returned SVG to {@code targetSvg}.
     *
     * @return the path to the written SVG
     */
    public static Path exportSvg(String plantUml, Path targetSvg) throws IOException, InterruptedException {
        Path parent = targetSvg.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        HttpRequest request = HttpRequest.newBuilder()
            .uri(KROKI_PLANTUML_SVG_ENDPOINT)
            .header("Content-Type", "text/plain; charset=UTF-8")
            .POST(HttpRequest.BodyPublishers.ofString(plantUml, StandardCharsets.UTF_8))
            .build();
        HttpResponse<String> response = HttpClient.newHttpClient()
            .send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new IOException(
                "kroki.io returned HTTP " + response.statusCode()
                    + (response.body() == null || response.body().isBlank() ? "" : (": " + response.body()))
            );
        }
        Files.writeString(targetSvg, response.body(), StandardCharsets.UTF_8);
        return targetSvg;
    }
}
