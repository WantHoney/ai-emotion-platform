package com.wuhao.aiemotion.controller;

import com.wuhao.aiemotion.service.SystemStatusService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class SystemController {

    private final SystemStatusService systemStatusService;

    public SystemController(SystemStatusService systemStatusService) {
        this.systemStatusService = systemStatusService;
    }

    @GetMapping("/api/system/status")
    public Map<String, Object> status() {
        return systemStatusService.status();
    }
}
