package com.wuhao.aiemotion.controller;

import com.wuhao.aiemotion.service.AdminUserService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    public Map<String, Object> users(@RequestParam(defaultValue = "1") int page,
                                     @RequestParam(defaultValue = "10") int pageSize,
                                     @RequestParam(required = false) String keyword,
                                     @RequestParam(required = false) String role,
                                     @RequestParam(required = false) String status,
                                     @RequestParam(required = false) String accountType) {
        return adminUserService.listUsers(page, pageSize, keyword, role, status, accountType);
    }

    @GetMapping("/{userId}")
    public Map<String, Object> userDetail(@PathVariable long userId) {
        return adminUserService.getUserDetail(userId);
    }

    @PutMapping("/{userId}/status")
    public Map<String, Object> updateUserStatus(@PathVariable long userId,
                                                @RequestBody UpdateUserStatusRequest request) {
        adminUserService.updateUserStatus(userId, request.status());
        return Map.of("success", true);
    }

    public record UpdateUserStatusRequest(@NotBlank String status) {
    }
}
