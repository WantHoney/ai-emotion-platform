package com.wuhao.aiemotion.service;

import com.wuhao.aiemotion.dto.response.AnalysisTaskStartResponse;
import com.wuhao.aiemotion.repository.AudioRepository;
import com.wuhao.aiemotion.repository.AudioUploadSessionRepository;
import com.wuhao.aiemotion.repository.AudioUploadSessionRepository.AudioUploadSessionEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class AudioChunkUploadService {

    private static final int MAX_TOTAL_CHUNKS = 4000;
    private static final long MAX_CHUNK_SIZE_BYTES = 10L * 1024 * 1024;

    private final AudioUploadSessionRepository uploadSessionRepository;
    private final AudioRepository audioRepository;
    private final AnalysisTaskService analysisTaskService;

    @Value("${upload.base-dir:${user.home}/ai-emotion/uploads}")
    private String baseDir;

    public AudioChunkUploadService(AudioUploadSessionRepository uploadSessionRepository,
                                   AudioRepository audioRepository,
                                   AnalysisTaskService analysisTaskService) {
        this.uploadSessionRepository = uploadSessionRepository;
        this.audioRepository = audioRepository;
        this.analysisTaskService = analysisTaskService;
    }

    @Transactional
    public Map<String, Object> initSession(String originalName,
                                           String contentType,
                                           Long fileSize,
                                           Integer totalChunks,
                                           Long userId) {
        if (originalName == null || originalName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fileName is required");
        }
        int safeTotalChunks = totalChunks == null ? 1 : totalChunks;
        if (safeTotalChunks < 1 || safeTotalChunks > MAX_TOTAL_CHUNKS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "totalChunks out of range");
        }
        if (fileSize != null && fileSize < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fileSize must be >= 0");
        }

        String uploadId = "up_" + UUID.randomUUID().toString().replace("-", "");
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(24);
        long sessionId = uploadSessionRepository.createSession(
                uploadId,
                userId,
                originalName.trim(),
                contentType == null || contentType.isBlank() ? null : contentType.trim(),
                fileSize,
                safeTotalChunks,
                expiresAt
        );
        ensureChunkDir(uploadId);
        return Map.of(
                "sessionId", sessionId,
                "uploadId", uploadId,
                "status", "INIT",
                "totalChunks", safeTotalChunks,
                "receivedChunks", 0,
                "expiresAt", expiresAt.toString()
        );
    }

    @Transactional
    public Map<String, Object> uploadChunk(String uploadId,
                                           int chunkIndex,
                                           MultipartFile file,
                                           Long userId) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "chunk file is empty");
        }
        if (file.getSize() > MAX_CHUNK_SIZE_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "chunk size too large");
        }

        AudioUploadSessionEntity session = requireSession(uploadId);
        assertSessionOwner(session, userId);
        assertSessionWritable(session);

        if (chunkIndex < 0 || chunkIndex >= session.totalChunks()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "chunkIndex out of range");
        }
        if (session.expiresAt() != null && session.expiresAt().isBefore(LocalDateTime.now())) {
            uploadSessionRepository.markFailed(session.id());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "upload session expired");
        }

        try {
            Path target = chunkPath(uploadId, chunkIndex);
            Files.createDirectories(target.getParent());
            file.transferTo(target);

            String chunkSha256 = sha256(target);
            uploadSessionRepository.upsertChunk(
                    session.id(),
                    chunkIndex,
                    file.getSize(),
                    chunkSha256,
                    target.toString()
            );

            int received = uploadSessionRepository.countReceivedChunks(session.id());
            String status = received >= session.totalChunks() ? "UPLOADING" : "UPLOADING";
            uploadSessionRepository.updateProgress(session.id(), received, status);

            int percent = (int) Math.floor((received * 100.0D) / Math.max(1, session.totalChunks()));
            return Map.of(
                    "uploadId", uploadId,
                    "chunkIndex", chunkIndex,
                    "receivedChunks", received,
                    "totalChunks", session.totalChunks(),
                    "progressPercent", percent,
                    "completed", received >= session.totalChunks()
            );
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            uploadSessionRepository.markFailed(session.id());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "chunk upload failed");
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> sessionStatus(String uploadId, Long userId) {
        AudioUploadSessionEntity session = requireSession(uploadId);
        assertSessionOwner(session, userId);
        List<Map<String, Object>> chunks = uploadSessionRepository.listChunksBySession(session.id());
        List<Integer> uploadedIndexes = new ArrayList<>();
        for (Map<String, Object> row : chunks) {
            Object idx = row.get("chunk_index");
            if (idx instanceof Number number) {
                uploadedIndexes.add(number.intValue());
            }
        }
        uploadedIndexes.sort(Comparator.naturalOrder());
        int progress = (int) Math.floor((session.receivedChunks() * 100.0D) / Math.max(1, session.totalChunks()));
        Map<String, Object> payload = new HashMap<>();
        payload.put("uploadId", session.uploadId());
        payload.put("status", session.status());
        payload.put("totalChunks", session.totalChunks());
        payload.put("receivedChunks", session.receivedChunks());
        payload.put("progressPercent", progress);
        payload.put("uploadedChunkIndexes", uploadedIndexes);
        payload.put("mergedAudioId", session.mergedAudioId());
        payload.put("expiresAt", session.expiresAt() == null ? null : session.expiresAt().toString());
        return payload;
    }

    @Transactional
    public Map<String, Object> complete(String uploadId,
                                        boolean autoStartTask,
                                        Long userId) {
        AudioUploadSessionEntity session = requireSession(uploadId);
        assertSessionOwner(session, userId);
        assertSessionWritable(session);

        int received = uploadSessionRepository.countReceivedChunks(session.id());
        if (received < session.totalChunks()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "chunks not complete");
        }

        String generatedFileName = UUID.randomUUID().toString().replace("-", "") + detectExt(session.originalName());
        Path mergedTarget = resolveUploadDir().resolve(generatedFileName);

        try {
            Files.createDirectories(mergedTarget.getParent());
            try (OutputStream outputStream = Files.newOutputStream(mergedTarget)) {
                for (int i = 0; i < session.totalChunks(); i++) {
                    Path chunk = chunkPath(uploadId, i);
                    if (!Files.exists(chunk)) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "missing chunk index=" + i);
                    }
                    try (InputStream inputStream = Files.newInputStream(chunk)) {
                        inputStream.transferTo(outputStream);
                    }
                }
            }

            long size = Files.size(mergedTarget);
            long audioId = audioRepository.insertAudio(
                    userId,
                    session.originalName(),
                    generatedFileName,
                    mergedTarget.toString(),
                    session.contentType(),
                    size,
                    null,
                    null
            );
            uploadSessionRepository.markMerged(session.id(), audioId);
            cleanupChunkDir(uploadId);
            uploadSessionRepository.deleteChunksBySession(session.id());

            Long taskId = null;
            String taskNo = null;
            if (autoStartTask) {
                if (userId == null) {
                    throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "login required");
                }
                AnalysisTaskStartResponse start = analysisTaskService.startTask(audioId, new AuthService.UserProfile(userId, "session-user", AuthService.ROLE_USER));
                taskId = start.taskId();
                taskNo = start.taskNo();
            }
            Map<String, Object> payload = new HashMap<>();
            payload.put("uploadId", uploadId);
            payload.put("audioId", audioId);
            payload.put("taskId", taskId);
            payload.put("taskNo", taskNo);
            payload.put("fileName", generatedFileName);
            payload.put("fileUrl", "/uploads/" + generatedFileName);
            payload.put("status", "MERGED");
            return payload;
        } catch (ResponseStatusException e) {
            uploadSessionRepository.markFailed(session.id());
            throw e;
        } catch (Exception e) {
            uploadSessionRepository.markFailed(session.id());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "merge chunks failed");
        }
    }

    @Transactional
    public Map<String, Object> cancel(String uploadId, Long userId) {
        AudioUploadSessionEntity session = requireSession(uploadId);
        assertSessionOwner(session, userId);
        uploadSessionRepository.markCanceled(session.id());
        uploadSessionRepository.deleteChunksBySession(session.id());
        cleanupChunkDir(uploadId);
        return Map.of(
                "uploadId", uploadId,
                "status", "CANCELED"
        );
    }

    private AudioUploadSessionEntity requireSession(String uploadId) {
        return uploadSessionRepository.findByUploadId(uploadId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "upload session not found"));
    }

    private void assertSessionOwner(AudioUploadSessionEntity session, Long userId) {
        if (session.userId() == null) {
            return;
        }
        if (!session.userId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "no permission for this upload session");
        }
    }

    private void assertSessionWritable(AudioUploadSessionEntity session) {
        if ("MERGED".equalsIgnoreCase(session.status()) || "CANCELED".equalsIgnoreCase(session.status())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "upload session is not writable");
        }
    }

    private Path resolveUploadDir() {
        return Paths.get(baseDir).toAbsolutePath().normalize();
    }

    private Path chunkRootDir() {
        return resolveUploadDir().resolve(".chunks");
    }

    private void ensureChunkDir(String uploadId) {
        try {
            Files.createDirectories(chunkRootDir().resolve(uploadId));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "init upload session directory failed");
        }
    }

    private Path chunkPath(String uploadId, int chunkIndex) {
        String name = String.format(Locale.ROOT, "part-%06d", chunkIndex);
        return chunkRootDir().resolve(uploadId).resolve(name);
    }

    private void cleanupChunkDir(String uploadId) {
        Path dir = chunkRootDir().resolve(uploadId);
        if (!Files.exists(dir)) {
            return;
        }
        try (var stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (Exception ignored) {
                }
            });
        } catch (Exception ignored) {
        }
    }

    private String detectExt(String originalName) {
        if (originalName == null || originalName.isBlank()) {
            return ".dat";
        }
        int idx = originalName.lastIndexOf('.');
        if (idx < 0 || idx >= originalName.length() - 1) {
            return ".dat";
        }
        String ext = originalName.substring(idx).toLowerCase(Locale.ROOT);
        if (ext.length() > 10) {
            return ".dat";
        }
        return ext;
    }

    private String sha256(Path file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream inputStream = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = inputStream.read(buffer)) > 0) {
                    digest.update(buffer, 0, len);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception e) {
            return null;
        }
    }
}
