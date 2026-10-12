package com.ericjc.musicplayer.service;

import com.ericjc.musicplayer.dto.PlaylistDto;
import com.ericjc.musicplayer.dto.SongDto;
import com.ericjc.musicplayer.entity.Playlist;
import com.ericjc.musicplayer.entity.PlaylistItem;
import com.ericjc.musicplayer.entity.Song;
import com.ericjc.musicplayer.entity.User;
import com.ericjc.musicplayer.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PlaylistService {
    private final PlaylistRepository playlistRepository;
    private final PlaylistItemRepository playlistItemRepository;
    private final SongRepository songRepository;
    private final FavoriteRepository favoriteRepository;

    public PlaylistService(PlaylistRepository playlistRepository, PlaylistItemRepository playlistItemRepository,
                           SongRepository songRepository, FavoriteRepository favoriteRepository) {
        this.playlistRepository = playlistRepository;
        this.playlistItemRepository = playlistItemRepository;
        this.songRepository = songRepository;
        this.favoriteRepository = favoriteRepository;
    }

    @Transactional(readOnly = true)
    public List<PlaylistDto> getPlaylists(User user) {
        return playlistRepository.findByUserOrderByCreatedAtDesc(user).stream()
                .map(p -> PlaylistDto.fromPlaylist(p, playlistItemRepository.countByPlaylistId(p.getId())))
                .toList();
    }

    @Transactional
    public PlaylistDto createPlaylist(User user, String name, String description) {
        Playlist playlist = playlistRepository.save(new Playlist(user, validateName(name), validateDescription(description)));
        return PlaylistDto.fromPlaylist(playlist, 0);
    }

    @Transactional
    public PlaylistDto renamePlaylist(User user, Long playlistId, String name, String description) {
        Playlist playlist = getOwnedForUpdate(user, playlistId);
        playlist.setName(validateName(name));
        playlist.setDescription(validateDescription(description));
        playlistRepository.save(playlist);
        return PlaylistDto.fromPlaylist(playlist, playlistItemRepository.countByPlaylistId(playlistId));
    }

    @Transactional
    public void deletePlaylist(User user, Long playlistId) {
        Playlist playlist = getOwnedForUpdate(user, playlistId);
        playlistItemRepository.deleteByPlaylist(playlist);
        playlistRepository.delete(playlist);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getPlaylistDetail(User user, Long playlistId) {
        Playlist playlist = playlistRepository.findByIdAndUser(playlistId, user)
                .orElseThrow(() -> new RuntimeException("歌单不存在"));
        List<SongDto> songs = playlistItemRepository.findByPlaylistOrderByPositionAscCreatedAtAsc(playlist).stream()
                .map(item -> SongDto.fromSong(item.getSong(), favoriteRepository.existsByUserAndSongId(user, item.getSong().getId())))
                .toList();
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("id", playlist.getId());
        detail.put("name", playlist.getName());
        detail.put("description", playlist.getDescription());
        detail.put("createdAt", playlist.getCreatedAt());
        detail.put("songs", songs);
        return detail;
    }

    @Transactional
    public void addSong(User user, Long playlistId, Long songId) {
        Playlist playlist = getOwnedForUpdate(user, playlistId);
        if (songId == null || songId <= 0) throw new RuntimeException("歌曲不存在");
        Song song = songRepository.findById(songId).orElseThrow(() -> new RuntimeException("歌曲不存在"));
        if (playlistItemRepository.existsByPlaylistIdAndSongId(playlistId, songId)) {
            throw new RuntimeException("歌曲已在歌单中");
        }
        int position = playlistItemRepository.findMaxPosition(playlistId) + 1;
        playlistItemRepository.save(new PlaylistItem(playlist, song, position));
    }

    @Transactional
    public void removeSong(User user, Long playlistId, Long songId) {
        getOwnedForUpdate(user, playlistId);
        playlistItemRepository.deleteByPlaylistIdAndSongId(playlistId, songId);
    }

    private Playlist getOwnedForUpdate(User user, Long playlistId) {
        return playlistRepository.findOwnedForUpdate(playlistId, user)
                .orElseThrow(() -> new RuntimeException("歌单不存在"));
    }

    private String validateName(String name) {
        if (name == null || name.trim().isEmpty()) throw new RuntimeException("歌单名称不能为空");
        String trimmed = name.trim();
        if (trimmed.length() > 100) throw new RuntimeException("歌单名称不能超过100字");
        return trimmed;
    }

    private String validateDescription(String description) {
        if (description == null) return null;
        String trimmed = description.trim();
        if (trimmed.length() > 500) throw new RuntimeException("歌单描述不能超过500字");
        return trimmed;
    }
}
