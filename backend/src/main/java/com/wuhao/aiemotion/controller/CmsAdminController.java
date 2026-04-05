package com.wuhao.aiemotion.controller;

import com.wuhao.aiemotion.domain.PsyCenter;
import com.wuhao.aiemotion.service.ContentHubService;
import com.wuhao.aiemotion.service.CmsService;
import com.wuhao.aiemotion.service.PsyCenterService;
import jakarta.validation.constraints.NotBlank;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class CmsAdminController {

    private final CmsService cmsService;
    private final ContentHubService contentHubService;
    private final PsyCenterService psyCenterService;

    public CmsAdminController(CmsService cmsService, ContentHubService contentHubService, PsyCenterService psyCenterService) {
        this.cmsService = cmsService;
        this.contentHubService = contentHubService;
        this.psyCenterService = psyCenterService;
    }

    @GetMapping("/banners")
    public List<Map<String, Object>> banners() { return cmsService.listBanners(); }

    @PostMapping("/banners")
    public void createBanner(@RequestBody BannerRequest request) {
        cmsService.createBanner(request.title(), request.imageUrl(), request.linkUrl(), request.sortOrder(),
                request.recommended(), request.enabled(), request.startsAt(), request.endsAt());
    }

    @PutMapping("/banners/{id}")
    public void updateBanner(@PathVariable long id, @RequestBody BannerRequest request) {
        cmsService.updateBanner(id, request.title(), request.imageUrl(), request.linkUrl(), request.sortOrder(),
                request.recommended(), request.enabled(), request.startsAt(), request.endsAt());
    }

    @DeleteMapping("/banners/{id}")
    public void deleteBanner(@PathVariable long id) { cmsService.deleteBanner(id); }

    @GetMapping("/quotes")
    public List<Map<String, Object>> quotes() { return cmsService.listQuotes(); }

    @PostMapping("/quotes")
    public void createQuote(@RequestBody QuoteRequest request) {
        cmsService.createQuote(
                request.content(),
                request.author(),
                request.sortOrder(),
                request.recommended(),
                request.enabled(),
                request.isActiveOrDefault()
        );
    }

    @PutMapping("/quotes/{id}")
    public void updateQuote(@PathVariable long id, @RequestBody QuoteRequest request) {
        cmsService.updateQuote(
                id,
                request.content(),
                request.author(),
                request.sortOrder(),
                request.recommended(),
                request.enabled(),
                request.isActiveOrDefault()
        );
    }

    @DeleteMapping("/quotes/{id}")
    public void deleteQuote(@PathVariable long id) { cmsService.deleteQuote(id); }

    @GetMapping("/articles")
    public List<Map<String, Object>> articles() { return cmsService.listArticles(); }

    @PostMapping("/articles")
    public void createArticle(@RequestBody ArticleRequest request) {
        cmsService.createArticle(
                request.title(),
                request.coverImageUrl(),
                request.summary(),
                request.recommendReason(),
                request.fitFor(),
                request.highlights(),
                request.readingMinutes(),
                request.category(),
                request.sourceName(),
                request.sourceUrlOrFallback(),
                request.isExternalOrDefault(),
                request.difficultyTag(),
                request.sortOrder(),
                request.recommended(),
                request.enabled(),
                request.isActiveOrDefault(),
                request.publishedAt()
        );
    }

    @PutMapping("/articles/{id}")
    public void updateArticle(@PathVariable long id, @RequestBody ArticleRequest request) {
        cmsService.updateArticle(
                id,
                request.title(),
                request.coverImageUrl(),
                request.summary(),
                request.recommendReason(),
                request.fitFor(),
                request.highlights(),
                request.readingMinutes(),
                request.category(),
                request.sourceName(),
                request.sourceUrlOrFallback(),
                request.isExternalOrDefault(),
                request.difficultyTag(),
                request.sortOrder(),
                request.recommended(),
                request.enabled(),
                request.isActiveOrDefault(),
                request.publishedAt()
        );
    }

    @DeleteMapping("/articles/{id}")
    public void deleteArticle(@PathVariable long id) { cmsService.deleteArticle(id); }

    @GetMapping("/books")
    public List<Map<String, Object>> books() { return cmsService.listBooks(); }

    @PostMapping("/books")
    public void createBook(@RequestBody BookRequest request) {
        cmsService.createBook(
                request.title(),
                request.author(),
                request.coverImageUrl(),
                request.description(),
                request.category(),
                request.recommendReason(),
                request.fitFor(),
                request.highlights(),
                request.purchaseUrl(),
                request.sortOrder(),
                request.recommended(),
                request.enabled(),
                request.isActiveOrDefault()
        );
    }

    @PutMapping("/books/{id}")
    public void updateBook(@PathVariable long id, @RequestBody BookRequest request) {
        cmsService.updateBook(
                id,
                request.title(),
                request.author(),
                request.coverImageUrl(),
                request.description(),
                request.category(),
                request.recommendReason(),
                request.fitFor(),
                request.highlights(),
                request.purchaseUrl(),
                request.sortOrder(),
                request.recommended(),
                request.enabled(),
                request.isActiveOrDefault()
        );
    }

    @DeleteMapping("/books/{id}")
    public void deleteBook(@PathVariable long id) { cmsService.deleteBook(id); }

    @GetMapping("/content/schedules")
    public List<Map<String, Object>> schedules(@RequestParam(required = false) LocalDate date) {
        return cmsService.listSchedules(date);
    }

    @PostMapping("/content/schedules")
    public void createSchedule(@RequestBody ScheduleRequest request) {
        cmsService.createSchedule(
                request.scheduleDate(),
                request.themeKey(),
                request.themeTitle(),
                request.themeSubtitle(),
                request.quoteId(),
                request.status(),
                request.items().stream()
                        .map(item -> new CmsService.ScheduleItemInput(
                                item.contentType(),
                                item.contentId(),
                                item.slotRole(),
                                item.sortOrder()
                        ))
                        .toList()
        );
    }

    @PutMapping("/content/schedules/{id}")
    public void updateSchedule(@PathVariable long id, @RequestBody ScheduleRequest request) {
        cmsService.updateSchedule(
                id,
                request.scheduleDate(),
                request.themeKey(),
                request.themeTitle(),
                request.themeSubtitle(),
                request.quoteId(),
                request.status(),
                request.items().stream()
                        .map(item -> new CmsService.ScheduleItemInput(
                                item.contentType(),
                                item.contentId(),
                                item.slotRole(),
                                item.sortOrder()
                        ))
                        .toList()
        );
    }

    @DeleteMapping("/content/schedules/{id}")
    public void deleteSchedule(@PathVariable long id) {
        cmsService.deleteSchedule(id);
    }

    @GetMapping("/content/schedules/preview")
    public Map<String, Object> previewSchedule(@RequestParam(required = false) LocalDate date) {
        return contentHubService.previewDailyPackage(date);
    }

    @GetMapping("/psy-centers")
    public List<PsyCenter> psyCenters() { return psyCenterService.adminList(); }

    @PostMapping("/psy-centers")
    public void createPsyCenter(@RequestBody PsyCenterRequest request) {
        psyCenterService.adminCreate(request.name(), request.cityCode(), request.cityName(), request.district(), request.address(),
                request.phone(), request.latitude(), request.longitude(), request.sourceName(), request.sourceUrl(),
                request.sourceLevel(), request.recommended(), request.enabled(), request.isActiveOrDefault());
    }

    @PutMapping("/psy-centers/{id}")
    public void updatePsyCenter(@PathVariable long id, @RequestBody PsyCenterRequest request) {
        psyCenterService.adminUpdate(id, request.name(), request.cityCode(), request.cityName(), request.district(), request.address(),
                request.phone(), request.latitude(), request.longitude(), request.sourceName(), request.sourceUrl(),
                request.sourceLevel(), request.recommended(), request.enabled(), request.isActiveOrDefault());
    }

    @DeleteMapping("/psy-centers/{id}")
    public void deletePsyCenter(@PathVariable long id) { psyCenterService.adminDelete(id); }

    @GetMapping("/psy-centers/export")
    public ResponseEntity<String> exportPsyCenters() throws IOException {
        StringWriter writer = new StringWriter();
        try (CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT)) {
            printer.printRecord(
                    "name", "cityCode", "cityName", "district", "address", "phone", "latitude", "longitude",
                    "sourceName", "sourceUrl", "sourceLevel", "recommended", "enabled", "isActive", "seedKey", "dataSource"
            );
            for (PsyCenter center : psyCenterService.adminList()) {
                printer.printRecord(
                        center.name(),
                        center.cityCode(),
                        center.cityName(),
                        center.district(),
                        center.address(),
                        center.phone(),
                        center.latitude(),
                        center.longitude(),
                        center.sourceName(),
                        center.sourceUrl(),
                        center.sourceLevel(),
                        center.recommended(),
                        center.enabled(),
                        center.isActive(),
                        center.seedKey(),
                        center.dataSource()
                );
            }
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=psy-centers.csv")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(writer.toString());
    }

    @PostMapping(value = "/psy-centers/import", consumes = MediaType.TEXT_PLAIN_VALUE)
    public Map<String, Object> importPsyCenters(@RequestBody String csvContent) throws IOException {
        int success = 0;
        try (CSVParser parser = CSVParser.parse(
                new StringReader(csvContent),
                CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setIgnoreEmptyLines(true).build()
        )) {
            for (CSVRecord record : parser) {
                if (record == null || record.size() == 0) {
                    continue;
                }
                psyCenterService.adminCreate(
                        record.get("name"),
                        record.get("cityCode"),
                        record.get("cityName"),
                        emptyToNull(record.get("district")),
                        record.get("address"),
                        emptyToNull(record.get("phone")),
                        parseDecimal(record.get("latitude")),
                        parseDecimal(record.get("longitude")),
                        emptyToNull(record.get("sourceName")),
                        emptyToNull(record.get("sourceUrl")),
                        emptyToNull(record.get("sourceLevel")),
                        parseBoolean(record.get("recommended"), false),
                        parseBoolean(record.get("enabled"), true),
                        parseBoolean(record.get("isActive"), true)
                );
                success++;
            }
        }
        return Map.of("imported", success);
    }

    @GetMapping("/dashboard/light")
    public Map<String, Object> lightDashboard() {
        return cmsService.lightDashboard();
    }

    @PostMapping("/content-events/click")
    public void saveClick(@RequestBody ClickEventRequest request) {
        cmsService.recordClick(request.contentType(), request.contentId());
    }

    private static BigDecimal parseDecimal(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return new BigDecimal(value.trim());
    }

    private static boolean parseBoolean(String value, boolean defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return switch (value.trim().toLowerCase()) {
            case "1", "true", "yes", "y" -> true;
            case "0", "false", "no", "n" -> false;
            default -> defaultValue;
        };
    }

    private static String emptyToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record BannerRequest(
            @NotBlank String title,
            @NotBlank String imageUrl,
            String linkUrl,
            Integer sortOrder,
            boolean recommended,
            boolean enabled,
            LocalDateTime startsAt,
            LocalDateTime endsAt
    ) {}

    public record QuoteRequest(
            @NotBlank String content,
            String author,
            Integer sortOrder,
            boolean recommended,
            boolean enabled,
            Boolean isActive
    ) {
        public boolean isActiveOrDefault() {
            return isActive == null || isActive;
        }
    }

    public record ArticleRequest(
            @NotBlank String title,
            String coverImageUrl,
            String summary,
            String recommendReason,
            String fitFor,
            String highlights,
            Integer readingMinutes,
            String category,
            String sourceName,
            String sourceUrl,
            String contentUrl,
            Boolean isExternal,
            String difficultyTag,
            Integer sortOrder,
            boolean recommended,
            boolean enabled,
            Boolean isActive,
            LocalDateTime publishedAt
    ) {
        public String sourceUrlOrFallback() {
            if (sourceUrl != null && !sourceUrl.isBlank()) {
                return sourceUrl.trim();
            }
            if (contentUrl != null && !contentUrl.isBlank()) {
                return contentUrl.trim();
            }
            return null;
        }

        public boolean isExternalOrDefault() {
            return isExternal == null || isExternal;
        }

        public boolean isActiveOrDefault() {
            return isActive == null || isActive;
        }
    }

    public record BookRequest(
            @NotBlank String title,
            String author,
            String coverImageUrl,
            String description,
            String category,
            String recommendReason,
            String fitFor,
            String highlights,
            String purchaseUrl,
            Integer sortOrder,
            boolean recommended,
            boolean enabled,
            Boolean isActive
    ) {
        public boolean isActiveOrDefault() {
            return isActive == null || isActive;
        }
    }

    public record PsyCenterRequest(
            @NotBlank String name,
            @NotBlank String cityCode,
            @NotBlank String cityName,
            String district,
            @NotBlank String address,
            String phone,
            BigDecimal latitude,
            BigDecimal longitude,
            String sourceName,
            String sourceUrl,
            String sourceLevel,
            boolean recommended,
            boolean enabled,
            Boolean isActive
    ) {
        public boolean isActiveOrDefault() {
            return isActive == null || isActive;
        }
    }

    public record ScheduleItemRequest(
            @NotBlank String contentType,
            Long contentId,
            String slotRole,
            Integer sortOrder
    ) {}

    public record ScheduleRequest(
            LocalDate scheduleDate,
            @NotBlank String themeKey,
            @NotBlank String themeTitle,
            String themeSubtitle,
            Long quoteId,
            String status,
            List<ScheduleItemRequest> items
    ) {
        public List<ScheduleItemRequest> items() {
            return items == null ? List.of() : items;
        }
    }

    public record ClickEventRequest(@NotBlank String contentType, Long contentId) {}
}
