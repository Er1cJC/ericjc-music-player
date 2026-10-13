package com.ericjc.musicplayer.repository;

import com.ericjc.musicplayer.entity.PlayHistory;
import com.ericjc.musicplayer.entity.User;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface PlayHistoryRepository extends JpaRepository<PlayHistory, Long> {
    List<PlayHistory> findByUserOrderByPlayedAtDesc(User user, Limit limit);
    long countByUser(User user);

    @Transactional
    void deleteBySongId(Long songId);
}
