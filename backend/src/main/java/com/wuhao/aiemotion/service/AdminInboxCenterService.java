package com.wuhao.aiemotion.service;

import com.wuhao.aiemotion.repository.AdminInboxRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminInboxCenterService {

    private final AdminInboxService adminInboxService;
    private final AdminInboxRepository adminInboxRepository;

    public AdminInboxCenterService(AdminInboxService adminInboxService,
                                   AdminInboxRepository adminInboxRepository) {
        this.adminInboxService = adminInboxService;
        this.adminInboxRepository = adminInboxRepository;
    }

    @PostConstruct
    public void init() {
        adminInboxRepository.ensureTables();
    }

    public Map<String, Object> listInbox(long adminUserId,
                                         Integer limit,
                                         boolean archived,
                                         boolean pendingWarningsOnly) {
        int safeLimit = Math.max(12, Math.min(limit == null ? 36 : limit, 80));
        Map<String, Object> generated = adminInboxService.listInbox(Math.max(safeLimit, 48));
        AdminInboxRepository.InboxPreferenceRow preference = adminInboxRepository.getPreference(adminUserId);

        List<Map<String, Object>> generatedItems = asListOfMaps(generated.get("items"));
        Map<String, AdminInboxRepository.InboxStateRow> states = adminInboxRepository.findStates(
                adminUserId,
                generatedItems.stream().map(item -> asString(item.get("id"))).filter(id -> id != null && !id.isBlank()).toList()
        );

        List<Map<String, Object>> visibleItems = generatedItems.stream()
                .filter(item -> categoryEnabled(asString(item.get("category")), preference))
                .filter(item -> !pendingWarningsOnly || isPendingWarning(item))
                .map(item -> enrichItem(item, states.get(asString(item.get("id")))))
                .filter(item -> truthy(item.get("archived")) == archived)
                .limit(safeLimit)
                .toList();

        long highPriority = visibleItems.stream()
                .filter(item -> isHighPriority(asString(item.get("level"))))
                .count();
        long unread = visibleItems.stream()
                .filter(item -> !truthy(item.get("read")))
                .count();

        Map<String, Long> categoryCounts = new LinkedHashMap<>();
        for (String category : List.of("warning", "system", "model", "schedule", "psy_center")) {
            long count = visibleItems.stream()
                    .filter(item -> category.equals(asString(item.get("category"))))
                    .count();
            if (count > 0) {
                categoryCounts.put(category, count);
            }
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", visibleItems.size());
        summary.put("highPriority", highPriority);
        summary.put("unread", unread);
        summary.put("categoryCounts", categoryCounts);

        return Map.of(
                "generatedAt", generated.get("generatedAt"),
                "preferences", toPreferenceMap(preference),
                "summary", summary,
                "items", visibleItems
        );
    }

    public Map<String, Object> updatePreferences(long adminUserId,
                                                 Boolean warningEnabled,
                                                 Boolean systemEnabled,
                                                 Boolean modelEnabled,
                                                 Boolean scheduleEnabled,
                                                 Boolean psyCenterEnabled) {
        AdminInboxRepository.InboxPreferenceRow existing = adminInboxRepository.getPreference(adminUserId);
        AdminInboxRepository.InboxPreferenceRow updated = adminInboxRepository.updatePreference(
                adminUserId,
                warningEnabled == null ? existing.warningEnabled() : warningEnabled,
                systemEnabled == null ? existing.systemEnabled() : systemEnabled,
                modelEnabled == null ? existing.modelEnabled() : modelEnabled,
                scheduleEnabled == null ? existing.scheduleEnabled() : scheduleEnabled,
                psyCenterEnabled == null ? existing.psyCenterEnabled() : psyCenterEnabled
        );
        return toPreferenceMap(updated);
    }

    public void updateState(long adminUserId,
                            List<InboxStateInput> items,
                            Boolean read,
                            Boolean archived) {
        if (items == null || items.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "items are required");
        }
        Map<String, AdminInboxRepository.InboxStateRow> existing = adminInboxRepository.findStates(
                adminUserId,
                items.stream().map(InboxStateInput::messageId).toList()
        );
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        List<AdminInboxRepository.InboxStateMutation> mutations = new ArrayList<>();
        for (InboxStateInput item : items) {
            if (item.messageId() == null || item.messageId().isBlank()) {
                continue;
            }
            AdminInboxRepository.InboxStateRow state = existing.get(item.messageId());
            boolean nextRead = read == null ? state != null && state.read() : read;
            boolean nextArchived = archived == null ? state != null && state.archived() : archived;
            if (Boolean.TRUE.equals(archived) && read == null) {
                nextRead = true;
            }

            LocalDateTime readAt = nextRead
                    ? (state != null && state.readAt() != null && (read == null || state.read()) ? state.readAt() : now)
                    : null;
            LocalDateTime archivedAt = nextArchived
                    ? (state != null && state.archivedAt() != null && (archived == null || state.archived()) ? state.archivedAt() : now)
                    : null;

            mutations.add(new AdminInboxRepository.InboxStateMutation(
                    item.messageId(),
                    normalizeBlank(item.sourceType()),
                    normalizeBlank(item.sourceId()),
                    nextRead,
                    readAt,
                    nextArchived,
                    archivedAt
            ));
        }
        adminInboxRepository.upsertStates(adminUserId, mutations);
    }

    private Map<String, Object> enrichItem(Map<String, Object> item, AdminInboxRepository.InboxStateRow state) {
        Map<String, Object> next = new LinkedHashMap<>(item);
        next.put("read", state != null && state.read());
        next.put("archived", state != null && state.archived());
        return next;
    }

    private boolean categoryEnabled(String category, AdminInboxRepository.InboxPreferenceRow preference) {
        return switch (category == null ? "" : category) {
            case "warning" -> preference.warningEnabled();
            case "system" -> preference.systemEnabled();
            case "model" -> preference.modelEnabled();
            case "schedule" -> preference.scheduleEnabled();
            case "psy_center" -> preference.psyCenterEnabled();
            default -> true;
        };
    }

    private boolean isPendingWarning(Map<String, Object> item) {
        return "warning".equals(asString(item.get("category"))) && !"success".equals(asString(item.get("level")));
    }

    private boolean isHighPriority(String level) {
        return "critical".equalsIgnoreCase(level) || "warning".equalsIgnoreCase(level);
    }

    private Map<String, Object> toPreferenceMap(AdminInboxRepository.InboxPreferenceRow preference) {
        Map<String, Boolean> categories = new LinkedHashMap<>();
        categories.put("warning", preference.warningEnabled());
        categories.put("system", preference.systemEnabled());
        categories.put("model", preference.modelEnabled());
        categories.put("schedule", preference.scheduleEnabled());
        categories.put("psy_center", preference.psyCenterEnabled());
        return Map.of("categories", categories);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> asListOfMaps(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                rows.add(new LinkedHashMap<>((Map<String, Object>) map));
            }
        }
        return rows;
    }

    private boolean truthy(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        return value != null && "true".equalsIgnoreCase(String.valueOf(value));
    }

    private String asString(Object value) {
        if (value == null) {
            return null;
        }
        String normalized = String.valueOf(value).trim();
        return normalized.isBlank() ? null : normalized;
    }

    private String normalizeBlank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record InboxStateInput(String messageId, String sourceType, String sourceId) {
    }
}
