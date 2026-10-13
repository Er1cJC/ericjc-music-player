package com.ericjc.musicplayer.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class FileUploadService {

    private final Path uploadPath;

    public FileUploadService(@Value("${app.upload-dir:uploads}") String uploadDir) {
        this.uploadPath = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    public String uploadFile(MultipartFile file, String subDir) throws IOException {
        Path dir = uploadPath.resolve(subDir);
        Files.createDirectories(dir);

        String originalName = file.getOriginalFilename();
        String ext = "";
        if (originalName != null && originalName.contains(".")) {
            ext = originalName.substring(originalName.lastIndexOf("."));
        }
        String storedName = UUID.randomUUID().toString() + ext;

        Path target = dir.resolve(storedName);
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

        return "/uploads/" + subDir + "/" + storedName;
    }

    public void deleteFile(String fileUrl) {
        if (fileUrl == null || !fileUrl.startsWith("/uploads/")) {
            return;
        }
        try {
            // URL 里空格是 %20、中文是 %E4...，先解码回真实文件名再删除
            String relativePath = URLDecoder.decode(fileUrl.substring("/uploads/".length()), StandardCharsets.UTF_8);
            Path target = uploadPath.resolve(relativePath).normalize();
            if (target.startsWith(uploadPath)) {
                Files.deleteIfExists(target);
            }
        } catch (IOException | IllegalArgumentException ignored) {
        }
    }
}
