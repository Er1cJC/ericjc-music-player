package com.ericjc.musicplayer.service;

import com.ericjc.musicplayer.dto.AuthResponse;
import com.ericjc.musicplayer.dto.LoginRequest;
import com.ericjc.musicplayer.dto.RegisterRequest;
import com.ericjc.musicplayer.entity.User;
import com.ericjc.musicplayer.repository.UserRepository;
import com.ericjc.musicplayer.security.JwtUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("用户名已存在");
        }

        User user = new User(request.getUsername(), passwordEncoder.encode(request.getPassword()));
        long userCount = userRepository.count();
        if (userCount == 0) {
            user.setRole("ADMIN");
        }
        user = userRepository.save(user);

        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
        return AuthResponse.fromUser(token, user);
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("用户名或密码错误"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new RuntimeException("用户名或密码错误");
        }

        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
        return AuthResponse.fromUser(token, user);
    }
}
