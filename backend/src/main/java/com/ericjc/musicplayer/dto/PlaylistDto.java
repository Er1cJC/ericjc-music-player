package com.ericjc.musicplayer.dto;

import com.ericjc.musicplayer.entity.Playlist;
import java.time.LocalDateTime;

public class PlaylistDto {
    private Long id;
    private String name;
    private String description;
    private long songCount;
    private LocalDateTime createdAt;

    public static PlaylistDto fromPlaylist(Playlist playlist, long songCount) {
        PlaylistDto dto = new PlaylistDto();
        dto.id = playlist.getId();
        dto.name = playlist.getName();
        dto.description = playlist.getDescription();
        dto.songCount = songCount;
        dto.createdAt = playlist.getCreatedAt();
        return dto;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public long getSongCount() { return songCount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
