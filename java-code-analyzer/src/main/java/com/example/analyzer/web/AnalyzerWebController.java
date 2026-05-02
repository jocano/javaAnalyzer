package com.example.analyzer.web;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AnalyzerWebController {
    private final AnalyzerRuntime runtime;

    public AnalyzerWebController(AnalyzerRuntime runtime) {
        this.runtime = runtime;
    }

    @GetMapping("/commands")
    public Map<String, List<String>> commands() {
        return Map.of("commands", runtime.availableCommands());
    }

    @PostMapping(value = "/execute", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public CommandResult execute(@RequestBody CommandRequest req) {
        if (req == null || req.getCommand() == null) {
            return CommandResult.error("Request body must include: {\"command\":\"...\"}");
        }
        return runtime.execute(req.getCommand());
    }
}
