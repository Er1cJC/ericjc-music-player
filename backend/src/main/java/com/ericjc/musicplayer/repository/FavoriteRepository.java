package com.ericjc.musicplayer.repository;

import com.ericjc.musicplayer.entity.Favorite;
import com.ericjc.musicplayer.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {
    List<Favorite> findByUserOrderByCreatedAtDesc(User user);
    Optional<Favorite> findByUserAndSongId(User user, Long songId);
    boolean existsByUserAndSongId(User user, Long songId);
    void deleteByUserAndSongId(User user, Long songId);
    long countByUser(User user);
}
