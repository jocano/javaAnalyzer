package com.example.springmvccontroller.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.example.springmvccontroller.config.MaskingConfigProperties;

import io.jsonwebtoken.lang.Assert;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Utility class that analyzes a class and matches its properties to MaskFunctions
 * using the maskedFields map matching logic (exact, prefix, and Soundex matching).
 * Can generate a new class with @MaskedField annotations.
 */
@Component
public class ClassMaskingUtility {
    
    private static final Logger logger = LoggerFactory.getLogger(ClassMaskingUtility.class);
    private Map<String, Function<String, String>> maskedFields = new HashMap<>();
    
    // @Autowired
    // private UsersAllInterceptor usersAllInterceptor;
    
    /**
     * Analyzes a class and returns a map of field names to their corresponding
     * MaskFunctions enum type names.
     * 
     * @param clazz The class to analyze
     * @return Map of field name -> MaskFunctions enum type name (e.g., "PASSWORD_TYPE")
     */
    public Map<String, String> analyzeClass(Class<?> clazz, MaskingConfigProperties config) {
        Assert.notNull(config, "MaskingConfigProperties cannot be null");
        initDefaultMaskingFields(config);
        Map<String, Function<String, String>> maskedFields = getMaskedFields();
        Map<Function<String, String>, String> functionToEnumMap = getFunctionToEnumMap();
        
        return analyzeClass(clazz, maskedFields, functionToEnumMap, config);
    }
    
    /**
     * Analyzes a class and returns a map of field names to their corresponding
     * MaskFunctions enum type names.
     * 
     * @param clazz The class to analyze
     * @param maskedFields The map of field patterns to mask functions
     * @param functionToEnumMap Map of mask function -> enum type name (e.g., function -> "PASSWORD_TYPE")
     * @return Map of field name -> MaskFunctions enum type name (e.g., "PASSWORD_TYPE")
     */
    public static Map<String, String> analyzeClass(Class<?> clazz, 
                                                    Map<String, Function<String, String>> maskedFields,
                                                    Map<Function<String, String>, String> functionToEnumMap,
                                                    MaskingConfigProperties config) {
        Map<String, String> fieldMappings = new HashMap<>();
        
        logger.info("Analyzing class: {}", clazz.getName());
        
        // Get all declared fields
        Field[] fields = clazz.getDeclaredFields();
        
        for (Field field : fields) {
            String fieldName = field.getName();
            String maskFunctionType = findMatchingMaskFunctionType(fieldName, maskedFields, functionToEnumMap);
            
            if (maskFunctionType != null) {
                fieldMappings.put(fieldName, maskFunctionType);
                logger.info("  Field '{}' -> MaskFunction: {}", fieldName, maskFunctionType);
            } else {
                logger.debug("  Field '{}' -> No mask function found", fieldName);
            }
        }
        
        return fieldMappings;
    }
    
    /**
     * Finds the matching MaskFunctions enum type name for a field name.
     * Uses the same matching logic as UsersAllInterceptor (exact, prefix, Soundex).
     * 
     * @param fieldName The field name to match
     * @param maskedFields The map of patterns to mask functions
     * @param functionToEnumMap Map of mask function -> enum type name
     * @return The MaskFunctions enum type name, or null if no match
     */
    private static String findMatchingMaskFunctionType(String fieldName, 
                                                       Map<String, Function<String, String>> maskedFields,
                                                       Map<Function<String, String>, String> functionToEnumMap) {
        // Use the same matching logic as UsersAllInterceptor
        Function<String, String> maskFunction = findMatchingMaskFunction(fieldName, maskedFields);
        
        if (maskFunction != null && functionToEnumMap != null) {
            return functionToEnumMap.get(maskFunction);
        }
        
        return null;
    }
    
