package com.example.analyzer.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.example.analyzer")
public class WebAnalyzerApplication {
    public static void main(String[] args) {
        SpringApplication.run(WebAnalyzerApplication.class, args);
    }
}
