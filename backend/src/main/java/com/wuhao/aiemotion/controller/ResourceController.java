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
                                  @RequestParam(required = false) Integer pageSize,
                                  @RequestParam(required = false) Integer size,
                                  @RequestParam(required = false) String status,
                                  @RequestParam(required = false) String keyword,
                                  @RequestParam(required = false) String q,
                                  @RequestParam(required = false) String sortBy,
                                  @RequestParam(required = false) String sortOrder,
                                  @RequestParam(required = false) String sort) {
        int resolvedPageSize = pageSize != null ? pageSize : (size != null ? size : 10);
        String resolvedKeyword = firstNonBlank(keyword, q);
        String[] sortValues = resolveSort(sortBy, sortOrder, sort);
        return resourceManagementService.tasks(page, resolvedPageSize, status, resolvedKeyword, sortValues[0], sortValues[1]);
    }

    @GetMapping("/reports")
    public ReportListResponse reports(@RequestParam(defaultValue = "1") int page,
                                      @RequestParam(required = false) Integer pageSize,
                                      @RequestParam(required = false) Integer size,
                                      @RequestParam(required = false) String riskLevel,
                                      @RequestParam(required = false) String emotion,
                                      @RequestParam(required = false) String keyword,
                                      @RequestParam(required = false) String q,
                                      @RequestParam(required = false) String sortBy,
                                      @RequestParam(required = false) String sortOrder) {
        int resolvedPageSize = pageSize != null ? pageSize : (size != null ? size : 10);
        String resolvedKeyword = firstNonBlank(keyword, q);
        String resolvedSortBy = sortBy == null || sortBy.isBlank() ? "createdAt" : sortBy.trim();
        String resolvedSortOrder = sortOrder == null || sortOrder.isBlank() ? "desc" : sortOrder.trim();
        return resourceManagementService.reports(page, resolvedPageSize, riskLevel, emotion, resolvedKeyword, resolvedSortBy, resolvedSortOrder);
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

    private String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary.trim();
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback.trim();
        }
        return null;
    }

    private String[] resolveSort(String sortBy, String sortOrder, String sort) {
        String resolvedSortBy = sortBy;
        String resolvedSortOrder = sortOrder;
        if ((resolvedSortBy == null || resolvedSortBy.isBlank()) && sort != null && !sort.isBlank()) {
            String[] pair = sort.split(",", 2);
            resolvedSortBy = pair[0];
            if (pair.length > 1) {
                resolvedSortOrder = pair[1];
            }
        }
        if (resolvedSortBy == null || resolvedSortBy.isBlank()) {
            resolvedSortBy = "createdAt";
        }
        if (resolvedSortOrder == null || resolvedSortOrder.isBlank()) {
            resolvedSortOrder = "desc";
        }
        return new String[]{resolvedSortBy.trim(), resolvedSortOrder.trim()};
    }
}
