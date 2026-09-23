package com.ericjc.musicplayer.repository;

import com.ericjc.musicplayer.entity.Song;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SongRepository extends JpaRepository<Song, Long> {
}
