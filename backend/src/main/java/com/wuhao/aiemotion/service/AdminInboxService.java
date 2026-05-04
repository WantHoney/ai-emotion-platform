package com.wuhao.aiemotion.service;

import com.wuhao.aiemotion.domain.DailySchedule;
import com.wuhao.aiemotion.domain.PsyCenter;
import com.wuhao.aiemotion.repository.ContentHubRepository;
import com.wuhao.aiemotion.repository.ModelGovernanceRepository;
import com.wuhao.aiemotion.repository.PsyCenterRepository;
import com.wuhao.aiemotion.repository.WarningGovernanceRepository;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class AdminInboxService {

    private final WarningGovernanceRepository warningGovernanceRepository;
    private final ModelGovernanceRepository modelGovernanceRepository;
    private final ContentHubRepository contentHubRepository;
    private final PsyCenterRepository psyCenterRepository;
    private final SystemStatusService systemStatusService;

    public AdminInboxService(WarningGovernanceRepository warningGovernanceRepository,
                             ModelGovernanceRepository modelGovernanceRepository,
                             ContentHubRepository contentHubRepository,
                             PsyCenterRepository psyCenterRepository,
                             SystemStatusService systemStatusService) {
        this.warningGovernanceRepository = warningGovernanceRepository;
        this.modelGovernanceRepository = modelGovernanceRepository;
        this.contentHubRepository = contentHubRepository;
        this.psyCenterRepository = psyCenterRepository;
        this.systemStatusService = systemStatusService;
    }

    public Map<String, Object> listInbox(Integer limit) {
        int safeLimit = Math.max(12, Math.min(limit == null ? 36 : limit, 80));
        List<Map<String, Object>> items = new ArrayList<>();
        items.addAll(buildWarningItems(Math.min(12, safeLimit)));
        items.addAll(buildSystemItems());
        items.addAll(buildModelSwitchItems(Math.min(8, safeLimit)));
        items.addAll(buildScheduleItems(Math.min(8, safeLimit)));
        items.addAll(buildPsyCenterItems(Math.min(8, safeLimit)));

        List<Map<String, Object>> sortedItems = items.stream()
                .sorted(this::compareItems)
                .limit(safeLimit)
                .toList();

        Map<String, Long> categoryCounts = new LinkedHashMap<>();
        for (String category : List.of("warning", "system", "model", "schedule", "psy_center")) {
            long count = sortedItems.stream()
                    .filter(item -> category.equals(asString(item.get("category"))))
                    .count();
            if (count > 0) {
                categoryCounts.put(category, count);
            }
        }

        long highPriority = sortedItems.stream()
                .filter(item -> isHighPriority(asString(item.get("level"))))
                .count();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", sortedItems.size());
        summary.put("highPriority", highPriority);
        summary.put("categoryCounts", categoryCounts);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("generatedAt", LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS));
        payload.put("summary", summary);
        payload.put("items", sortedItems);
        return payload;
    }

    private List<Map<String, Object>> buildWarningItems(int limit) {
        warningGovernanceRepository.markOverdueWarningsBreached();
        return warningGovernanceRepository.listWarnings(0, Math.max(limit, 8), null, null, null, null).stream()
                .limit(limit)
                .map(this::toWarningInboxItem)
                .filter(Objects::nonNull)
                .toList();
    }

    private Map<String, Object> toWarningInboxItem(Map<String, Object> row) {
        Long warningId = toLong(row.get("id"));
        if (warningId == null) {
            return null;
        }
        String status = normalizeCode(asString(row.get("status"), "NEW"));
        String riskLevel = normalizeCode(asString(row.get("risk_level"), "MEDIUM"));
        boolean breached = truthy(row.get("breached"));
        LocalDateTime occurredAt = firstNonNull(
                toLocalDateTime(row.get("resolved_at")),
                toLocalDateTime(row.get("first_followed_at")),
                toLocalDateTime(row.get("first_acked_at")),
                toLocalDateTime(row.get("created_at"))
        );

        String title;
        if (breached) {
            title = formatRiskLevel(riskLevel) + "预警已超时";
        } else if ("NEW".equals(status)) {
            title = formatRiskLevel(riskLevel) + "预警待处理";
        } else if ("ACKED".equals(status) || "FOLLOWING".equals(status)) {
            title = formatRiskLevel(riskLevel) + "预警处理中";
        } else {
            title = formatRiskLevel(riskLevel) + "预警已更新";
        }

        String detail = joinNonBlank(
                asString(row.get("user_mask")) == null ? null : "用户 " + asString(row.get("user_mask")),
                asString(row.get("top_emotion")) == null ? null : "情绪 " + formatEmotion(asString(row.get("top_emotion"))),
                row.get("risk_score") == null ? null : "风险分 " + formatScore(row.get("risk_score")),
                "状态 " + formatWarningStatus(status)
        );

        String level = breached || ("HIGH".equals(riskLevel) && "NEW".equals(status))
                ? "critical"
                : ("RESOLVED".equals(status) || "CLOSED".equals(status) ? "success" : "warning");

        return inboxItem(
                warningMessageId(row, warningId, status, breached),
                "warning",
                level,
                title,
                detail,
                occurredAt,
                "/admin/warnings",
                "前往预警处置台",
                "WARNING_EVENT",
                warningId
        );
    }

    private List<Map<String, Object>> buildModelSwitchItems(int limit) {
        return modelGovernanceRepository.listSwitchLogs(null, null, Math.max(limit, 6)).stream()
                .limit(limit)
                .map(row -> {
                    Long logId = toLong(row.get("id"));
                    if (logId == null) {
                        return null;
                    }
                    String modelType = normalizeCode(asString(row.get("model_type"), "MODEL"));
                    String env = normalizeCode(asString(row.get("env"), "prod")).toLowerCase(Locale.ROOT);
                    String detail = joinNonBlank(
                            "环境 " + formatEnv(env),
                            toLong(row.get("to_model_id")) == null ? null : "目标模型 #" + toLong(row.get("to_model_id")),
                            asString(row.get("switch_reason")) == null ? null : "原因：" + asString(row.get("switch_reason"))
                    );
                    return inboxItem(
                            "model-switch-" + logId,
                            "model",
                            "success",
                            formatModelType(modelType) + "模型已切换",
                            detail,
                            toLocalDateTime(row.get("switched_at")),
                            "/admin/models",
                            "查看模型治理",
                            "MODEL_SWITCH_LOG",
                            logId
                    );
                })
                .filter(Objects::nonNull)
                .toList();
    }

    private List<Map<String, Object>> buildSystemItems() {
        Map<String, Object> status = systemStatusService.status();
        Map<String, Object> db = asMap(status.get("db"));
        Map<String, Object> ser = asMap(status.get("ser"));
        Map<String, Object> metrics = asMap(status.get("metrics"));
        LocalDateTime occurredAt = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);

        List<Map<String, Object>> items = new ArrayList<>();
        appendSystemServiceItem(items, "db", "数据库连接异常", db, occurredAt);
        appendSystemServiceItem(items, "ser", "语音情绪服务异常", ser, occurredAt);

        long failedTasks24h = toLong(metrics.get("failedTasks24h")) == null ? 0L : toLong(metrics.get("failedTasks24h"));
        if (failedTasks24h > 0) {
            items.add(inboxItem(
                    "system-failed-tasks-" + failedTasks24h,
                    "system",
                    failedTasks24h >= 5 ? "critical" : "warning",
                    "近 24 小时存在失败任务",
                    "失败任务 " + failedTasks24h + " 个，建议复查分析链路和上游服务状态",
                    occurredAt,
                    "/admin/system",
                    "查看系统状态",
                    "SYSTEM_STATUS",
                    "failedTasks24h"
            ));
        }

        long queuedTasks = toLong(metrics.get("queuedTasks")) == null ? 0L : toLong(metrics.get("queuedTasks"));
        if (queuedTasks >= 10) {
            items.add(inboxItem(
                    "system-queued-tasks-" + queuedTasks,
                    "system",
                    queuedTasks >= 30 ? "warning" : "info",
                    "任务队列出现积压",
                    "当前排队任务 " + queuedTasks + " 个，可前往系统状态与模型治理页复查吞吐",
                    occurredAt,
                    "/admin/system",
                    "查看系统状态",
                    "SYSTEM_STATUS",
                    "queuedTasks"
            ));
        }

        return items;
    }

    private void appendSystemServiceItem(List<Map<String, Object>> items,
                                         String key,
                                         String title,
                                         Map<String, Object> service,
                                         LocalDateTime occurredAt) {
        String serviceStatus = normalizeCode(asString(service.get("status"), "UNKNOWN"));
        if ("UP".equals(serviceStatus)) {
            return;
        }
        String level = "DOWN".equals(serviceStatus) ? "critical" : "warning";
        String detail = joinNonBlank(
                asString(service.get("message")),
                toLong(service.get("latencyMs")) == null ? null : "探测耗时 " + toLong(service.get("latencyMs")) + " ms"
        );
        items.add(inboxItem(
                "system-" + key + "-" + serviceStatus,
                "system",
                level,
                title,
                detail,
                occurredAt,
                "/admin/system",
                "查看系统状态",
                "SYSTEM_STATUS",
                key
        ));
    }

    private List<Map<String, Object>> buildScheduleItems(int limit) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(21);
        return contentHubRepository.listSchedules(null).stream()
                .sorted(Comparator.comparing(this::scheduleOccurredAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .filter(schedule -> {
                    LocalDateTime occurredAt = scheduleOccurredAt(schedule);
                    return occurredAt != null && occurredAt.isAfter(cutoff);
                })
                .limit(limit)
                .map(schedule -> inboxItem(
                        "schedule-" + schedule.id() + "-" + timeSignature(scheduleOccurredAt(schedule)),
                        "schedule",
                        "DRAFT".equalsIgnoreCase(schedule.status()) ? "warning" : "info",
                        "每日排期已更新：" + schedule.scheduleDate(),
                        joinNonBlank(schedule.themeTitle(), schedule.themeSubtitle(), "状态 " + formatScheduleStatus(schedule.status())),
                        scheduleOccurredAt(schedule),
                        "/admin/content/schedules",
                        "查看每日排期",
                        "DAILY_SCHEDULE",
                        schedule.id()
                ))
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> buildPsyCenterItems(int limit) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(21);
        return psyCenterRepository.findAllForAdmin().stream()
                .sorted(Comparator.comparing(this::psyCenterOccurredAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .filter(center -> {
                    LocalDateTime occurredAt = psyCenterOccurredAt(center);
                    return occurredAt != null && occurredAt.isAfter(cutoff);
                })
                .limit(limit)
                .map(center -> {
                    String title;
                    if (Boolean.FALSE.equals(center.isActive())) {
                        title = "心理中心已下线";
                    } else if (isRecentlyCreated(center)) {
                        title = "心理中心已新增";
                    } else {
                        title = "心理中心信息已更新";
                    }
                    String detail = joinNonBlank(
                            center.name(),
                            joinLocation(center.cityName(), center.district()),
                            center.sourceName()
                    );
                    return inboxItem(
                            "psy-center-" + center.id() + "-" + timeSignature(psyCenterOccurredAt(center)),
                            "psy_center",
                            Boolean.FALSE.equals(center.isActive()) || Boolean.FALSE.equals(center.enabled()) ? "warning" : "info",
                            title,
                            detail,
                            psyCenterOccurredAt(center),
                            "/admin/psy-centers",
                            "查看心理中心管理",
                            "PSY_CENTER",
                            center.id()
                    );
                })
                .collect(Collectors.toList());
    }

    private Map<String, Object> inboxItem(String id,
                                          String category,
                                          String level,
                                          String title,
                                          String detail,
                                          LocalDateTime occurredAt,
                                          String route,
                                          String routeLabel,
                                          String sourceType,
                                          Object sourceId) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", id);
        item.put("category", category);
        item.put("level", level);
        item.put("title", title);
        item.put("detail", detail);
        item.put("occurredAt", occurredAt);
        item.put("route", route);
        item.put("routeLabel", routeLabel);
        item.put("sourceType", sourceType);
        item.put("sourceId", sourceId);
        return item;
    }

    private int compareItems(Map<String, Object> left, Map<String, Object> right) {
        int levelCompare = Integer.compare(levelRank(asString(left.get("level"))), levelRank(asString(right.get("level"))));
        if (levelCompare != 0) {
            return levelCompare;
        }
        LocalDateTime leftTime = toLocalDateTime(left.get("occurredAt"));
        LocalDateTime rightTime = toLocalDateTime(right.get("occurredAt"));
        if (leftTime == null && rightTime == null) {
            return String.valueOf(left.get("title")).compareToIgnoreCase(String.valueOf(right.get("title")));
        }
        if (leftTime == null) {
            return 1;
        }
        if (rightTime == null) {
            return -1;
        }
        return rightTime.compareTo(leftTime);
    }

    private int levelRank(String level) {
        return switch (normalizeCode(level)) {
            case "CRITICAL" -> 0;
            case "WARNING" -> 1;
            case "INFO" -> 2;
            case "SUCCESS" -> 3;
            default -> 4;
        };
    }

    private boolean isHighPriority(String level) {
        String normalized = normalizeCode(level);
        return "CRITICAL".equals(normalized) || "WARNING".equals(normalized);
    }

    private String warningMessageId(Map<String, Object> row, Long warningId, String status, boolean breached) {
        return String.join("-",
                "warning",
                String.valueOf(warningId),
                status,
                breached ? "breached" : "safe",
                timeSignature(toLocalDateTime(row.get("first_acked_at"))),
                timeSignature(toLocalDateTime(row.get("first_followed_at"))),
                timeSignature(toLocalDateTime(row.get("resolved_at")))
        );
    }

    private LocalDateTime scheduleOccurredAt(DailySchedule schedule) {
        return firstNonNull(schedule.updatedAt(), schedule.createdAt());
    }

    private LocalDateTime psyCenterOccurredAt(PsyCenter center) {
        return firstNonNull(center.updatedAt(), center.createdAt());
    }

    private boolean isRecentlyCreated(PsyCenter center) {
        if (center.createdAt() == null || center.updatedAt() == null) {
            return false;
        }
        return !center.updatedAt().isAfter(center.createdAt().plusMinutes(2));
    }

    private String joinLocation(String cityName, String district) {
        return joinNonBlank(cityName, district);
    }

    private String joinNonBlank(String... parts) {
        return java.util.Arrays.stream(parts)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(part -> !part.isBlank())
                .collect(Collectors.joining(" · "));
    }

    private String formatScore(Object value) {
        if (value instanceof Number number) {
            return String.format(Locale.ROOT, "%.2f", number.doubleValue());
        }
        return asString(value, "-");
    }

    private String formatRiskLevel(String value) {
        return switch (normalizeCode(value)) {
            case "LOW", "NORMAL" -> "低风险";
            case "MEDIUM", "ATTENTION" -> "中风险";
            case "HIGH" -> "高风险";
            default -> "风险";
        };
    }

    private String formatWarningStatus(String value) {
        return switch (normalizeCode(value)) {
            case "NEW" -> "新建";
            case "ACKED" -> "已确认";
            case "FOLLOWING" -> "跟进中";
            case "RESOLVED" -> "已结案";
            case "CLOSED" -> "已关闭";
            default -> "处理中";
        };
    }

    private String formatModelType(String value) {
        return switch (normalizeCode(value)) {
            case "ASR" -> "语音转写";
            case "AUDIO_EMOTION" -> "音频情绪";
            case "TEXT_SENTIMENT" -> "文本情绪";
            case "FUSION" -> "融合分析";
            case "SCORING" -> "综合评分";
            default -> "模型";
        };
    }

    private String formatEnv(String value) {
        return switch (normalizeCode(value)) {
            case "DEV" -> "开发环境";
            case "STAGING" -> "预发环境";
            case "PROD" -> "生产环境";
            default -> value == null || value.isBlank() ? "默认环境" : value;
        };
    }

    private String formatScheduleStatus(String value) {
        return switch (normalizeCode(value)) {
            case "ACTIVE" -> "启用";
            case "DRAFT" -> "草稿";
            default -> value == null || value.isBlank() ? "-" : value;
        };
    }

    private String formatEmotion(String value) {
        return switch (normalizeCode(value)) {
            case "HAPPY" -> "高兴";
            case "SAD" -> "悲伤";
            case "ANGRY" -> "愤怒";
            case "NEUTRAL" -> "平静";
            case "FEAR" -> "恐惧";
            case "DISGUST" -> "厌恶";
            case "SURPRISE" -> "惊讶";
            default -> value == null || value.isBlank() ? "-" : value;
        };
    }

    private String normalizeCode(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String asString(Object value) {
        return asString(value, null);
    }

    private String asString(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String normalized = String.valueOf(value).trim();
        return normalized.isBlank() ? fallback : normalized;
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignore) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private boolean truthy(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        if (value == null) {
            return false;
        }
        String normalized = String.valueOf(value).trim().toLowerCase(Locale.ROOT);
        return "1".equals(normalized) || "true".equals(normalized) || "y".equals(normalized) || "yes".equals(normalized);
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        if (value == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(String.valueOf(value));
        } catch (Exception ignore) {
            return null;
        }
    }

    private LocalDateTime firstNonNull(LocalDateTime... values) {
        for (LocalDateTime value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String timeSignature(LocalDateTime value) {
        return value == null ? "na" : value.truncatedTo(ChronoUnit.SECONDS).toString();
    }
}
