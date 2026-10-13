package com.ericjc.musicplayer.repository;

import com.ericjc.musicplayer.entity.Playlist;
import com.ericjc.musicplayer.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PlaylistRepository extends JpaRepository<Playlist, Long> {
    List<Playlist> findByUserOrderByCreatedAtDesc(User user);
    Optional<Playlist> findByIdAndUser(Long id, User user);
    long countByUser(User user);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Playlist p where p.id = :id and p.user = :user")
    Optional<Playlist> findOwnedForUpdate(@Param("id") Long id, @Param("user") User user);
}
