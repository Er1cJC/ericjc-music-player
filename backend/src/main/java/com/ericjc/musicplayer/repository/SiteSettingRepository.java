package com.ericjc.musicplayer.repository;

import com.ericjc.musicplayer.entity.SiteSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SiteSettingRepository extends JpaRepository<SiteSetting, String> {
}
