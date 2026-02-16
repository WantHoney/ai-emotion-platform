package com.wuhao.aiemotion.service;

import com.wuhao.aiemotion.dto.response.AudioDeleteResponse;
import com.wuhao.aiemotion.repository.AudioRepository;
import com.wuhao.aiemotion.service.model.AudioSavedResult;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.UUID;

@Service
public class AudioService {

    private static final Logger log = LoggerFactory.getLogger(AudioService.class);

    private final AudioRepository audioRepository;

    @Value("${upload.base-dir:${user.home}/ai-emotion/uploads}")
    private String baseDir;

    private Path uploadDir;

    public AudioService(AudioRepository audioRepository) {
        this.audioRepository = audioRepository;
    }

    @PostConstruct
    public void initUploadDir() {
        this.uploadDir = Paths.get(baseDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.uploadDir);
            log.info("Upload dir = {}", this.uploadDir);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize upload directory: " + this.uploadDir, e);
        }
    }

    public AudioSavedResult upload(MultipartFile file, Long userId) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("file is empty");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            originalFilename = "audio.wav";
        }

        String ext = getExtension(originalFilename);
        if (ext.isBlank()) {
            ext = ".dat";
        }

        String generatedFileName = UUID.randomUUID().toString().replace("-", "") + ext;

        Path target = uploadDir.resolve(generatedFileName);
        long size = file.getSize();
        String contentType = file.getContentType();
        String targetPath = target.toString();

        try {
            Files.createDirectories(uploadDir);
            log.info("Upload dir = {}", uploadDir);
            log.info("Saving file to = {}", target);

            file.transferTo(target.toFile());

            long id = audioRepository.insertAudio(
                    userId,
                    originalFilename,
                    generatedFileName,
                    target.toString(),
                    contentType,
                    size,
                    null,
                    null
            );

            return new AudioSavedResult(id, originalFilename, generatedFileName, target.toString());
        } catch (Exception e) {
            log.error("File upload failed: name={}, size={}, contentType={}, targetPath={}", originalFilename, size, contentType, targetPath, e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    /**
     * ✅ 软删除：把 audio_file.status 改为 DELETED
     */
    public AudioDeleteResponse deleteSoft(long audioId) {
        if (!audioRepository.existsById(audioId)) {
            throw new IllegalArgumentException("audio 不存在: " + audioId);
        }
        audioRepository.softDelete(audioId);
        return new AudioDeleteResponse(audioId, "DELETED");
    }

    private String getExtension(String filename) {
        String name = filename.trim();
        int lastDot = name.lastIndexOf('.');
        if (lastDot < 0 || lastDot == name.length() - 1) {
            return "";
        }
        String ext = name.substring(lastDot).toLowerCase(Locale.ROOT);
        if (ext.length() > 10) {
            return "";
        }
        return ext;
    }
}