    /**
     * Finds a matching mask function using the same logic as UsersAllInterceptor.
     */
    private static Function<String, String> findMatchingMaskFunction(String fieldName, Map<String, Function<String, String>> maskedFields) {
        // 1. Exact match
        if (maskedFields.containsKey(fieldName)) {
            return maskedFields.get(fieldName);
        }
        
        // 2. Prefix match: fieldName starts with key
        String longestMatch = null;
        int longestLength = 0;
        
        for (String key : maskedFields.keySet()) {
            if (fieldName.startsWith(key) && key.length() > longestLength) {
                longestMatch = key;
                longestLength = key.length();
            }
        }
        
        if (longestMatch != null) {
            return maskedFields.get(longestMatch);
        }
        
        // 3. Reverse prefix match: key starts with fieldName
        for (String key : maskedFields.keySet()) {
            if (key.startsWith(fieldName)) {
                return maskedFields.get(key);
            }
        }
        
        // 4. Soundex match
        String fieldNameSoundex = soundex(fieldName);
        for (String key : maskedFields.keySet()) {
            String keySoundex = soundex(key);
            if (fieldNameSoundex.equals(keySoundex)) {
                logger.debug("Soundex match: '{}' matches '{}'", fieldName, key);
                return maskedFields.get(key);
            }
        }
        
        return null;
    }
    
    /**
     * Soundex encoding algorithm (same as in UsersAllInterceptor).
     */
    private static String soundex(String word) {
        if (word == null || word.isEmpty()) {
            return "0000";
        }
        
        word = word.toUpperCase().replaceAll("[^A-Z]", "");
        if (word.isEmpty()) {
            return "0000";
        }
        
        char[] chars = word.toCharArray();
        StringBuilder soundex = new StringBuilder();
        soundex.append(chars[0]);
        
        char previousDigit = getSoundexDigit(chars[0]);
        
        for (int i = 1; i < chars.length && soundex.length() < 4; i++) {
            char c = chars[i];
            char digit = getSoundexDigit(c);
            
            if (digit != '0' && digit != previousDigit) {
                soundex.append(digit);
                previousDigit = digit;
            }
        }
        
        while (soundex.length() < 4) {
            soundex.append('0');
        }
        
        return soundex.toString();
    }
    
    private static char getSoundexDigit(char c) {
        switch (c) {
            case 'B': case 'F': case 'P': case 'V':
                return '1';
            case 'C': case 'G': case 'J': case 'K': case 'Q': case 'S': case 'X': case 'Z':
                return '2';
            case 'D': case 'T':
                return '3';
            case 'L':
                return '4';
            case 'M': case 'N':
                return '5';
            case 'R':
                return '6';
            default:
                return '0';
        }
    }
    
