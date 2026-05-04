package com.wuhao.aiemotion.service;

import com.wuhao.aiemotion.repository.AdminUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class AdminUserService {

    private static final List<String> ALLOWED_USER_STATUSES = List.of("ACTIVE", "DISABLED");
    private static final List<String> ALLOWED_ACCOUNT_TYPES = List.of("ALL", "REAL_ONLY", "TEST_ONLY");

    private final AdminUserRepository adminUserRepository;

    public AdminUserService(AdminUserRepository adminUserRepository) {
        this.adminUserRepository = adminUserRepository;
    }

    public Map<String, Object> listUsers(int page,
                                         int pageSize,
                                         String keyword,
                                         String role,
                                         String status,
                                         String accountType) {
        int safePage = Math.max(1, page);
        int safePageSize = Math.min(100, Math.max(1, pageSize));
        int offset = (safePage - 1) * safePageSize;

        String normalizedRole = normalizeRoleFilter(role);
        String normalizedStatus = normalizeStatusFilter(status);
        String normalizedAccountType = normalizeAccountType(accountType);

        long total = adminUserRepository.countUsers(keyword, normalizedRole, normalizedStatus, normalizedAccountType);
        List<Map<String, Object>> items = adminUserRepository.listUsers(
                offset,
                safePageSize,
                normalizeKeyword(keyword),
                normalizedRole,
                normalizedStatus,
                normalizedAccountType
        );
        return Map.of(
                "items", items,
                "total", total,
                "page", safePage,
                "pageSize", safePageSize,
                "accountType", normalizedAccountType,
                "systemActiveSessions", adminUserRepository.countActiveSessions()
        );
    }

    public Map<String, Object> getUserDetail(long userId) {
        Map<String, Object> summary = adminUserRepository.findUserSummaryById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "user not found: " + userId));
        return Map.of(
                "profile", summary,
                "recentTasks", adminUserRepository.listRecentTasksByUser(userId, 8),
                "recentReports", adminUserRepository.listRecentReportsByUser(userId, 8),
                "recentWarnings", adminUserRepository.listRecentWarningsByUser(userId, 8)
        );
    }

    public void updateUserStatus(long userId, String status) {
        String normalizedStatus = normalizeRequiredStatus(status);
        Map<String, Object> summary = adminUserRepository.findUserSummaryById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "user not found: " + userId));

        String roleCode = String.valueOf(summary.getOrDefault("role_code", AuthService.ROLE_USER)).toUpperCase(Locale.ROOT);
        if (AuthService.ROLE_ADMIN.equals(roleCode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "admin account status cannot be changed here");
        }

        int updated = adminUserRepository.updateUserStatus(userId, normalizedStatus);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "user not found: " + userId);
        }
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.trim();
    }

    private String normalizeRoleFilter(String role) {
        if (role == null || role.isBlank()) {
            return null;
        }
        String normalized = role.trim().toUpperCase(Locale.ROOT);
        if (!AuthService.ROLE_USER.equals(normalized) && !AuthService.ROLE_ADMIN.equals(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unsupported role filter");
        }
        return normalized;
    }

    private String normalizeStatusFilter(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        return normalizeRequiredStatus(status);
    }

    private String normalizeAccountType(String accountType) {
        if (accountType == null || accountType.isBlank()) {
            return "ALL";
        }
        String normalized = accountType.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_ACCOUNT_TYPES.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unsupported account type");
        }
        return normalized;
    }

    private String normalizeRequiredStatus(String status) {
        String normalized = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_USER_STATUSES.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unsupported user status");
        }
        return normalized;
    }
}
