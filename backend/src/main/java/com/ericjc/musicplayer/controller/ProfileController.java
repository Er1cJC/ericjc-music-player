package com.ericjc.musicplayer.controller;

import com.ericjc.musicplayer.entity.User;
import com.ericjc.musicplayer.repository.FavoriteRepository;
import com.ericjc.musicplayer.repository.PlayHistoryRepository;
import com.ericjc.musicplayer.repository.PlaylistRepository;
import com.ericjc.musicplayer.repository.UserRepository;
import com.ericjc.musicplayer.service.FileUploadService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 个人资料：查看资料与统计、修改昵称/签名、上传头像。
 */
@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private static final Set<String> ALLOWED_IMAGE_EXTENSIONS =
            Set.of(".jpg", ".jpeg", ".png", ".webp", ".gif");

    private final UserRepository userRepository;
    private final FavoriteRepository favoriteRepository;
    private final PlayHistoryRepository playHistoryRepository;
    private final PlaylistRepository playlistRepository;
    private final FileUploadService fileUploadService;

    public ProfileController(UserRepository userRepository, FavoriteRepository favoriteRepository,
                             PlayHistoryRepository playHistoryRepository, PlaylistRepository playlistRepository,
                             FileUploadService fileUploadService) {
        this.userRepository = userRepository;
        this.favoriteRepository = favoriteRepository;
        this.playHistoryRepository = playHistoryRepository;
        this.playlistRepository = playlistRepository;
        this.fileUploadService = fileUploadService;
    }

    @GetMapping
    public ResponseEntity<?> getProfile(@AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        }

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("favorites", favoriteRepository.countByUser(user));
        stats.put("playlists", playlistRepository.countByUser(user));
        stats.put("history", playHistoryRepository.countByUser(user));

        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("userId", user.getId());
        profile.put("username", user.getUsername());
        profile.put("role", user.getRole());
        profile.put("nickname", user.getNickname());
        profile.put("avatarUrl", user.getAvatarUrl());
        profile.put("bio", user.getBio());
        profile.put("createdAt", user.getCreatedAt());
        profile.put("stats", stats);
        return ResponseEntity.ok(profile);
    }

    @PutMapping
    @Transactional
    public ResponseEntity<?> updateProfile(@AuthenticationPrincipal User user,
                                           @RequestBody Map<String, String> body) {
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        }

        String nickname = normalize(body.get("nickname"), 50);
        String bio = normalize(body.get("bio"), 200);

        User managed = userRepository.findById(user.getId()).orElse(null);
        if (managed == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "用户不存在"));
        }

        managed.setNickname(nickname);
        managed.setBio(bio);
        userRepository.save(managed);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("nickname", managed.getNickname());
        result.put("bio", managed.getBio());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/avatar")
    @Transactional
    public ResponseEntity<?> uploadAvatar(@AuthenticationPrincipal User user,
                                          @RequestParam("file") MultipartFile file) {
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        }
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "请选择图片文件"));
        }

        String originalName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        String extension = originalName.contains(".")
                ? originalName.substring(originalName.lastIndexOf('.'))
                : "";
        if (!ALLOWED_IMAGE_EXTENSIONS.contains(extension)) {
            return ResponseEntity.badRequest().body(Map.of("error", "仅支持 jpg / jpeg / png / webp / gif 图片"));
        }

        User managed = userRepository.findById(user.getId()).orElse(null);
        if (managed == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "用户不存在"));
        }

        String oldAvatarUrl = managed.getAvatarUrl();
        try {
            String url = fileUploadService.uploadFile(file, "avatars");
            managed.setAvatarUrl(url);
            userRepository.save(managed);
            // 替换头像时清理旧文件，避免磁盘堆积
            if (oldAvatarUrl != null) {
                fileUploadService.deleteFile(oldAvatarUrl);
            }
            return ResponseEntity.ok(Map.of("avatarUrl", url));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "头像上传失败: " + e.getMessage()));
        }
    }

    /** 去掉首尾空白；空串归一为 null；超长截断 */
    private String normalize(String value, int maxLength) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return null;
        return trimmed.length() > maxLength ? trimmed.substring(0, maxLength) : trimmed;
    }
}
