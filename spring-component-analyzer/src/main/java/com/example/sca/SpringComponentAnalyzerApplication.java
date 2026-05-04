package com.example.sca;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Spring Component Analyzer.
 *
 * <h2>Quick start</h2>
 * <pre>
 *   # 1. Start Couchbase (Docker)
 *   docker run -d --name couchbase -p 8091-8096:8091-8096 -p 11210:11210 couchbase
 *   # Then open http://localhost:8091 and create a bucket named "java-analyzer"
 *   # with two collections: "components" and "relationships" in the _default scope.
 *
 *   # 2. Build and run
 *   cd spring-component-analyzer
 *   mvn spring-boot:run
 *
 *   # 3. Interactive shell — type at the shell prompt:
 *   analyze /path/to/your/spring-project
 *   list-components
 *   find BeerController
 *   dependencies BeerController
 *   dependents BeerService
 *   wiring
 *
 *   # 4. Web UI — open in browser:
 *   http://localhost:8080
 * </pre>
 */
@SpringBootApplication
public class SpringComponentAnalyzerApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringComponentAnalyzerApplication.class, args);
    }
}
