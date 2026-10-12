package com.ericjc.musicplayer.repository;

import com.ericjc.musicplayer.entity.Playlist;
import com.ericjc.musicplayer.entity.PlaylistItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PlaylistItemRepository extends JpaRepository<PlaylistItem, Long> {
    List<PlaylistItem> findByPlaylistOrderByPositionAscCreatedAtAsc(Playlist playlist);
    boolean existsByPlaylistIdAndSongId(Long playlistId, Long songId);
    long countByPlaylistId(Long playlistId);
    void deleteByPlaylistIdAndSongId(Long playlistId, Long songId);
    void deleteByPlaylist(Playlist playlist);

    @Query("select coalesce(max(i.position), 0) from PlaylistItem i where i.playlist.id = :playlistId")
    int findMaxPosition(@Param("playlistId") Long playlistId);
}
