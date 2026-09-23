package com.ericjc.musicplayer.controller;

import com.ericjc.musicplayer.entity.User;
import com.ericjc.musicplayer.service.FavoriteService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/favorites")
public class FavoriteController {

    private final FavoriteService favoriteService;

    public FavoriteController(FavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    @GetMapping
    public ResponseEntity<?> getFavorites(@AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        }
        List<Long> songIds = favoriteService.getFavoriteSongIds(user);
        return ResponseEntity.ok(Map.of("songIds", songIds));
    }

    @PostMapping("/{songId}")
    public ResponseEntity<?> addFavorite(@AuthenticationPrincipal User user, @PathVariable Long songId) {
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        }
        try {
            favoriteService.addFavorite(user, songId);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{songId}")
    public ResponseEntity<?> removeFavorite(@AuthenticationPrincipal User user, @PathVariable Long songId) {
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        }
        favoriteService.removeFavorite(user, songId);
        return ResponseEntity.ok(Map.of("success", true));
    }
}
