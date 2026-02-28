package com.example.springmvccontroller.interceptor;

import com.example.springmvccontroller.config.MaskingConfigProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.util.ContentCachingResponseWrapper;

@Component
public class UsersAllInterceptor implements HandlerInterceptor {
    
    private static final Logger logger = LoggerFactory.getLogger(UsersAllInterceptor.class);
    private Map<String, Function<String, String>> maskedFields;
    
    @Autowired(required = false)
    private ObjectMapper objectMapper;
    
    @Autowired(required = false)
    private MaskingConfigProperties maskingConfigProperties;

    private Map<String, Function<String, String>> maskingFunctions = new HashMap<>();
    
    @PostConstruct
    public void initializeMaskedFields() {
        initPredefinedMaskingFunctions();

        maskedFields = new HashMap<>();
        
        // If configuration is not available, use default values
        if (maskingConfigProperties == null) {
            logger.warn("MaskingConfigProperties not available. Using default hardcoded values.");
            initDefaultMaskingFields();
            return;
        }
        
        // Get field mappings from configuration
        Map<String, String> fieldMappings = maskingConfigProperties.getFields();
       // Map<String, String> maskFunctionValues = maskingConfigProperties.getMaskFunctions();
        
        if (fieldMappings == null || fieldMappings.isEmpty()) {
            logger.warn("No masking fields configured in YAML. Using default values.");
            initDefaultMaskingFields();
            return;
        }
        
        // Build maskedFields map from configuration
        for (Map.Entry<String, String> entry : fieldMappings.entrySet()) {
            String fieldName = entry.getKey();
            String maskFunctionType = entry.getValue();
            
            // Get the mask value from configuration, or use enum default
            Function<String, String> maskFunction = createMaskFunction(maskFunctionType);
            
            if (maskFunction != null) {
                maskedFields.put(fieldName, maskFunction);
                logger.debug("Loaded masking configuration: field='{}', maskType='{}'", fieldName, maskFunctionType);
            } else {
                logger.warn("Unknown mask function type '{}' for field '{}'. Skipping.", maskFunctionType, fieldName);
            }
        }
        
        logger.info("Loaded {} masked fields from configuration", maskedFields.size());
    }

    
    /**
     * Creates a mask function based on the mask function type from configuration.
     * 
     * @param maskFunctionType The type of mask function (e.g., "PASSWORD_TYPE")
     * @param maskFunctionValues Map of mask function types to their replacement values
     * @return The mask function, or null if type is unknown
     */
    private Function<String, String> createMaskFunction(String maskFunctionType) {
        // Get the replacement value from configuration, or use enum default
      
        Function<String, String> maskFunction = maskingFunctions.get(maskFunctionType);
        if (maskFunction == null) {
            // Fallback to enum values
            
            logger.warn("Unknown mask function type: {}", maskFunctionType);
            return null;
        }

        return maskFunction;
    }
    
    // Fallback: Create ObjectMapper if not autowired
    private ObjectMapper getObjectMapper() {
        if (objectMapper == null) {
            objectMapper = new ObjectMapper();
        }
        return objectMapper;
    }
    
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        logger.info("=== UsersAllInterceptor: preHandle ===");
        logger.info("Request URI: {}", request.getRequestURI());
        logger.info("Request Method: {}", request.getMethod());
        logger.info("Remote Address: {}", request.getRemoteAddr());
        logger.info("User Agent: {}", request.getHeader("User-Agent"));
        logger.info("Request Time: {}", System.currentTimeMillis());
        
        // Add custom header
        response.setHeader("X-Users-All-Intercepted", "true");
        
