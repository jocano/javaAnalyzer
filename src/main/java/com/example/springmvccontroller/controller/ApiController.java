package com.example.springmvccontroller.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ApiController {

    @GetMapping("/public/hello")
    public ResponseEntity<Map<String, String>> publicHello() {
           Map<String, String> response = new HashMap<>();
        response.put("message", "Hello from public endpoint!");
        response.put("timestamp", LocalDateTime.now().toString());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/protected/user-info")
    public ResponseEntity<Map<String, Object>> getUserInfo(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Hello from protected endpoint!");
        response.put("username", authentication.getName());
        response.put("timestamp", LocalDateTime.now().toString());
        response.put("authorities", authentication.getAuthorities());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/protected/data")
    public ResponseEntity<Map<String, Object>> getData(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();
        response.put("data", "This is sensitive data that requires authentication");
        response.put("user", authentication.getName());
        response.put("timestamp", LocalDateTime.now().toString());
        response.put("status", "success");
        return ResponseEntity.ok(response);
    }
}
