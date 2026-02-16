package com.wuhao.aiemotion.controller;

import com.wuhao.aiemotion.config.AuthInterceptor;
import com.wuhao.aiemotion.dto.response.AudioDeleteResponse;
import com.wuhao.aiemotion.dto.response.AudioListResponse;
import com.wuhao.aiemotion.dto.response.AudioUploadResponse;
import com.wuhao.aiemotion.repository.AudioRepository;
import com.wuhao.aiemotion.service.AudioService;
import com.wuhao.aiemotion.service.AuthService;
import com.wuhao.aiemotion.service.model.AudioSavedResult;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/audio")
public class AudioController {

    private final AudioService audioService;
    private final AudioRepository audioRepository;

    @Value("${app.upload.public-path:/uploads}")
    private String publicPath;

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public AudioController(AudioService audioService, AudioRepository audioRepository) {
        this.audioService = audioService;
        this.audioRepository = audioRepository;
    }

    /**
     * POST /api/audio/upload
     * form-data: file=<mp3>
     */
    @PostMapping("/upload")
    public AudioUploadResponse upload(@RequestParam("file") MultipartFile file, HttpServletRequest request) {
        Long userId = null;
        Object attr = request.getAttribute(AuthInterceptor.AUTH_USER_ATTR);
        if (attr instanceof AuthService.UserProfile user) {
            userId = user.userId();
        }
        AudioSavedResult saved = audioService.upload(file, userId);

        return new AudioUploadResponse(
                saved.audioId(),
                saved.originalName(),
                saved.fileName(),
                publicPath + "/" + saved.fileName()
        );
    }

    /**
     * GET /api/audio/list?page=1&size=10
     * 建议默认只看 UPLOADED（软删除后列表才会“消失”）
     */
    @GetMapping("/list")
    public AudioListResponse list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "true") boolean onlyUploaded   // ✅ 改为 true
    ) {
        if (page < 1) page = 1;
        if (size < 1) size = 10;
        if (size > 100) size = 100;

        int offset = (page - 1) * size;

        var list = (userId == null)
                ? audioRepository.findPage(offset, size, onlyUploaded)
                : audioRepository.findPageByUser(userId, offset, size, onlyUploaded);

        long total = (userId == null)
                ? audioRepository.countAll(onlyUploaded)
                : audioRepository.countByUser(userId, onlyUploaded);

        var items = list.stream()
                .map(a -> new AudioListResponse.Item(
                        a.id(),
                        a.userId(),
                        a.originalName(),
                        a.storedName(),
                        publicPath + "/" + a.storedName(),
                        a.sizeBytes(),
                        a.durationMs(),
                        a.status(),
                        a.createdAt() == null ? null : a.createdAt().format(FMT)
                ))
                .collect(Collectors.toList());

        return new AudioListResponse(total, page, size, items);
    }

    /**
     * ✅ 软删除：DELETE /api/audio/{id}
     */
    @DeleteMapping("/{id}")
    public AudioDeleteResponse delete(@PathVariable long id) {
        return audioService.deleteSoft(id);
    }
}
