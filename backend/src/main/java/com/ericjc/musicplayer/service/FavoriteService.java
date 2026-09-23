package com.ericjc.musicplayer.service;

import com.ericjc.musicplayer.entity.Favorite;
import com.ericjc.musicplayer.entity.Song;
import com.ericjc.musicplayer.entity.User;
import com.ericjc.musicplayer.repository.FavoriteRepository;
import com.ericjc.musicplayer.repository.SongRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class FavoriteService {

    private final FavoriteRepository favoriteRepository;
    private final SongRepository songRepository;

    public FavoriteService(FavoriteRepository favoriteRepository, SongRepository songRepository) {
        this.favoriteRepository = favoriteRepository;
        this.songRepository = songRepository;
    }

    public List<Long> getFavoriteSongIds(User user) {
        return favoriteRepository.findByUserOrderByCreatedAtDesc(user)
                .stream()
                .map(fav -> fav.getSong().getId())
                .toList();
    }

    @Transactional
    public void addFavorite(User user, Long songId) {
        if (favoriteRepository.existsByUserAndSongId(user, songId)) {
            return;
        }
        Song song = songRepository.findById(songId)
                .orElseThrow(() -> new RuntimeException("歌曲不存在"));
        favoriteRepository.save(new Favorite(user, song));
    }

    @Transactional
    public void removeFavorite(User user, Long songId) {
        favoriteRepository.deleteByUserAndSongId(user, songId);
    }

    public boolean isFavorited(User user, Long songId) {
        return favoriteRepository.existsByUserAndSongId(user, songId);
    }
}
