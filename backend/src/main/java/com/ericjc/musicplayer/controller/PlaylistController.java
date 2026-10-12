package com.ericjc.musicplayer.controller;

import com.ericjc.musicplayer.dto.CreatePlaylistRequest;
import com.ericjc.musicplayer.dto.UpdatePlaylistRequest;
import com.ericjc.musicplayer.entity.User;
import com.ericjc.musicplayer.service.PlaylistService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/playlists")
public class PlaylistController {
    private final PlaylistService playlistService;

    public PlaylistController(PlaylistService playlistService) { this.playlistService = playlistService; }

    @GetMapping
    public ResponseEntity<?> getPlaylists(@AuthenticationPrincipal User user) {
        if (user == null) return unauthorized();
        try {
            return ResponseEntity.ok(Map.of("playlists", playlistService.getPlaylists(user)));
        } catch (RuntimeException e) { return badRequest(e); }
    }

    @PostMapping
    public ResponseEntity<?> createPlaylist(@AuthenticationPrincipal User user, @RequestBody CreatePlaylistRequest body) {
        if (user == null) return unauthorized();
        try {
            return ResponseEntity.ok(playlistService.createPlaylist(user, body.getName(), body.getDescription()));
        } catch (RuntimeException e) { return badRequest(e); }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> renamePlaylist(@AuthenticationPrincipal User user, @PathVariable Long id,
                                            @RequestBody UpdatePlaylistRequest body) {
        if (user == null) return unauthorized();
        try {
            return ResponseEntity.ok(playlistService.renamePlaylist(user, id, body.getName(), body.getDescription()));
        } catch (RuntimeException e) { return badRequest(e); }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deletePlaylist(@AuthenticationPrincipal User user, @PathVariable Long id) {
        if (user == null) return unauthorized();
        try {
            playlistService.deletePlaylist(user, id);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (RuntimeException e) { return badRequest(e); }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getPlaylistDetail(@AuthenticationPrincipal User user, @PathVariable Long id) {
        if (user == null) return unauthorized();
        try {
            return ResponseEntity.ok(playlistService.getPlaylistDetail(user, id));
        } catch (RuntimeException e) { return badRequest(e); }
    }

    @PostMapping("/{id}/songs")
    public ResponseEntity<?> addSong(@AuthenticationPrincipal User user, @PathVariable Long id,
                                     @RequestBody Map<String, Long> body) {
        if (user == null) return unauthorized();
        try {
            playlistService.addSong(user, id, body.get("songId"));
            return ResponseEntity.ok(Map.of("success", true));
        } catch (RuntimeException e) { return badRequest(e); }
    }

    @DeleteMapping("/{id}/songs/{songId}")
    public ResponseEntity<?> removeSong(@AuthenticationPrincipal User user, @PathVariable Long id, @PathVariable Long songId) {
        if (user == null) return unauthorized();
        try {
            playlistService.removeSong(user, id, songId);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (RuntimeException e) { return badRequest(e); }
    }

    private ResponseEntity<?> unauthorized() {
        return ResponseEntity.status(401).body(Map.of("error", "未登录"));
    }

    private ResponseEntity<?> badRequest(RuntimeException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage() == null ? "操作失败" : e.getMessage()));
    }
}
