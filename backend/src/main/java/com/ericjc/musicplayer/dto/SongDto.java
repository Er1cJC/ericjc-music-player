package com.ericjc.musicplayer.dto;

import com.ericjc.musicplayer.entity.Song;

public class SongDto {

    private Long id;
    private String title;
    private String artist;
    private String album;
    private Integer duration;
    private String coverUrl;
    private String audioUrl;
    private String lyricUrl;
    private boolean liked;

    public static SongDto fromSong(Song song, boolean liked) {
        SongDto dto = new SongDto();
        dto.id = song.getId();
        dto.title = song.getTitle();
        dto.artist = song.getArtist();
        dto.album = song.getAlbum();
        dto.duration = song.getDuration();
        dto.coverUrl = song.getCoverUrl();
        dto.audioUrl = song.getAudioUrl();
        dto.lyricUrl = song.getLyricUrl();
        dto.liked = liked;
        return dto;
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getArtist() { return artist; }
    public String getAlbum() { return album; }
    public Integer getDuration() { return duration; }
    public String getCoverUrl() { return coverUrl; }
    public String getAudioUrl() { return audioUrl; }
    public String getLyricUrl() { return lyricUrl; }
    public boolean isLiked() { return liked; }
}
