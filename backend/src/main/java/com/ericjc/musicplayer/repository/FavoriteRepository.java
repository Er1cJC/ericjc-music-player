package com.ericjc.musicplayer.repository;

import com.ericjc.musicplayer.entity.Favorite;
import com.ericjc.musicplayer.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {
    List<Favorite> findByUserOrderByCreatedAtDesc(User user);
    Optional<Favorite> findByUserAndSongId(User user, Long songId);
    boolean existsByUserAndSongId(User user, Long songId);
    void deleteByUserAndSongId(User user, Long songId);
    long countByUser(User user);

    /** 一次查出用户收藏的所有 songId，避免 N+1 查询 */
    @Query("SELECT f.song.id FROM Favorite f WHERE f.user = :user")
    Set<Long> findSongIdsByUser(@Param("user") User user);

    @Transactional
    void deleteBySongId(Long songId);
}
