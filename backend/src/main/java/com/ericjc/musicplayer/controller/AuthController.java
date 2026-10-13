package com.ericjc.musicplayer.controller;

import com.ericjc.musicplayer.dto.AuthResponse;
import com.ericjc.musicplayer.dto.LoginRequest;
import com.ericjc.musicplayer.dto.RegisterRequest;
import com.ericjc.musicplayer.entity.User;
import com.ericjc.musicplayer.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        try {
            AuthResponse response = authService.register(request);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        try {
            AuthResponse response = authService.login(request);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(@AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        }
        // 注意：nickname/avatarUrl/bio 可能为 null，Map.of 不允许 null 值，必须用 LinkedHashMap
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("userId", user.getId());
        profile.put("username", user.getUsername());
        profile.put("role", user.getRole());
        profile.put("nickname", user.getNickname());
        profile.put("avatarUrl", user.getAvatarUrl());
        profile.put("bio", user.getBio());
        return ResponseEntity.ok(profile);
    }
}
