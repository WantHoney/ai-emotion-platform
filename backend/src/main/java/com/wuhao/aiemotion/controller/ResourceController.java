package com.wuhao.aiemotion.controller;

import com.wuhao.aiemotion.dto.response.AdminMetricsResponse;
import com.wuhao.aiemotion.dto.response.AudioListResponse;
import com.wuhao.aiemotion.dto.response.ReportListResponse;
import com.wuhao.aiemotion.dto.response.TaskListResponse;
import com.wuhao.aiemotion.service.ResourceManagementService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ResourceController {

    private final ResourceManagementService resourceManagementService;

    public ResourceController(ResourceManagementService resourceManagementService) {
        this.resourceManagementService = resourceManagementService;
    }

    @GetMapping("/tasks")
    public TaskListResponse tasks(@RequestParam(defaultValue = "1") int page,
                                  @RequestParam(defaultValue = "10") int size,
                                  @RequestParam(required = false) String status,
                                  @RequestParam(defaultValue = "createdAt,desc") String sort) {
        return resourceManagementService.tasks(page, size, status, sort);
    }

    @GetMapping("/reports")
    public ReportListResponse reports(@RequestParam(defaultValue = "1") int page,
                                      @RequestParam(defaultValue = "10") int size,
                                      @RequestParam(required = false) String riskLevel,
                                      @RequestParam(required = false) String emotion,
                                      @RequestParam(required = false) String q) {
        return resourceManagementService.reports(page, size, riskLevel, emotion, q);
    }

    @GetMapping("/reports/{reportId}")
    public ReportListResponse.ReportDTO report(@PathVariable long reportId) {
        return resourceManagementService.report(reportId);
    }

    @DeleteMapping("/reports/{reportId}")
    public ResponseEntity<Void> deleteReport(@PathVariable long reportId) {
        resourceManagementService.deleteReport(reportId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/audios")
    public AudioListResponse audios(@RequestParam(defaultValue = "1") int page,
                                    @RequestParam(defaultValue = "10") int size,
                                    @RequestParam(required = false) String q) {
        return resourceManagementService.audios(page, size, q);
    }

    @DeleteMapping("/audios/{audioId}")
    public ResponseEntity<Void> deleteAudio(@PathVariable long audioId) {
        resourceManagementService.deleteAudio(audioId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/admin/metrics")
    public AdminMetricsResponse metrics() {
        return resourceManagementService.metrics();
    }
}
