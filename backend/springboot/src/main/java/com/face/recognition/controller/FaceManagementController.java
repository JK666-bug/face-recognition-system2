package com.face.recognition.controller;

import com.face.recognition.dto.CaptureRecordDTO;
import com.face.recognition.dto.PeopleRecordDTO;
import com.face.recognition.model.User;
import com.face.recognition.repository.UserRepository;
import com.face.recognition.service.FaceManagementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
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

    @DeleteMapping("/captures/all")
    public void deleteAllCaptures() {
        managementService.deleteAllCaptures();
    }

    @GetMapping("/people")
    public List<PeopleRecordDTO> listPeopleLibrary() {
        return managementService.listPeopleLibrary();
    }

    @PostMapping(value = "/people/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PeopleRecordDTO> uploadPeoplePhoto(
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            @RequestParam(value = "suspect", defaultValue = "false") boolean suspect) {
        try {
            PeopleRecordDTO created = managementService.createPersonFromUpload(file.getBytes(), suspect);
            return ResponseEntity.ok(created);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/people/{peopleId}/suspect")
    public ResponseEntity<?> setSuspect(
            @PathVariable String peopleId,
            @RequestParam("value") boolean value) {
        try {
            managementService.setPeopleSuspect(peopleId, value);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }

    @DeleteMapping("/people/{peopleId}")
    public ResponseEntity<?> deletePerson(@PathVariable String peopleId) {
        try {
            managementService.deletePerson(peopleId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }

    @PostMapping(value = "/people/{peopleId}/replace-photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> replacePersonPhoto(
            @PathVariable String peopleId,
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        try {
            return ResponseEntity.ok(managementService.replacePersonPhoto(peopleId, file.getBytes()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }

    @GetMapping("/videos/{videoId}")
    public ResponseEntity<Resource> downloadVideo(@PathVariable String videoId) {
        try {
            InputStream videoStream = managementService.getVideo(videoId);
            InputStreamResource resource = new InputStreamResource(videoStream);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + videoId + "\"")
                    .contentType(MediaType.parseMediaType("video/mp4"))
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
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

    @DeleteMapping("/admin/users/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        return userRepository.findById(id).map(user -> {
            // 禁止删除任何超级管理员 (只能删除普通 ADMIN)
            if ("SUPER_ADMIN".equals(user.getRole())) {
                return ResponseEntity.badRequest().body("Super Administrators cannot be deleted.");
            }
            userRepository.delete(user);
            return ResponseEntity.ok().build();
        }).orElse(ResponseEntity.notFound().build());
    }
}
