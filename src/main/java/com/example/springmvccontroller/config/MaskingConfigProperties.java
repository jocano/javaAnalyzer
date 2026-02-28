package com.example.springmvccontroller.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration properties for field masking functionality.
 * Reads from application.yml under the 'masking' prefix.
 */
@Component
@ConfigurationProperties(prefix = "masking")
public class MaskingConfigProperties {
    
    private Map<String, String> fields = new HashMap<>();
    private Map<String, String> maskFunctions = new HashMap<>();
    
    public Map<String, String> getFields() {
        return fields;
    }
    
    public void setFields(Map<String, String> fields) {
        this.fields = fields;
    }
    
    public Map<String, String> getMaskFunctions() {
        return maskFunctions;
    }
    
    public void setMaskFunctions(Map<String, String> maskFunctions) {
        this.maskFunctions = maskFunctions;
    }

    /**
     * Loads configuration from a YAML file without Spring Boot.
     * Uses SnakeYAML library (included in Spring Boot dependencies).
     * 
     * @param file The YAML file to load from
     * @throws IOException If the file cannot be read or parsed
     */
    public void load(File file) throws IOException {
        load(Paths.get(file.getAbsolutePath()));
    }
    
    /**
     * Loads configuration from a YAML file path without Spring Boot.
     * 
     * @param filePath The path to the YAML file
     * @throws IOException If the file cannot be read or parsed
     */
    public void load(Path filePath) throws IOException {
        if (!Files.exists(filePath)) {
            throw new IOException("Configuration file not found: " + filePath);
        }
        
        // try {
        //     // Try using SnakeYAML (available in Spring Boot dependencies)
        //     loadWithSnakeYAML(filePath);
        // } catch (NoClassDefFoundError e) {
            // Fallback to manual YAML parsing if SnakeYAML not available
            loadWithManualParsing(filePath);
        // }
    }
    
    /**
     * Loads configuration using SnakeYAML library.
     * 
     * @param filePath Path to the YAML file
     * @throws IOException If parsing fails
     */
    private void loadWithSnakeYAML(Path filePath) throws IOException {
        try {
            // SnakeYAML is included in spring-boot-starter-web
            org.yaml.snakeyaml.Yaml yaml = new org.yaml.snakeyaml.Yaml();
            
            try (InputStream inputStream = Files.newInputStream(filePath)) {
                Map<String, Object> yamlData = yaml.load(inputStream);
                
                if (yamlData == null) {
                    throw new IOException("YAML file is empty or invalid");
                }
                
                // Extract the 'masking' section
                @SuppressWarnings("unchecked")
                Map<String, Object> maskingSection = (Map<String, Object>) yamlData.get("masking");
                
                if (maskingSection == null) {
                    System.err.println("Warning: No 'masking' section found in YAML file");
                    return;
                }
                
                // Extract 'fields' map
                @SuppressWarnings("unchecked")
                Map<String, Object> fieldsMap = (Map<String, Object>) maskingSection.get("fields");
                
                if (fieldsMap != null) {
                    this.fields = new HashMap<>();
                    for (Map.Entry<String, Object> entry : fieldsMap.entrySet()) {
                        this.fields.put(entry.getKey(), entry.getValue().toString());
                    }
                }
                
                // Extract 'mask-functions' map (kebab-case -> camelCase conversion)
                @SuppressWarnings("unchecked")
                Map<String, Object> maskFunctionsMap = (Map<String, Object>) maskingSection.get("mask-functions");
                
                if (maskFunctionsMap == null) {
                    // Try camelCase version
                    @SuppressWarnings("unchecked")
                    Map<String, Object> camelCase = (Map<String, Object>) maskingSection.get("maskFunctions");
                    maskFunctionsMap = camelCase;
                }
                
                if (maskFunctionsMap != null) {
                    this.maskFunctions = new HashMap<>();
                    for (Map.Entry<String, Object> entry : maskFunctionsMap.entrySet()) {
                        this.maskFunctions.put(entry.getKey(), entry.getValue().toString());
                    }
                }
            }
            
        } catch (Exception e) {
            throw new IOException("Failed to parse YAML file: " + e.getMessage(), e);
        }
    }
    
    /**
     * Fallback method: Manual YAML parsing for simple structures.
     * This is a basic parser and may not handle all YAML features.
     * 
     * @param filePath Path to the YAML file
     * @throws IOException If parsing fails
     */
    private void loadWithManualParsing(Path filePath) throws IOException {
        this.fields = new HashMap<>();
        this.maskFunctions = new HashMap<>();
        
        boolean inMaskingSection = false;
        boolean inFieldsSection = false;
        boolean inMaskFunctionsSection = false;
        
        for (String line : Files.readAllLines(filePath)) {
            String trimmed = line.trim();
            
            // Skip empty lines and comments
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            
            // Remove inline comments
            int commentIndex = trimmed.indexOf('#');
            if (commentIndex >= 0) {
                trimmed = trimmed.substring(0, commentIndex).trim();
            }
            
            // Detect section starts
            if (trimmed.equals("masking:")) {
                inMaskingSection = true;
                continue;
            }
            
            if (inMaskingSection && trimmed.equals("fields:")) {
                inFieldsSection = true;
                inMaskFunctionsSection = false;
                continue;
            }
            
            if (inMaskingSection && (trimmed.equals("mask-functions:") || trimmed.equals("maskFunctions:"))) {
                inFieldsSection = false;
                inMaskFunctionsSection = true;
                continue;
            }
            
            // Parse key-value pairs
            if (inFieldsSection && trimmed.contains(":")) {
                String[] parts = trimmed.split(":", 2);
                if (parts.length == 2) {
                    String key = parts[0].trim();
                    String value = parts[1].trim();
                    this.fields.put(key, value);
                }
            }
            
            if (inMaskFunctionsSection && trimmed.contains(":")) {
                String[] parts = trimmed.split(":", 2);
                if (parts.length == 2) {
                    String key = parts[0].trim();
                    String value = parts[1].trim().replaceAll("^[\"']|[\"']$", ""); // Remove quotes
                    this.maskFunctions.put(key, value);
                }
            }
            
            // Reset sections if we encounter a top-level key
            if (!line.startsWith(" ") && !line.startsWith("\t") && 
                trimmed.contains(":") && !trimmed.equals("masking:")) {
                inMaskingSection = false;
                inFieldsSection = false;
                inMaskFunctionsSection = false;
            }
        }
    }
    
    /**
     * Static factory method to load configuration from a file path.
     * 
     * @param filePath Path to the YAML file
     * @return Loaded MaskingConfigProperties instance
     * @throws IOException If the file cannot be read or parsed
     */
    public static MaskingConfigProperties loadFromFile(String filePath) throws IOException {
        MaskingConfigProperties config = new MaskingConfigProperties();
        config.load(Paths.get(filePath));
        return config;
    }
    
    /**
     * Static factory method to load configuration from a File object.
     * 
     * @param file The YAML file
     * @return Loaded MaskingConfigProperties instance
     * @throws IOException If the file cannot be read or parsed
     */
    public static MaskingConfigProperties loadFromFile(File file) throws IOException {
        MaskingConfigProperties config = new MaskingConfigProperties();
        config.load(file);
        return config;
    }
}

