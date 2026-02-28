package com.example.springmvccontroller.controller;

import com.example.springmvccontroller.entity.User;
import com.example.springmvccontroller.security.JwtUtil;
import com.example.springmvccontroller.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserService userService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(@RequestBody Map<String, String> loginRequest) {
        String username = loginRequest.get("username");
        String password = loginRequest.get("password");
        
        // Validate credentials using database
        if (userService.validateCredentials(username, password)) {
            Optional<User> user = userService.findByUsername(username);
            if (user.isPresent()) {
                String token = jwtUtil.generateToken(username, user.get().getPaswrd());
                Map<String, String> response = new HashMap<>();
                response.put("token", token);
                response.put("username", username);
                response.put("role", user.get().getRole());
                return ResponseEntity.ok(response);
            }
        }
        
        return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials"));
    }
    
    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> register(@RequestBody Map<String, String> registerRequest) {
        String username = registerRequest.get("username");
        String password = registerRequest.get("password");
        String email = registerRequest.get("email");
        
        // Check if user already exists
        if (userService.existsByUsername(username)) {
            return ResponseEntity.status(400).body(Map.of("error", "Username already exists"));
        }
        
        if (userService.existsByEmail(email)) {
            return ResponseEntity.status(400).body(Map.of("error", "Email already exists"));
        }
        
        // Create new user (encode password so login with BCrypt works)
        User newUser = new User(username, passwordEncoder.encode(password), email);
        userService.save(newUser);
        
        Map<String, String> response = new HashMap<>();
        response.put("message", "User created successfully");
        response.put("username", username);
        return ResponseEntity.ok(response);
    }
}
