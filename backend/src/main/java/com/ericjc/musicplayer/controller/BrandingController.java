package com.ericjc.musicplayer.controller;

import com.ericjc.musicplayer.entity.SiteSetting;
import com.ericjc.musicplayer.entity.User;
import com.ericjc.musicplayer.repository.SiteSettingRepository;
import com.ericjc.musicplayer.service.FileUploadService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class BrandingController {

    private static final String LOGO_KEY = "site.logo_url";

    private final SiteSettingRepository siteSettingRepository;
    private final FileUploadService fileUploadService;

    public BrandingController(SiteSettingRepository siteSettingRepository, FileUploadService fileUploadService) {
        this.siteSettingRepository = siteSettingRepository;
        this.fileUploadService = fileUploadService;
    }

    @GetMapping("/branding")
    public ResponseEntity<?> getBranding() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("logoUrl", siteSettingRepository.findById(LOGO_KEY).map(SiteSetting::getSettingValue).orElse(null));
        return ResponseEntity.ok(result);
    }

    @PostMapping("/admin/branding/logo")
    @Transactional
    public ResponseEntity<?> uploadLogo(@AuthenticationPrincipal User admin,
                                        @RequestParam("file") MultipartFile file) {
        if (admin == null || !admin.isAdmin()) return ResponseEntity.status(403).body(Map.of("error", "无权限"));
        if (file == null || file.isEmpty() || file.getContentType() == null || !file.getContentType().startsWith("image/")) {
            return ResponseEntity.badRequest().body(Map.of("error", "请选择图片文件"));
        }

        String oldUrl = siteSettingRepository.findById(LOGO_KEY).map(SiteSetting::getSettingValue).orElse(null);
        try {
            String url = fileUploadService.uploadFile(file, "branding");
            siteSettingRepository.save(new SiteSetting(LOGO_KEY, url));
            if (oldUrl != null) fileUploadService.deleteFile(oldUrl);
            return ResponseEntity.ok(Map.of("logoUrl", url));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "网站图标上传失败: " + e.getMessage()));
        }
    }

    @DeleteMapping("/admin/branding/logo")
    @Transactional
    public ResponseEntity<?> deleteLogo(@AuthenticationPrincipal User admin) {
        if (admin == null || !admin.isAdmin()) return ResponseEntity.status(403).body(Map.of("error", "无权限"));
        SiteSetting setting = siteSettingRepository.findById(LOGO_KEY).orElse(null);
        if (setting != null) {
            fileUploadService.deleteFile(setting.getSettingValue());
            siteSettingRepository.delete(setting);
        }
        return ResponseEntity.ok(Map.of("logoUrl", ""));
    }
}
