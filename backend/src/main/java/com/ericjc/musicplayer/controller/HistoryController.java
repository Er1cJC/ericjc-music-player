package com.ericjc.musicplayer.controller;

import com.ericjc.musicplayer.entity.User;
import com.ericjc.musicplayer.service.HistoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/history")
public class HistoryController {

    public static final int MAX_HISTORY = 12;

    private final HistoryService historyService;

    public HistoryController(HistoryService historyService) {
        this.historyService = historyService;
    }

    @GetMapping
    public ResponseEntity<?> getHistory(@AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        }
        List<Long> songIds = historyService.getRecentSongIds(user, MAX_HISTORY);
        return ResponseEntity.ok(Map.of("songIds", songIds));
    }

    @PostMapping
    public ResponseEntity<?> recordPlay(@AuthenticationPrincipal User user, @RequestBody Map<String, Long> body) {
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        }
        Long songId = body.get("songId");
        if (songId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "songId 不能为空"));
        }
        try {
            historyService.recordPlay(user, songId);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
