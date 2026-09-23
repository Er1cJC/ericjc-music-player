package com.ericjc.musicplayer.controller;

import com.ericjc.musicplayer.dto.SongDto;
import com.ericjc.musicplayer.entity.User;
import com.ericjc.musicplayer.service.SongService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/songs")
public class SongController {

    private final SongService songService;

    public SongController(SongService songService) {
        this.songService = songService;
    }

    @GetMapping
    public List<SongDto> listSongs(@AuthenticationPrincipal User user) {
        return songService.getAllSongs(user);
    }
}
