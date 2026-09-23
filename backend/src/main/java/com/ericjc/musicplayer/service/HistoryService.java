package com.ericjc.musicplayer.service;

import com.ericjc.musicplayer.entity.PlayHistory;
import com.ericjc.musicplayer.entity.Song;
import com.ericjc.musicplayer.entity.User;
import com.ericjc.musicplayer.repository.PlayHistoryRepository;
import com.ericjc.musicplayer.repository.SongRepository;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class HistoryService {

    private final PlayHistoryRepository historyRepository;
    private final SongRepository songRepository;

    public HistoryService(PlayHistoryRepository historyRepository, SongRepository songRepository) {
        this.historyRepository = historyRepository;
        this.songRepository = songRepository;
    }

    @Transactional
    public void recordPlay(User user, Long songId) {
        Song song = songRepository.findById(songId)
                .orElseThrow(() -> new RuntimeException("歌曲不存在"));
        historyRepository.save(new PlayHistory(user, song));
    }

    public List<Long> getRecentSongIds(User user, int limit) {
        return historyRepository.findByUserOrderByPlayedAtDesc(user, Limit.of(limit))
                .stream()
                .map(h -> h.getSong().getId())
                .distinct()
                .toList();
    }
}
