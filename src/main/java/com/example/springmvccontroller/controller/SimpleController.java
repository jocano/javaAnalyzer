package com.example.springmvccontroller.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

import javax.crypto.Cipher;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@RestController
@RequestMapping("/api")
public class SimpleController {

    @PostMapping("/wrap-aes")
    public ResponseEntity<Map<String, String>> handlePost(@RequestBody Map<String, String> request) {
        Map<String, String> response = new HashMap<>();
        String clientSpkiB64 = request.get("clientSpki");
        response.put("received", clientSpkiB64);
        try {
            System.out.println("\n\nReceived message: >" + clientSpkiB64 + "<");
            String secretKey = wrapAesWithClientSpki(clientSpkiB64);
            System.out.println("\n\nSecret key:>" + secretKey + "<");
            response.put("wrappedKeyB64", secretKey);
            response.put("status", "success");
        } catch (Exception e) {
            response.put("status", "error");
            response.put("error", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
        return ResponseEntity.ok(response);
    }

    public static String wrapAesWithClientSpki(String clientSpkiB64) throws Exception {
        System.out.println("\n\nReceived clientSpkiB64: >" + clientSpkiB64 + "<");

        
        byte[] spki = Base64.getDecoder().decode(clientSpkiB64);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        PublicKey clientPub = kf.generatePublic(new X509EncodedKeySpec(spki));
        PrivateKey clientPriv = kf.generatePrivate(new X509EncodedKeySpec(spki));

        // generate AES-256 key bytes (server-generated secret)
            // byte[] aesKey = new byte[32];
            // SecureRandom rnd = SecureRandom.getInstanceStrong();
            // rnd.nextBytes(aesKey);

        // wrap with RSA-OAEP (SHA-256)
        byte[] aesKey = "Hello from the server!".getBytes();
        Cipher rsa = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        rsa.init(Cipher.ENCRYPT_MODE, clientPub);
        byte[] wrapped = rsa.doFinal(aesKey);
        System.out.println("\n Wrapped: >" + Base64.getEncoder().encodeToString(wrapped) + "<");
        
        // String decrypted = decryptWrappedValue(Base64.getEncoder().encodeToString(wrapped), clientPriv);
        // System.out.println("\n\nDecrypted message:>" + decrypted + "<");
        // return wrapped bytes as base64 (send over HTTPS JSON)
        return Base64.getEncoder().encodeToString(wrapped);
    }

    /**
     * Unwraps/decrypts an RSA-OAEP encrypted value using a private key.
     * This demonstrates how to decrypt the wrappedKeyB64 value.
     * 
     * @param wrappedKeyB64 Base64-encoded encrypted data
     * @param privateKeyB64 Base64-encoded private key in PKCS#8 format
     * @return Decrypted bytes (e.g., AES key bytes)
     * @throws Exception if decryption fails
     */
    public static byte[] unwrapAesWithPrivateKey(String wrappedKeyB64, String privateKeyB64) throws Exception {
        // Decode base64 wrapped key
        byte[] wrapped = Base64.getDecoder().decode(wrappedKeyB64);
        
        // Decode and load private key
        byte[] pkcs8Bytes = Base64.getDecoder().decode(privateKeyB64);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        java.security.spec.PKCS8EncodedKeySpec keySpec = new java.security.spec.PKCS8EncodedKeySpec(pkcs8Bytes);
        PrivateKey privateKey = kf.generatePrivate(keySpec);
        
        // Unwrap/decrypt using RSA-OAEP (must match encryption algorithm)
        Cipher rsa = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        rsa.init(Cipher.DECRYPT_MODE, privateKey);
        byte[] unwrapped = rsa.doFinal(wrapped);

       

    
        System.out.println("\n\nUnwrapped key length: " + unwrapped.length + " bytes");
        System.out.println("Unwrapped key (hex): " + bytesToHex(unwrapped));
        
        return unwrapped;
    }
    
   
    /**
     * Decrypts an RSA-OAEP encrypted value using Cipher with a PrivateKey object.
     * This is an overloaded version that accepts a PrivateKey directly.
     * 
     * @param encryptedMessageB64 Base64-encoded encrypted data
     * @param privateKey PrivateKey object to use for decryption
     * @return Decrypted string
     * @throws Exception if decryption fails
     */
    public static String decryptWrappedValue(String encryptedMessageB64, PrivateKey privateKey) throws Exception {
        // Step 1: Decode base64 encrypted message to bytes
        byte[] encrypted = Base64.getDecoder().decode(encryptedMessageB64);
        
        // Step 2: Decrypt using RSA-OAEP (must match encryption algorithm)
        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        cipher.init(Cipher.DECRYPT_MODE, privateKey);
        byte[] decrypted = cipher.doFinal(encrypted);
        
        // Step 3: Convert decrypted bytes to String
        String plaintext = new String(decrypted, "UTF-8");
        
        System.out.println("Decrypted message length: " + decrypted.length + " bytes");
        System.out.println("Decrypted message: " + plaintext);
        
        return plaintext;
    }
    
    /**
     * Helper method to convert bytes to hex string for display
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
}

