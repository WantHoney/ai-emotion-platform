package com.wuhao.aiemotion.controller;

import com.wuhao.aiemotion.domain.PsyCenter;
import com.wuhao.aiemotion.service.CmsService;
import com.wuhao.aiemotion.service.PsyCenterService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class CmsAdminController {

    private final CmsService cmsService;
    private final PsyCenterService psyCenterService;

    public CmsAdminController(CmsService cmsService, PsyCenterService psyCenterService) {
        this.cmsService = cmsService;
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
        cmsService.createQuote(request.content(), request.author(), request.sortOrder(), request.recommended(), request.enabled());
    }

    @PutMapping("/quotes/{id}")
    public void updateQuote(@PathVariable long id, @RequestBody QuoteRequest request) {
        cmsService.updateQuote(id, request.content(), request.author(), request.sortOrder(), request.recommended(), request.enabled());
    }

    @DeleteMapping("/quotes/{id}")
    public void deleteQuote(@PathVariable long id) { cmsService.deleteQuote(id); }

    @GetMapping("/articles")
    public List<Map<String, Object>> articles() { return cmsService.listArticles(); }

    @PostMapping("/articles")
    public void createArticle(@RequestBody ArticleRequest request) {
        cmsService.createArticle(request.title(), request.coverImageUrl(), request.summary(), request.contentUrl(), request.sortOrder(),
                request.recommended(), request.enabled(), request.publishedAt());
    }

    @PutMapping("/articles/{id}")
    public void updateArticle(@PathVariable long id, @RequestBody ArticleRequest request) {
        cmsService.updateArticle(id, request.title(), request.coverImageUrl(), request.summary(), request.contentUrl(), request.sortOrder(),
                request.recommended(), request.enabled(), request.publishedAt());
    }

    @DeleteMapping("/articles/{id}")
    public void deleteArticle(@PathVariable long id) { cmsService.deleteArticle(id); }

    @GetMapping("/books")
    public List<Map<String, Object>> books() { return cmsService.listBooks(); }

    @PostMapping("/books")
    public void createBook(@RequestBody BookRequest request) {
        cmsService.createBook(request.title(), request.author(), request.coverImageUrl(), request.description(), request.purchaseUrl(),
                request.sortOrder(), request.recommended(), request.enabled());
    }

    @PutMapping("/books/{id}")
    public void updateBook(@PathVariable long id, @RequestBody BookRequest request) {
        cmsService.updateBook(id, request.title(), request.author(), request.coverImageUrl(), request.description(), request.purchaseUrl(),
                request.sortOrder(), request.recommended(), request.enabled());
    }

    @DeleteMapping("/books/{id}")
    public void deleteBook(@PathVariable long id) { cmsService.deleteBook(id); }

    @GetMapping("/psy-centers")
    public List<PsyCenter> psyCenters() { return psyCenterService.adminList(); }

    @PostMapping("/psy-centers")
    public void createPsyCenter(@RequestBody PsyCenterRequest request) {
        psyCenterService.adminCreate(request.name(), request.cityCode(), request.cityName(), request.district(), request.address(),
                request.phone(), request.latitude(), request.longitude(), request.recommended(), request.enabled());
    }

    @PutMapping("/psy-centers/{id}")
    public void updatePsyCenter(@PathVariable long id, @RequestBody PsyCenterRequest request) {
        psyCenterService.adminUpdate(id, request.name(), request.cityCode(), request.cityName(), request.district(), request.address(),
                request.phone(), request.latitude(), request.longitude(), request.recommended(), request.enabled());
    }

    @DeleteMapping("/psy-centers/{id}")
    public void deletePsyCenter(@PathVariable long id) { psyCenterService.adminDelete(id); }

    @GetMapping("/psy-centers/export")
    public ResponseEntity<String> exportPsyCenters() {
        StringBuilder csv = new StringBuilder("name,cityCode,cityName,district,address,phone,latitude,longitude,recommended,enabled\n");
        for (PsyCenter center : psyCenterService.adminList()) {
            csv.append(center.name()).append(',').append(center.cityCode()).append(',').append(center.cityName()).append(',')
                    .append(center.district() == null ? "" : center.district()).append(',')
                    .append(center.address()).append(',').append(center.phone() == null ? "" : center.phone()).append(',')
                    .append(center.latitude() == null ? "" : center.latitude()).append(',')
                    .append(center.longitude() == null ? "" : center.longitude()).append(',')
                    .append(center.recommended()).append(',').append(center.enabled()).append('\n');
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=psy-centers.csv")
                .contentType(MediaType.TEXT_PLAIN)
                .body(csv.toString());
    }

    @PostMapping(value = "/psy-centers/import", consumes = MediaType.TEXT_PLAIN_VALUE)
    public Map<String, Object> importPsyCenters(@RequestBody String csvContent) {
        String[] lines = csvContent.split("\\r?\\n");
        int success = 0;
        for (int i = 1; i < lines.length; i++) {
            if (lines[i].isBlank()) {
                continue;
            }
            String[] columns = lines[i].split(",");
            if (columns.length < 10) {
                continue;
            }
            psyCenterService.adminCreate(
                    columns[0], columns[1], columns[2], columns[3], columns[4], columns[5],
                    parseDecimal(columns[6]), parseDecimal(columns[7]),
                    Boolean.parseBoolean(columns[8]), Boolean.parseBoolean(columns[9])
            );
            success++;
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

    public record QuoteRequest(@NotBlank String content, String author, Integer sortOrder, boolean recommended, boolean enabled) {}

    public record ArticleRequest(
            @NotBlank String title,
            String coverImageUrl,
            String summary,
            String contentUrl,
            Integer sortOrder,
            boolean recommended,
            boolean enabled,
            LocalDateTime publishedAt
    ) {}

    public record BookRequest(
            @NotBlank String title,
            String author,
            String coverImageUrl,
            String description,
            String purchaseUrl,
            Integer sortOrder,
            boolean recommended,
            boolean enabled
    ) {}

    public record PsyCenterRequest(
            @NotBlank String name,
            @NotBlank String cityCode,
            @NotBlank String cityName,
            String district,
            @NotBlank String address,
            String phone,
            BigDecimal latitude,
            BigDecimal longitude,
            boolean recommended,
            boolean enabled
    ) {}

    public record ClickEventRequest(@NotBlank String contentType, Long contentId) {}
}
