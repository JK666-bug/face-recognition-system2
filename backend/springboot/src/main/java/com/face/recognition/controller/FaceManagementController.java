package com.face.recognition.controller;

import com.face.recognition.dto.CaptureRecordDTO;
import com.face.recognition.model.User;
import com.face.recognition.repository.UserRepository;
import com.face.recognition.service.FaceManagementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/management")
@CrossOrigin(origins = "http://localhost:5173")
public class FaceManagementController {

    @Autowired
    private FaceManagementService managementService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @GetMapping("/captures")
    public List<CaptureRecordDTO> getAllCaptures() {
        return managementService.listAllCaptures();
    }

    @DeleteMapping("/captures/{id}")
    public void deleteCapture(@PathVariable String id) {
        managementService.deleteCapture(id);
    }

    // --- 用户管理系统 (超级管理员专用) ---

    @GetMapping("/admin/users")
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @PostMapping("/admin/register")
    public ResponseEntity<?> registerUser(@RequestBody User user) {
        if (userRepository.findByUsername(user.getUsername()).isPresent()) {
            return ResponseEntity.badRequest().body("Username already exists");
        }
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setRole("ADMIN"); // 默认注册为普通管理员
        return ResponseEntity.ok(userRepository.save(user));
    }

    @PostMapping("/admin/promote/{id}")
    public ResponseEntity<?> promoteUser(@PathVariable Long id) {
        return userRepository.findById(id).map(user -> {
            user.setRole("SUPER_ADMIN");
            return ResponseEntity.ok(userRepository.save(user));
        }).orElse(ResponseEntity.notFound().build());
    }
}
