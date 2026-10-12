package com.ericjc.musicplayer.controller;

import com.ericjc.musicplayer.entity.Song;
import com.ericjc.musicplayer.entity.User;
import com.ericjc.musicplayer.repository.SongRepository;
import com.ericjc.musicplayer.repository.UserRepository;
import com.ericjc.musicplayer.service.FileUploadService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final SongRepository songRepository;
    private final UserRepository userRepository;
    private final FileUploadService fileUploadService;

    public AdminController(SongRepository songRepository, UserRepository userRepository, FileUploadService fileUploadService) {
        this.songRepository = songRepository;
        this.userRepository = userRepository;
        this.fileUploadService = fileUploadService;
    }

    @GetMapping("/users")
    public ResponseEntity<?> listUsers(@AuthenticationPrincipal User admin) {
        if (!admin.isAdmin()) return forbidden();

        List<Map<String, Object>> users = userRepository.findAll().stream()
                .map(u -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", u.getId());
                    m.put("username", u.getUsername());
                    m.put("role", u.getRole());
                    m.put("createdAt", u.getCreatedAt());
                    return m;
                })
                .toList();
        return ResponseEntity.ok(users);
    }

    @PutMapping("/users/{id}/role")
    public ResponseEntity<?> updateUserRole(@AuthenticationPrincipal User admin,
                                            @PathVariable Long id,
                                            @RequestBody Map<String, String> body) {
        if (!admin.isAdmin()) return forbidden();

        String role = body.get("role");
        if (!"ADMIN".equals(role) && !"USER".equals(role)) {
            return ResponseEntity.badRequest().body(Map.of("error", "无效的角色"));
        }

        User user = userRepository.findById(id).orElse(null);
        if (user == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "用户不存在"));
        }

        user.setRole(role);
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/songs")
    public ResponseEntity<?> createSong(@AuthenticationPrincipal User admin, @RequestBody @Valid SongBody body) {
        if (!admin.isAdmin()) return forbidden();

        Song song = new Song(body.title, body.artist, body.album,
                body.duration != null ? body.duration : 0,
                body.coverUrl, body.audioUrl, body.lyricUrl);
        song = songRepository.save(song);
        return ResponseEntity.ok(Map.of("id", song.getId()));
    }

    @PutMapping("/songs/{id}")
    public ResponseEntity<?> updateSong(@AuthenticationPrincipal User admin,
                                        @PathVariable Long id,
                                        @RequestBody @Valid SongBody body) {
        if (!admin.isAdmin()) return forbidden();

        Song song = songRepository.findById(id).orElse(null);
        if (song == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "歌曲不存在"));
        }

        song.setTitle(body.title);
        song.setArtist(body.artist);
        song.setAlbum(body.album);
        if (body.duration != null) song.setDuration(body.duration);
        if (body.coverUrl != null) song.setCoverUrl(body.coverUrl);
        if (body.audioUrl != null) song.setAudioUrl(body.audioUrl);
        if (body.lyricUrl != null) song.setLyricUrl(body.lyricUrl);

        songRepository.save(song);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @DeleteMapping("/songs/{id}")
    public ResponseEntity<?> deleteSong(@AuthenticationPrincipal User admin, @PathVariable Long id) {
        if (!admin.isAdmin()) return forbidden();

        Song song = songRepository.findById(id).orElse(null);
        if (song == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "歌曲不存在"));
        }

        fileUploadService.deleteFile(song.getCoverUrl());
        fileUploadService.deleteFile(song.getAudioUrl());
        fileUploadService.deleteFile(song.getLyricUrl());

        songRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(@AuthenticationPrincipal User admin,
                                        @RequestParam("file") MultipartFile file,
                                        @RequestParam("type") String type) {
        if (!admin.isAdmin()) return forbidden();

        String subDir = switch (type) {
            case "audio" -> "audio";
            case "cover" -> "covers";
            case "lyric" -> "lyrics";
            default -> throw new RuntimeException("无效的文件类型");
        };

        try {
            String url = fileUploadService.uploadFile(file, subDir);
            return ResponseEntity.ok(Map.of("url", url));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "文件上传失败: " + e.getMessage()));
        }
    }

    @PostMapping("/upload/batch")
    public ResponseEntity<?> uploadFiles(@AuthenticationPrincipal User admin,
                                         @RequestParam(value = "files", required = false) MultipartFile[] files,
                                         @RequestParam(value = "type", required = false) String type) {
        if (!admin.isAdmin()) return forbidden();
        if (files == null || files.length == 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "未选择文件"));
        }
        if (files.length > 20) {
            return ResponseEntity.badRequest().body(Map.of("error", "单次最多上传 20 个文件"));
        }

        String subDir = switch (type == null ? "" : type) {
            case "audio" -> "audio";
            case "cover" -> "covers";
            case "lyric" -> "lyrics";
            default -> null;
        };
        if (subDir == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "无效的文件类型"));
        }

        List<Map<String, Object>> results = new ArrayList<>();
        for (MultipartFile file : files) {
            Map<String, Object> result = new LinkedHashMap<>();
            try {
                result.put("url", fileUploadService.uploadFile(file, subDir));
                result.put("error", null);
            } catch (Exception e) {
                result.put("url", null);
                result.put("error", "文件上传失败: " + e.getMessage());
            }
            results.add(result);
        }
        return ResponseEntity.ok(Map.of("results", results));
    }

    @PostMapping("/songs/batch")
    public ResponseEntity<?> createSongs(@AuthenticationPrincipal User admin, @RequestBody BatchSongBody body) {
        if (!admin.isAdmin()) return forbidden();
        if (body == null || body.songs == null || body.songs.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "歌曲列表不能为空"));
        }
        if (body.songs.size() > 100) {
            return ResponseEntity.badRequest().body(Map.of("error", "单次最多导入 100 首歌曲"));
        }

        List<Song> validSongs = new ArrayList<>();
        List<Integer> validIndexes = new ArrayList<>();
        Map<Integer, String> errors = new HashMap<>();
        for (int i = 0; i < body.songs.size(); i++) {
            SongBody songBody = body.songs.get(i);
            String error = validateBatchSong(songBody);
            if (error != null) {
                errors.put(i, error);
                continue;
            }
            validIndexes.add(i);
            validSongs.add(new Song(
                    songBody.title.trim(), songBody.artist.trim(), songBody.album.trim(),
                    songBody.duration != null ? songBody.duration : 0,
                    songBody.coverUrl, songBody.audioUrl.trim(), songBody.lyricUrl));
        }

        List<Song> savedSongs = validSongs.isEmpty() ? List.of() : songRepository.saveAll(validSongs);
        Map<Integer, Song> savedByIndex = new HashMap<>();
        for (int i = 0; i < savedSongs.size(); i++) {
            savedByIndex.put(validIndexes.get(i), savedSongs.get(i));
        }

        List<Map<String, Object>> results = new ArrayList<>();
        for (int i = 0; i < body.songs.size(); i++) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("index", i);
            Song saved = savedByIndex.get(i);
            if (saved != null) {
                result.put("success", true);
                result.put("id", saved.getId());
            } else {
                result.put("success", false);
                result.put("error", errors.getOrDefault(i, "歌曲创建失败"));
            }
            results.add(result);
        }
        return ResponseEntity.ok(Map.of("created", savedSongs.size(), "results", results));
    }

    private String validateBatchSong(SongBody body) {
        if (body == null || body.title == null || body.title.trim().isEmpty()) return "标题不能为空";
        if (body.artist == null || body.artist.trim().isEmpty()) return "歌手不能为空";
        if (body.album == null || body.album.trim().isEmpty()) return "专辑不能为空";
        if (body.audioUrl == null || body.audioUrl.trim().isEmpty()) return "音频地址不能为空";
        return null;
    }

    private ResponseEntity<?> forbidden() {
        return ResponseEntity.status(403).body(Map.of("error", "无权限"));
    }

    static class SongBody {
        @NotBlank public String title;
        @NotBlank public String artist;
        @NotBlank public String album;
        public Integer duration;
        public String coverUrl;
        @NotBlank public String audioUrl;
        public String lyricUrl;
    }

    static class BatchSongBody {
        public List<SongBody> songs;
    }
}
