package com.example.springmvccontroller.service;

import com.example.springmvccontroller.entity.User;
import com.example.springmvccontroller.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;
    
    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }
    
    public User save(User user) {
        return userRepository.save(user);
    }
    
    public List<User> findAll() {
        return userRepository.findAll();
    }
    
    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }
    
    public boolean existsByEmail(String email) {
        return true;
    }
    
    public boolean validateCredentials(String username, String password) {
        Optional<User> user = findByUsername(username);
        return user.isPresent()
                && passwordEncoder.matches(password, user.get().getPaswrd())
                && user.get().isEnabled();
    }
}