    /**
     * Generates Java source code for a new class with the same properties as the input class,
     * but with @MaskedField annotations based on the field mappings.
     * 
     * @param originalClass The original class
     * @param newClassName The name for the new class
     * @param fieldMappings Map of field name -> MaskFunctions enum type name
     * @param accumulatorList List to accumulate annotated fields across all invocations (can be null)
     * @return Java source code as a string
     */
    public static String generateAnnotatedClassSource(Class<?> originalClass, String newClassName, 
                                                      Map<String, String> fieldMappings,
                                                      List<Map<String, String>> accumulatorList) {
        StringBuilder source = new StringBuilder();
        
        // Package declaration
        String packageName = originalClass.getPackage() != null ? originalClass.getPackage().getName() : "";
        if (!packageName.isEmpty()) {
            source.append("package ").append(packageName).append(";\n\n");
        }
        
        // Imports
        source.append("import ").append(MaskedField.class.getName()).append(";\n");
        source.append("import ").append(originalClass.getName()).append(";\n\n");
        
        // Class declaration
        source.append("public class ").append(newClassName).append(" {\n\n");
        
        // copy all class annotations in the original class to the new class
        source.append("    @").append(originalClass.getSimpleName()).append("(\n");
        for (Annotation annotation : originalClass.getAnnotations()) {
            source.append("        ").append(annotation.toString()).append("\n");
        }
        source.append("    )\n\n");


        // Generate fields with annotations
        Field[] fields = originalClass.getDeclaredFields();
        List<Map<String, String>> annotatedFields = new ArrayList<>();
        Set<String> seenFieldNames = new HashSet<>();
        
        for (Field field : fields) {
            String fieldName = field.getName();
            String fieldType = field.getType().getSimpleName();
            String maskFunctionType = fieldMappings.get(fieldName);
            
            // Add annotation if mapping exists
            if (maskFunctionType != null) {
                // Create entry in list of annotated fields if field name not already seen
                if (!seenFieldNames.contains(fieldName)) {
                    Map<String, String> annotatedField = new HashMap<>();
                    annotatedField.put("fieldName", fieldName + ":" + maskFunctionType);
                    //annotatedField.put("maskFunctionType", maskFunctionType);
                    annotatedFields.add(annotatedField);
                    seenFieldNames.add(fieldName);
                }
                source.append("    @MaskedField(maskFunctionType = \"").append(maskFunctionType).append("\")\n");
            }
            
            // Field declaration
            source.append("    private ").append(fieldType).append(" ").append(fieldName).append(";\n\n");
        }
        
        // Add annotated fields to accumulator list if provided
        if (accumulatorList != null) {
            Set<String> accumulatorFieldNames = new HashSet<>();
            for (Map<String, String> existingField : accumulatorList) {
                accumulatorFieldNames.add(existingField.get("fieldName"));
            }
            for (Map<String, String> annotatedField : annotatedFields) {
                String fieldName = annotatedField.get("fieldName");
                if (!accumulatorFieldNames.contains(fieldName)) {
                    accumulatorList.add(annotatedField);
                    accumulatorFieldNames.add(fieldName);
                }
            }
        }
        
        // Generate getters and setters
        for (Field field : fields) {
            String fieldName = field.getName();
            String fieldType = field.getType().getSimpleName();
            String capitalizedName = capitalize(fieldName);
            
            // Getter
            if (field.getType() == boolean.class) {
                source.append("    public boolean is").append(capitalizedName).append("() {\n");
            } else {
                source.append("    public ").append(fieldType).append(" get").append(capitalizedName).append("() {\n");
            }
            source.append("        return ").append(fieldName).append(";\n");
            source.append("    }\n\n");
            
            // Setter
            source.append("    public void set").append(capitalizedName).append("(").append(fieldType).append(" ").append(fieldName).append(") {\n");
            source.append("        this.").append(fieldName).append(" = ").append(fieldName).append(";\n");
            source.append("    }\n\n");
        }
        
        source.append("}\n");
        
        return source.toString();
    }
    
    /**
     * Overloaded method for backward compatibility (without accumulator list).
     * 
     * @param originalClass The original class
     * @param newClassName The name for the new class
     * @param fieldMappings Map of field name -> MaskFunctions enum type name
     * @return Java source code as a string
     */
    public static String generateAnnotatedClassSource(Class<?> originalClass, String newClassName, Map<String, String> fieldMappings) {
        return generateAnnotatedClassSource(originalClass, newClassName, fieldMappings, null);
    }
    
    /**
     * Convenience method that analyzes a class and generates the annotated class source code.
     * 
     * @param originalClass The original class to analyze
     * @param newClassName The name for the new annotated class
     * @param config Masking configuration properties
     * @param accumulatorList List to accumulate annotated fields across all invocations (can be null)
     * @return Java source code as a string
     */
    public String generateAnnotatedClass(Class<?> originalClass, String newClassName, 
                                            MaskingConfigProperties config,
                                            List<Map<String, String>> accumulatorList) {
        Assert.notNull(config, "MaskingConfigProperties cannot be null");
        Map<String, String> fieldMappings = analyzeClass(originalClass, config);
        return generateAnnotatedClassSource(originalClass, newClassName, fieldMappings, accumulatorList);
    }
    
    /**
     * Convenience method that analyzes a class and generates the annotated class source code.
     * 
     * @param originalClass The original class to analyze
     * @param newClassName The name for the new annotated class
     * @param config Masking configuration properties
     * @return Java source code as a string
     */
    public String generateAnnotatedClass(Class<?> originalClass, String newClassName, 
                                            MaskingConfigProperties config) {
        return generateAnnotatedClass(originalClass, newClassName, config, null);
    }
    
