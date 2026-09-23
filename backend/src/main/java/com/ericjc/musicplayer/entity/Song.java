package com.ericjc.musicplayer.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "songs")
public class Song {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 200)
    private String artist;

    @Column(nullable = false, length = 200)
    private String album;

    @Column(nullable = false)
    private Integer duration;

    @Column(name = "cover_url", length = 500)
    private String coverUrl;

    @Column(name = "audio_url", nullable = false, length = 500)
    private String audioUrl;

    @Column(name = "lyric_url", length = 500)
    private String lyricUrl;

    public Song() {}

    public Song(String title, String artist, String album, Integer duration,
                String coverUrl, String audioUrl, String lyricUrl) {
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.duration = duration;
        this.coverUrl = coverUrl;
        this.audioUrl = audioUrl;
        this.lyricUrl = lyricUrl;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getArtist() { return artist; }
    public void setArtist(String artist) { this.artist = artist; }
    public String getAlbum() { return album; }
    public void setAlbum(String album) { this.album = album; }
    public Integer getDuration() { return duration; }
    public void setDuration(Integer duration) { this.duration = duration; }
    public String getCoverUrl() { return coverUrl; }
    public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }
    public String getAudioUrl() { return audioUrl; }
    public void setAudioUrl(String audioUrl) { this.audioUrl = audioUrl; }
    public String getLyricUrl() { return lyricUrl; }
    public void setLyricUrl(String lyricUrl) { this.lyricUrl = lyricUrl; }
}
