package com.wuhao.aiemotion.service;

import com.wuhao.aiemotion.repository.AuthRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Service
public class AuthService {

    public static final String ROLE_USER = "USER";
    public static final String ROLE_ADMIN = "ADMIN";
    private static final String USER_STATUS_ACTIVE = "ACTIVE";

    private final AuthRepository authRepository;
    private final PasswordEncoder passwordEncoder;

    private final long accessTokenTtlSeconds;
    private final long refreshTokenTtlSeconds;
    private final String adminUsername;
    private final String adminPassword;

    public AuthService(
            AuthRepository authRepository,
            PasswordEncoder passwordEncoder,
            @Value("${auth.access-token-ttl-seconds:7200}") long accessTokenTtlSeconds,
            @Value("${auth.refresh-token-ttl-seconds:604800}") long refreshTokenTtlSeconds,
            @Value("${auth.seed-admin.username:operator}") String adminUsername,
            @Value("${auth.seed-admin.password:operator123}") String adminPassword
    ) {
        this.authRepository = authRepository;
        this.passwordEncoder = passwordEncoder;
        this.accessTokenTtlSeconds = accessTokenTtlSeconds;
        this.refreshTokenTtlSeconds = refreshTokenTtlSeconds;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
    }

    @PostConstruct
    @Transactional
    public void initAuthData() {
        authRepository.ensureSessionTable();
        authRepository.ensureRole(ROLE_USER, "普通用户");
        authRepository.ensureRole(ROLE_ADMIN, "运营管理员");

        String normalizedAdminUsername = normalizeUsername(adminUsername);
        validatePassword(adminPassword);

        AuthRepository.DbUser existing = authRepository.findUserWithRoleByUsername(normalizedAdminUsername).orElse(null);
        if (existing == null) {
            long userId = authRepository.insertUser(normalizedAdminUsername, passwordEncoder.encode(adminPassword), USER_STATUS_ACTIVE);
            authRepository.bindRole(userId, ROLE_ADMIN);
        }
    }

    @Transactional
    public AuthTokens register(String username, String password) {
        String normalizedUsername = normalizeUsername(username);
        validatePassword(password);

        if (authRepository.findUserByUsername(normalizedUsername).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "用户名已存在");
        }

        long userId = authRepository.insertUser(normalizedUsername, passwordEncoder.encode(password), USER_STATUS_ACTIVE);
        authRepository.bindRole(userId, ROLE_USER);

        AuthRepository.DbUser user = authRepository.findUserWithRoleById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "用户创建失败"));
        return createSession(user);
    }

    @Transactional
    public AuthTokens login(String username, String password, String requiredRole) {
        String normalizedUsername = normalizeUsername(username);
        AuthRepository.DbUser user = authRepository.findUserWithRoleByUsername(normalizedUsername)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误"));

        if (!USER_STATUS_ACTIVE.equals(user.status()) || !passwordEncoder.matches(password, user.passwordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }

        if (requiredRole != null && !requiredRole.equals(user.roleCode())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "账号无权限进入该端");
        }

        authRepository.updateLastLoginAt(user.id());
        return createSession(user);
    }

    @Transactional
    public AuthTokens refresh(String refreshToken) {
        AuthRepository.DbSession session = authRepository.findSessionByRefreshToken(refreshToken)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "refresh token 无效或已过期"));

        if (session.refreshExpireAt().isBefore(Instant.now())) {
            authRepository.deleteSessionByRefreshToken(refreshToken);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "refresh token 无效或已过期");
        }

        authRepository.deleteSessionByRefreshToken(refreshToken);

        AuthRepository.DbUser user = authRepository.findUserWithRoleById(session.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户不存在或已被禁用"));
        return createSession(user);
    }

    @Transactional
    public UserProfile requireValidUser(String accessToken) {
        AuthRepository.DbSession session = authRepository.findSessionByAccessToken(accessToken)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "登录已过期，请重新登录"));

        if (session.accessExpireAt().isBefore(Instant.now())) {
            authRepository.deleteSessionByAccessToken(accessToken);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "登录已过期，请重新登录");
        }

        AuthRepository.DbUser user = authRepository.findUserWithRoleById(session.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户不存在或已被禁用"));

        if (!USER_STATUS_ACTIVE.equals(user.status())) {
            authRepository.deleteSessionByAccessToken(accessToken);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户不可用，请联系管理员");
        }

        return new UserProfile(user.id(), user.username(), user.roleCode());
    }

    public void requireRole(UserProfile userProfile, Set<String> roles) {
        if (!roles.contains(userProfile.role())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权限访问该接口");
        }
    }

    @Transactional
    public void logout(String accessToken) {
        authRepository.deleteSessionByAccessToken(accessToken);
    }

    private AuthTokens createSession(AuthRepository.DbUser user) {
        Instant now = Instant.now();
        String accessToken = "atk_" + UUID.randomUUID();
        String refreshToken = "rtk_" + UUID.randomUUID();

        authRepository.insertSession(
                user.id(),
                accessToken,
                refreshToken,
                now.plusSeconds(accessTokenTtlSeconds),
                now.plusSeconds(refreshTokenTtlSeconds)
        );

        UserProfile userProfile = new UserProfile(user.id(), user.username(), user.roleCode());
        return new AuthTokens(accessToken, refreshToken, accessTokenTtlSeconds, refreshTokenTtlSeconds, userProfile);
    }

    private String normalizeUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户名不能为空");
        }
        return username.trim().toLowerCase();
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "密码至少 8 位");
        }
        boolean hasLetter = password.chars().anyMatch(Character::isLetter);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "密码需包含字母和数字");
        }
    }

    public record AuthTokens(String accessToken, String refreshToken, long accessExpiresIn, long refreshExpiresIn, UserProfile user) {
    }

    public record UserProfile(long userId, String username, String role) {
    }
}
