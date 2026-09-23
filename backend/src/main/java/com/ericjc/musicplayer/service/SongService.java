package com.ericjc.musicplayer.service;

import com.ericjc.musicplayer.dto.SongDto;
import com.ericjc.musicplayer.entity.Song;
import com.ericjc.musicplayer.entity.User;
import com.ericjc.musicplayer.repository.FavoriteRepository;
import com.ericjc.musicplayer.repository.SongRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SongService {

    private final SongRepository songRepository;
    private final FavoriteRepository favoriteRepository;

    public SongService(SongRepository songRepository, FavoriteRepository favoriteRepository) {
        this.songRepository = songRepository;
        this.favoriteRepository = favoriteRepository;
    }

    public List<SongDto> getAllSongs(User user) {
        List<Song> songs = songRepository.findAll();
        return songs.stream()
                .map(song -> {
                    boolean liked = user != null && favoriteRepository.existsByUserAndSongId(user, song.getId());
                    return SongDto.fromSong(song, liked);
                })
                .toList();
    }

    public List<Song> getAllSongsRaw() {
        return songRepository.findAll();
    }
}
