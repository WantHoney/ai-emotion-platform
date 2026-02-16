package com.wuhao.aiemotion.controller;

import com.wuhao.aiemotion.config.AuthInterceptor;
import com.wuhao.aiemotion.dto.response.AudioDeleteResponse;
import com.wuhao.aiemotion.dto.response.AudioListResponse;
import com.wuhao.aiemotion.dto.response.AudioUploadResponse;
import com.wuhao.aiemotion.repository.AudioRepository;
import com.wuhao.aiemotion.service.AudioChunkUploadService;
import com.wuhao.aiemotion.service.AudioService;
import com.wuhao.aiemotion.service.AuthService;
import com.wuhao.aiemotion.service.model.AudioSavedResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/audio")
public class AudioController {

    private final AudioService audioService;
    private final AudioChunkUploadService audioChunkUploadService;
    private final AudioRepository audioRepository;

    @Value("${app.upload.public-path:/uploads}")
    private String publicPath;

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public AudioController(AudioService audioService,
                           AudioChunkUploadService audioChunkUploadService,
                           AudioRepository audioRepository) {
        this.audioService = audioService;
        this.audioChunkUploadService = audioChunkUploadService;
        this.audioRepository = audioRepository;
    }

    /**
     * POST /api/audio/upload
     * form-data: file=<mp3>
     */
    @PostMapping("/upload")
    public AudioUploadResponse upload(@RequestParam("file") MultipartFile file, HttpServletRequest request) {
        Long userId = resolveUserId(request);
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
     * 寤鸿榛樿鍙湅 UPLOADED锛堣蒋鍒犻櫎鍚庡垪琛ㄦ墠浼氣€滄秷澶扁€濓級
     */
    @GetMapping("/list")
    public AudioListResponse list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "true") boolean onlyUploaded   // 鉁?鏀逛负 true
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
     * 鉁?杞垹闄わ細DELETE /api/audio/{id}
     */
    @DeleteMapping("/{id}")
    public AudioDeleteResponse delete(@PathVariable long id) {
        return audioService.deleteSoft(id);
    }

    @PostMapping("/upload-sessions/init")
    public Map<String, Object> initSession(@RequestBody @Valid InitUploadSessionRequest request,
                                           HttpServletRequest servletRequest) {
        return audioChunkUploadService.initSession(
                request.fileName(),
                request.contentType(),
                request.fileSize(),
                request.totalChunks(),
                resolveUserId(servletRequest)
        );
    }

    @PutMapping("/upload-sessions/{uploadId}/chunks/{chunkIndex}")
    public Map<String, Object> uploadChunk(@PathVariable String uploadId,
                                           @PathVariable int chunkIndex,
                                           @RequestParam("file") MultipartFile file,
                                           HttpServletRequest servletRequest) {
        return audioChunkUploadService.uploadChunk(uploadId, chunkIndex, file, resolveUserId(servletRequest));
    }

    @GetMapping("/upload-sessions/{uploadId}")
    public Map<String, Object> sessionStatus(@PathVariable String uploadId, HttpServletRequest servletRequest) {
        return audioChunkUploadService.sessionStatus(uploadId, resolveUserId(servletRequest));
    }

    @PostMapping("/upload-sessions/{uploadId}/complete")
    public Map<String, Object> complete(@PathVariable String uploadId,
                                        @RequestBody(required = false) CompleteUploadRequest request,
                                        HttpServletRequest servletRequest) {
        boolean autoStartTask = request != null && Boolean.TRUE.equals(request.autoStartTask());
        return audioChunkUploadService.complete(uploadId, autoStartTask, resolveUserId(servletRequest));
    }

    @DeleteMapping("/upload-sessions/{uploadId}")
    public Map<String, Object> cancel(@PathVariable String uploadId, HttpServletRequest servletRequest) {
        return audioChunkUploadService.cancel(uploadId, resolveUserId(servletRequest));
    }

    private Long resolveUserId(HttpServletRequest request) {
        Object attr = request.getAttribute(AuthInterceptor.AUTH_USER_ATTR);
        if (attr instanceof AuthService.UserProfile user) {
            return user.userId();
        }
        return null;
    }

    public record InitUploadSessionRequest(
            @NotBlank String fileName,
            String contentType,
            @Min(0) Long fileSize,
            @Min(1) Integer totalChunks
    ) {
    }

    public record CompleteUploadRequest(Boolean autoStartTask) {
    }
}