    /**
     * Convenience static method that analyzes a class and generates the annotated class source code.
     * 
     * @param originalClass The original class to analyze
     * @param newClassName The name for the new annotated class
     * @param maskedFields The map of field patterns to mask functions
     * @param functionToEnumMap Map of mask function -> enum type name
     * @param accumulatorList List to accumulate annotated fields across all invocations (can be null)
     * @return Java source code as a string
     */
    public static String generateAnnotatedClass(Class<?> originalClass, 
                                                 String newClassName,
                                                 Map<String, Function<String, String>> maskedFields,
                                                 Map<Function<String, String>, String> functionToEnumMap,
                                                 List<Map<String, String>> accumulatorList) {
        Map<String, String> fieldMappings = analyzeClass(originalClass, maskedFields, functionToEnumMap, null);
        return generateAnnotatedClassSource(originalClass, newClassName, fieldMappings, accumulatorList);
    }
    
    /**
     * Convenience static method that analyzes a class and generates the annotated class source code (without accumulator).
     * 
     * @param originalClass The original class to analyze
     * @param newClassName The name for the new annotated class
     * @param maskedFields The map of field patterns to mask functions
     * @param functionToEnumMap Map of mask function -> enum type name
     * @return Java source code as a string
     */
    public static String generateAnnotatedClass(Class<?> originalClass, 
                                                 String newClassName,
                                                 Map<String, Function<String, String>> maskedFields,
                                                 Map<Function<String, String>, String> functionToEnumMap) {
        return generateAnnotatedClass(originalClass, newClassName, maskedFields, functionToEnumMap, null);
    }
    
    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }


    private enum MaskFunctions {
        PASSWORD_TYPE("password", fieldValue -> fieldValue.replaceAll(".", "-")),
        EMAIL_TYPE("email", fieldValue -> "-----em***-----"),
        ROLE_TYPE("role", fieldValue -> "-----rol***-----");
        
        private final String fieldName;
        private final Function<String, String> maskFunction;
        
        MaskFunctions(String fieldName, Function<String, String> maskFunction) {
            this.fieldName = fieldName;
            this.maskFunction = maskFunction;
        }
        
        public String getFieldName() {
            return fieldName;
        }
        
        public Function<String, String> getMaskFunction() {
            return maskFunction;
        }
    }

     /**
     * Returns a map of mask function to MaskFunctions enum type name.
     * Useful for utilities that need to map functions to enum values.
     * 
     * @return Map of Function -> enum type name (e.g., function -> "PASSWORD_TYPE")
     */
    private Map<Function<String, String>, String> getFunctionToEnumMap() {
        Map<Function<String, String>, String> map = new HashMap<>();
        for (MaskFunctions mf : MaskFunctions.values()) {
            map.put(mf.getMaskFunction(), mf.name());
        }
        return map;
    }

      /**
     * Returns the maskedFields map for use by utilities.
     * 
     * @return The maskedFields map
     */
    private Map<String, Function<String, String>> getMaskedFields() {
        return maskedFields;
    }

    private void initDefaultMaskingFields(MaskingConfigProperties config) {
        Assert.notNull(config, "MaskingConfigProperties cannot be null");
        // maskedFields.put("pasword", MaskFunctions.PASSWORD_TYPE.getMaskFunction());
        // maskedFields.put("email", MaskFunctions.EMAIL_TYPE.getMaskFunction());
        // maskedFields.put("role", MaskFunctions.ROLE_TYPE.getMaskFunction());
        // //.put("street", MaskFunctions.ADDRESS_TYPE.getMaskFunction());
        // maskedFields.put("city", MaskFunctions.EMAIL_TYPE.getMaskFunction());
    
        for (Map.Entry<String, String> entry : config.getFields().entrySet()) {
            maskedFields.put(entry.getKey(), MaskFunctions.valueOf(entry.getValue()).getMaskFunction());
        }

        System.out.println("maskedFields: " + maskedFields);
    }
}

