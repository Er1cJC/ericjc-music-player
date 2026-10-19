package com.ericjc.musicplayer.service;

import com.ericjc.musicplayer.dto.SongDto;
import com.ericjc.musicplayer.entity.Song;
import com.ericjc.musicplayer.entity.User;
import com.ericjc.musicplayer.repository.FavoriteRepository;
import com.ericjc.musicplayer.repository.SongRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

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
        // 修复 N+1 查询：先一次性取出该用户所有收藏的 songId 集合，
        // 再用 Set.contains() O(1) 判断，避免每首歌单独查一次数据库。
        Set<Long> likedIds = user != null
                ? favoriteRepository.findSongIdsByUser(user)
                : Set.of();
        return songs.stream()
                .map(song -> SongDto.fromSong(song, likedIds.contains(song.getId())))
                .toList();
    }

    public List<Song> getAllSongsRaw() {
        return songRepository.findAll();
    }
}