        return true; // Return true to continue the request, false to stop it
    }
    
    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
        logger.info("=== UsersAllInterceptor: postHandle ===");
        logger.info("Response Status: {}", response.getStatus());
        logger.info("Response Content Type: {}", response.getContentType());
        
        // You can modify the response here if needed
        // Note: For REST endpoints, modelAndView is usually null
    }


    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        logger.info("=== UsersAllInterceptor: afterCompletion ===");
        

        // Get the cached response wrapper from request attribute (set by ResponseCachingFilter)
        ContentCachingResponseWrapper wrappedResponse = 
            (ContentCachingResponseWrapper) request.getAttribute("contentCachingResponseWrapper");
        
        String responseBody = null;
        
        if (wrappedResponse != null) {
            // Get the response body as String from the cached wrapper
            byte[] contentAsBytes = wrappedResponse.getContentAsByteArray();
            if (contentAsBytes.length > 0) {
                responseBody = new String(contentAsBytes, java.nio.charset.StandardCharsets.UTF_8);
            }
        } else {
            // Fallback: try to get from request attribute (set by filter after completion)
            responseBody = (String) request.getAttribute("responseBody");
        }
        
        if (responseBody != null && !responseBody.isEmpty()) {
            logger.info("=== Response JSON Body (as String) ===");
            logger.info(responseBody);
            
            try {
                // Parse the JSON string
                JsonNode jsonNode = getObjectMapper().readTree(responseBody);
                
                // Check if it's an array
                if (jsonNode.isArray()) {
                    processJsonArray(jsonNode);
                    
                } else if (jsonNode.isObject()) {
                    // If it's a single object (not an array)
                    logger.info("=== Processing Single JSON Object ===");
                    maskFields(jsonNode);
                } else {
                    logger.info("JSON is neither an array nor an object");
                }
                
                // Convert modified JSON back to string
                String modifiedResponseBody = jsonNode.toString();
                logger.info("Modified Response Body: {}", modifiedResponseBody);
                
                // Store original and modified versions in request attribute for filter to use
                request.setAttribute("cachedResponseBody", responseBody);
                request.setAttribute("maskedResponseBody", modifiedResponseBody);
                request.setAttribute("Masked parsedJsonNode", jsonNode);
            } catch (Exception e) {
                logger.error("Error parsing JSON response body: {}", e.getMessage(), e);
            }
            
            logger.info("Response body length: {} characters", responseBody.length());
            
        } else {
            logger.info("Response body is empty or not available");
        }
        
        if (ex != null) {
            logger.error("Exception occurred: {}", ex.getMessage(), ex);
        }
        logger.info("Request processing completed");
    }


    private void processJsonArray(JsonNode jsonNode) {
        logger.info("=== Iterating through JSON Array ===");
        logger.info("Total elements in array: {}", jsonNode.size());
        
        ArrayNode arrayNode = (ArrayNode) jsonNode;
        
        // Visit each element in the JSON array
        int index = 0;
        for (JsonNode userNode : arrayNode) {
            //logger.info("--- User Element #{} ---", index + 1 + " " + 
                   // userNode.get("address").asText());
            
            // Convert to ObjectNode to allow modification
            if (userNode.isObject()) {
                maskFields(userNode);
            }
             
            index++;
        }
        
        logger.info("=== Finished processing {} elements ===", index);
    }


    private void maskFields(JsonNode userNode) {
        ObjectNode userObjectNode = (ObjectNode) userNode;
        
        // Get all fields dynamically
        java.util.List<String> fieldNames = new java.util.ArrayList<>();
        userObjectNode.fieldNames().forEachRemaining(fieldNames::add);
        logger.info("  All field names: {}", fieldNames);

        // Mask fields that are in the maskedFields map (exact or prefix match)
        for (String fieldName : fieldNames) {
            logger.info("  Field name: {}", fieldName);
            JsonNode jsonNode = userNode.get(fieldName);
            if (jsonNode.isArray()) {
                processJsonArray(jsonNode);
            } else if (jsonNode.isObject()) {
                maskFields(userNode.get(fieldName));
            } else {
                Function<String, String> maskFunction = findMatchingMaskFunction(fieldName);
                if (maskFunction != null) {
                    userObjectNode.put(fieldName, 
                        maskedValue(userNode.get(fieldName).asText(), maskFunction));
                    logger.info("  Masked field: {} (matched)", fieldName);
                }
            }
    
        }
    }

    /**
     * Finds a matching mask function for the given field name.
     * Checks for:
     * 1. Exact match
     * 2. Prefix match: fieldName starts with a key in maskedFields
     * 3. Prefix match: a key in maskedFields starts with fieldName
     * 4. Soundex match: phonetic similarity using Soundex algorithm
     * 
     * @param fieldName The field name to find a mask function for
     * @return The matching mask function, or null if no match found
     */
    private Function<String, String> findMatchingMaskFunction(String fieldName) {
        // 1. Check for exact match first (highest priority)
        if (maskedFields.containsKey(fieldName)) {
            return maskedFields.get(fieldName);
        }
        
        // 2. Check if fieldName starts with any key in maskedFields (prefix match)
        // Use the longest matching key for consistency
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
        
        // 3. Check if any key starts with fieldName (reverse prefix match)
        for (String key : maskedFields.keySet()) {
            if (key.startsWith(fieldName)) {
                return maskedFields.get(key);
            }
        }
        
        // 4. Check Soundex phonetic matching (lowest priority)
        String fieldNameSoundex = soundex(fieldName);
        for (String key : maskedFields.keySet()) {
            String keySoundex = soundex(key);
            if (fieldNameSoundex.equals(keySoundex)) {
                logger.debug("Soundex match found: '{}' (soundex: {}) matches '{}' (soundex: {})", 
                    fieldName, fieldNameSoundex, key, keySoundex);
                return maskedFields.get(key);
            }
        }
        
        return null;
    }
    
    /**
     * Soundex encoding algorithm - encodes words based on phonetic similarity.
     * Words that sound similar will have the same Soundex code.
     * 
     * @param word The word to encode
     * @return Soundex code (4 characters: letter + 3 digits)
     */
    private String soundex(String word) {
        if (word == null || word.isEmpty()) {
            return "0000";
        }
        
        word = word.toUpperCase().replaceAll("[^A-Z]", "");
        if (word.isEmpty()) {
            return "0000";
        }
        
        char[] chars = word.toCharArray();
        StringBuilder soundex = new StringBuilder();
        
        // Keep first letter
        soundex.append(chars[0]);
        
        // Get the first letter's soundex digit for comparison
        char previousDigit = getSoundexDigit(chars[0]);
        
        // Soundex mapping: letters to digits
        // B, F, P, V → 1
        // C, G, J, K, Q, S, X, Z → 2
        // D, T → 3
        // L → 4
        // M, N → 5
        // R → 6
        // A, E, H, I, O, U, W, Y → ignored
        
        for (int i = 1; i < chars.length && soundex.length() < 4; i++) {
            char c = chars[i];
            char digit = getSoundexDigit(c);
            
            // Skip if same as previous digit or if it's a vowel/ignored character (0)
            if (digit != '0' && digit != previousDigit) {
                soundex.append(digit);
                previousDigit = digit;
            }
        }
        
        // Pad with zeros if needed
        while (soundex.length() < 4) {
            soundex.append('0');
        }
        
        return soundex.toString();
    }
    
    /**
     * Maps a character to its Soundex digit.
     * 
     * @param c The character to map
     * @return The Soundex digit, or '0' for ignored characters
     */
    private char getSoundexDigit(char c) {
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
                // A, E, H, I, O, U, W, Y are ignored
                return '0';
        }
    }
    
    private String maskedValue(String fieldValue, Function<String, String> maskFunction) {
        return maskFunction.apply(fieldValue);
    }

    /**
     * Returns a map of mask function to MaskFunctions enum type name.
     * Useful for utilities that need to map functions to enum values.
     * 
     * @return Map of Function -> enum type name (e.g., function -> "PASSWORD_TYPE")
     */
    public Map<Function<String, String>, String> getFunctionToEnumMap() {
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
    public Map<String, Function<String, String>> getMaskedFields() {
        return maskedFields;
    }
    
    private enum MaskFunctions {
        PASSWORD_TYPE("password", fieldValue -> fieldValue.replaceAll(".", "-")),
        EMAIL_TYPE("email", fieldValue -> "-----em***-----"),
        ROLE_TYPE("role", fieldValue -> "-----rol***-----"),
        STREET_TYPE("street", fieldValue -> "-----str***-----");

        
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

    private void initPredefinedMaskingFunctions() {
        maskingFunctions.put("PASSWORD_TYPE",  MaskFunctions.PASSWORD_TYPE.getMaskFunction());
        maskingFunctions.put("EMAIL_TYPE", MaskFunctions.EMAIL_TYPE.getMaskFunction());
        maskingFunctions.put("ROLE_TYPE", MaskFunctions.ROLE_TYPE.getMaskFunction());
        maskingFunctions.put("STREET_TYPE", MaskFunctions.STREET_TYPE.getMaskFunction());
    }

    private void initDefaultMaskingFields() {
        maskedFields.put("pasword", MaskFunctions.PASSWORD_TYPE.getMaskFunction());
        maskedFields.put("email", MaskFunctions.EMAIL_TYPE.getMaskFunction());
        maskedFields.put("role", MaskFunctions.ROLE_TYPE.getMaskFunction());
        maskedFields.put("street", MaskFunctions.STREET_TYPE.getMaskFunction());
    }
    
}