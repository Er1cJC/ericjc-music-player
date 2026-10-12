package com.ericjc.musicplayer.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import java.time.LocalDateTime;

@Entity
@Table(name = "playlist_items", uniqueConstraints = @UniqueConstraint(columnNames = {"playlist_id", "song_id"}))
public class PlaylistItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "playlist_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Playlist playlist;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "song_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Song song;

    @Column(nullable = false)
    private Integer position;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); }

    public PlaylistItem() {}

    public PlaylistItem(Playlist playlist, Song song, Integer position) {
        this.playlist = playlist;
        this.song = song;
        this.position = position;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Playlist getPlaylist() { return playlist; }
    public void setPlaylist(Playlist playlist) { this.playlist = playlist; }
    public Song getSong() { return song; }
    public void setSong(Song song) { this.song = song; }
    public Integer getPosition() { return position; }
    public void setPosition(Integer position) { this.position = position; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
