package com.wuhao.aiemotion.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuhao.aiemotion.integration.ai.local.OllamaNarrativeClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class TrendInsightGenerationService {

    private static final Logger log = LoggerFactory.getLogger(TrendInsightGenerationService.class);
    private static final int MAX_ERROR_LENGTH = 400;
    private static final String DEFAULT_NOTE = "该解读基于历史报告的聚合结果生成，仅用于辅助观察变化，不构成医疗判断。";

    private final AnalysisNarrativeProperties properties;
    private final OllamaNarrativeClient ollamaNarrativeClient;
    private final ObjectMapper objectMapper;

    public TrendInsightGenerationService(AnalysisNarrativeProperties properties,
                                         OllamaNarrativeClient ollamaNarrativeClient,
                                         ObjectMapper objectMapper) {
        this.properties = properties;
        this.ollamaNarrativeClient = ollamaNarrativeClient;
        this.objectMapper = objectMapper;
    }

    public TrendInsightPayload generate(long userId,
                                        int days,
                                        List<Map<String, Object>> trendRows,
                                        double mediumThreshold,
                                        double highThreshold) {
        String provider = properties.getProvider();
        String model = properties.getOllama().getModel();
        List<TrendPoint> points = normalizePoints(trendRows);

        if (points.isEmpty()) {
            return buildEmptyPayload(provider, model);
        }

        TrendSnapshot snapshot = TrendSnapshot.from(days, points, mediumThreshold, highThreshold);

        if (!properties.isEnabled() || !properties.getTrend().isEnabled()) {
            return buildFallbackPayload("disabled", provider, model, snapshot, null);
        }

        if (!"ollama".equalsIgnoreCase(provider)) {
            return buildFallbackPayload("fallback", provider, model, snapshot,
                    "unsupported trend insight provider: " + provider);
        }

        try {
            String promptPayload = buildPromptPayload(userId, snapshot);
            String responseJson = ollamaNarrativeClient.chat(buildSystemPrompt(), buildUserPrompt(promptPayload));
            LlmTrendInsightResponse llmResponse = objectMapper.readValue(responseJson, LlmTrendInsightResponse.class);

            String headline = cleanText(llmResponse.headline());
            String summary = cleanText(llmResponse.summary());
            List<String> highlights = normalizeHighlights(llmResponse.highlights());
            String note = cleanText(llmResponse.note());

            if (isBlank(headline) && isBlank(summary)) {
                throw new IllegalStateException("trend insight headline and summary are both empty");
            }

            TrendInsightPayload fallback = buildFallbackPayload("fallback", provider, model, snapshot, null);
            return new TrendInsightPayload(
                    "ready",
                    provider,
                    model,
                    isBlank(headline) ? fallback.headline() : headline,
                    isBlank(summary) ? fallback.summary() : summary,
                    highlights.isEmpty() ? fallback.highlights() : highlights,
                    isBlank(note) ? DEFAULT_NOTE : note,
                    null
            );
        } catch (Exception ex) {
            log.warn("trend insight generation fallback: userId={}, provider={}, model={}, reason={}",
                    userId,
                    provider,
                    model,
                    truncate(ex.getMessage()));
            return buildFallbackPayload("fallback", provider, model, snapshot, truncate(ex.getMessage()));
        }
    }

    private TrendInsightPayload buildEmptyPayload(String provider, String model) {
        return new TrendInsightPayload(
                "empty",
                provider,
                model,
                "暂无趋势解读",
                "当前时间窗口内还没有足够的历史报告，暂时无法形成趋势变化解读。",
                List.of("先完成更多分析任务，趋势页会逐步积累可对比的数据。"),
                DEFAULT_NOTE,
                null
        );
    }

    private TrendInsightPayload buildFallbackPayload(String status,
                                                     String provider,
                                                     String model,
                                                     TrendSnapshot snapshot,
                                                     String error) {
        return new TrendInsightPayload(
                status,
                provider,
                model,
                buildFallbackHeadline(snapshot),
                buildFallbackSummary(snapshot),
                buildFallbackHighlights(snapshot),
                DEFAULT_NOTE,
                error
        );
    }

    private String buildSystemPrompt() {
        return """
                你是一个情绪趋势解读助手。
                你只能根据输入的聚合统计结果生成趋势解读，不得虚构不存在的数据，也不得给出医疗诊断。
                请严格遵守以下规则：
                1. 只返回 RFC8259 JSON，不要输出 Markdown、标题或额外说明。
                2. 使用简体中文。
                3. 只基于输入中的时间趋势、风险均值和分布数据进行总结。
                4. 如果数据量较少，要明确说明观察结论有限，不要过度推断。
                5. headline 用 1 句话概括趋势状态，尽量简洁。
                6. summary 用 2 到 3 句话总结近一段时间的变化特点。
                7. highlights 提供 2 到 3 条简短观察点，每条一句话。
                8. note 保持审慎、支持性的提醒语气。
                输出 JSON 结构：
                {
                  "headline": "string",
                  "summary": "string",
                  "highlights": ["string"],
                  "note": "string"
                }
                """;
    }

    private String buildUserPrompt(String promptPayload) {
        return "请根据下面的趋势统计结果生成简洁、克制、可读性好的趋势解读。输入 JSON: " + promptPayload;
    }

    private String buildPromptPayload(long userId, TrendSnapshot snapshot) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("userId", userId);
        root.put("language", properties.getLanguage());
        root.put("windowDays", snapshot.windowDays());
        root.put("activeDays", snapshot.activeDays());
        root.put("totalReports", snapshot.totalReports());
        root.put("overallAverageRisk", round2(snapshot.overallAverageRisk()));
        root.put("highRiskDays", snapshot.highRiskDays());
        root.put("range", round2(snapshot.range()));
        root.put("trendDirection", snapshot.trendDirection());
        root.put("latest", toPointMap(snapshot.latestPoint()));
        root.put("first", toPointMap(snapshot.firstPoint()));
        root.put("peak", toPointMap(snapshot.peakPoint()));
        root.put("distribution", Map.of(
                "low", snapshot.lowCount(),
                "medium", snapshot.mediumCount(),
                "high", snapshot.highCount(),
                "dominantBand", snapshot.dominantBand()
        ));
        root.put("sparseData", snapshot.sparseData());
        root.put("recentPoints", snapshot.points().stream()
                .skip(Math.max(0, snapshot.points().size() - Math.max(1, properties.getTrend().getMaxPoints())))
                .map(this::toPointMap)
                .toList());
        try {
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to build trend insight prompt payload", e);
        }
    }

    private Map<String, Object> toPointMap(TrendPoint point) {
        if (point == null) {
            return Map.of();
        }
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("date", point.date());
        item.put("reportCount", point.reportCount());
        item.put("avgRiskScore", round2(point.avgRiskScore()));
        item.put("lowCount", point.lowCount());
        item.put("mediumCount", point.mediumCount());
        item.put("highCount", point.highCount());
        return item;
    }

    private List<TrendPoint> normalizePoints(List<Map<String, Object>> trendRows) {
        if (trendRows == null || trendRows.isEmpty()) {
            return List.of();
        }
        List<TrendPoint> points = new ArrayList<>();
        for (Map<String, Object> row : trendRows) {
            LocalDate date = parseDate(row.get("date"));
            if (date == null) {
                continue;
            }
            points.add(new TrendPoint(
                    date,
                    toLong(row.get("reportCount")),
                    toDouble(row.get("avgRiskScore")),
                    toLong(row.get("lowCount")),
                    toLong(row.get("mediumCount")),
                    toLong(row.get("highCount"))
            ));
        }
        points.sort(Comparator.comparing(TrendPoint::date));
        return points;
    }

    private String buildFallbackHeadline(TrendSnapshot snapshot) {
        if (snapshot.sparseData()) {
            return "近期记录仍较少";
        }
        if (snapshot.deltaFromFirstToLatest() >= 8) {
            return "近期风险有抬升";
        }
        if (snapshot.deltaFromFirstToLatest() <= -8) {
            return "近期风险在回落";
        }
        if (snapshot.peakPoint() != null && snapshot.peakPoint().avgRiskScore() >= snapshot.highThreshold()) {
            return "近期出现风险峰值";
        }
        if (snapshot.range() <= 10) {
            return "近期整体较平稳";
        }
        return "近期存在一定波动";
    }

    private String buildFallbackSummary(TrendSnapshot snapshot) {
        if (snapshot.sparseData()) {
            return String.format(Locale.ROOT,
                    "近 %d 天共记录 %d 份报告，当前可用于观察的趋势数据仍然较少，整体参考性有限。现有记录的平均风险分为 %.2f，建议继续积累数据后再结合变化进行判断。",
                    snapshot.windowDays(),
                    snapshot.totalReports(),
                    round2(snapshot.overallAverageRisk()));
        }

        String direction = switch (snapshot.trendDirection()) {
            case "rising" -> "呈现上升";
            case "falling" -> "有所回落";
            default -> "基本持平";
        };
        return String.format(Locale.ROOT,
                "近 %d 天共记录 %d 份报告，活跃记录日为 %d 天，整体平均风险分为 %.2f，处于%s。最近一次平均风险分为 %.2f，较起点%s，整体%s。",
                snapshot.windowDays(),
                snapshot.totalReports(),
                snapshot.activeDays(),
                round2(snapshot.overallAverageRisk()),
                snapshot.averageBandLabel(),
                round2(snapshot.latestPoint().avgRiskScore()),
                formatDelta(snapshot.deltaFromFirstToLatest()),
                resolveRangeLabel(snapshot.range(), direction));
    }

    private List<String> buildFallbackHighlights(TrendSnapshot snapshot) {
        LinkedHashSet<String> items = new LinkedHashSet<>();
        items.add(String.format(Locale.ROOT,
                "统计窗口内共记录 %d 份报告，覆盖 %d 个有记录日期。",
                snapshot.totalReports(),
                snapshot.activeDays()));

        items.add(String.format(Locale.ROOT,
                "最近一次平均风险分为 %.2f，区间峰值出现在 %s（%.2f）。",
                round2(snapshot.latestPoint().avgRiskScore()),
                snapshot.peakPoint().date(),
                round2(snapshot.peakPoint().avgRiskScore())));

        if (snapshot.highRiskDays() > 0) {
            items.add(String.format(Locale.ROOT,
                    "窗口内共有 %d 天出现高风险记录，建议重点关注对应时段的触发因素。",
                    snapshot.highRiskDays()));
        } else {
            items.add("当前窗口内未出现高风险日，整体以低到中风险波动为主。");
        }

        return items.stream().limit(3).toList();
    }

    private String resolveRangeLabel(double range, String direction) {
        if (range <= 10) {
            return "波动较小且走势" + direction;
        }
        if (range <= 20) {
            return "存在一定波动但整体" + direction;
        }
        return "波动较为明显";
    }

    private String formatDelta(double delta) {
        if (Math.abs(delta) < 3) {
            return "与起点接近";
        }
        if (delta > 0) {
            return String.format(Locale.ROOT, "上升 %.2f 分", round2(delta));
        }
        return String.format(Locale.ROOT, "下降 %.2f 分", round2(Math.abs(delta)));
    }

    private List<String> normalizeHighlights(List<String> highlights) {
        if (highlights == null || highlights.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> items = new LinkedHashSet<>();
        for (String item : highlights) {
            String cleaned = cleanText(item);
            if (!isBlank(cleaned)) {
                items.add(cleaned);
            }
        }
        return items.stream().limit(3).toList();
    }

    private String cleanText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.replace("\r", " ").replace("\n", " ").trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String truncate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.length() <= MAX_ERROR_LENGTH ? value : value.substring(0, MAX_ERROR_LENGTH);
    }

    private LocalDate parseDate(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(String.valueOf(value));
        } catch (Exception ex) {
            return null;
        }
    }

    private long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    private double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return 0D;
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return 0D;
        }
    }

    private double round2(double value) {
        return Math.round(value * 100D) / 100D;
    }

    private record LlmTrendInsightResponse(
            String headline,
            String summary,
            List<String> highlights,
            String note
    ) {
    }

    private record TrendPoint(
            LocalDate date,
            long reportCount,
            double avgRiskScore,
            long lowCount,
            long mediumCount,
            long highCount
    ) {
    }

    private record TrendSnapshot(
            int windowDays,
            List<TrendPoint> points,
            int activeDays,
            long totalReports,
            double overallAverageRisk,
            long lowCount,
            long mediumCount,
            long highCount,
            int highRiskDays,
            double range,
            double deltaFromFirstToLatest,
            String trendDirection,
            String dominantBand,
            boolean sparseData,
            double mediumThreshold,
            double highThreshold,
            TrendPoint firstPoint,
            TrendPoint latestPoint,
            TrendPoint peakPoint
    ) {
        private static TrendSnapshot from(int windowDays,
                                          List<TrendPoint> points,
                                          double mediumThreshold,
                                          double highThreshold) {
            TrendPoint firstPoint = points.get(0);
            TrendPoint latestPoint = points.get(points.size() - 1);
            TrendPoint peakPoint = points.stream()
                    .max(Comparator.comparingDouble(TrendPoint::avgRiskScore))
                    .orElse(latestPoint);

            long totalReports = points.stream().mapToLong(TrendPoint::reportCount).sum();
            double weightedRiskSum = points.stream().mapToDouble(point -> point.avgRiskScore() * point.reportCount()).sum();
            double overallAverageRisk = totalReports <= 0 ? 0D : weightedRiskSum / totalReports;

            long lowCount = points.stream().mapToLong(TrendPoint::lowCount).sum();
            long mediumCount = points.stream().mapToLong(TrendPoint::mediumCount).sum();
            long highCount = points.stream().mapToLong(TrendPoint::highCount).sum();
            int highRiskDays = (int) points.stream().filter(point -> point.highCount() > 0).count();

            double minRisk = points.stream().mapToDouble(TrendPoint::avgRiskScore).min().orElse(0D);
            double maxRisk = points.stream().mapToDouble(TrendPoint::avgRiskScore).max().orElse(0D);
            double delta = latestPoint.avgRiskScore() - firstPoint.avgRiskScore();
            String trendDirection = Math.abs(delta) < 5 ? "stable" : (delta > 0 ? "rising" : "falling");

            String dominantBand = "low";
            long dominantValue = lowCount;
            if (mediumCount > dominantValue) {
                dominantBand = "medium";
                dominantValue = mediumCount;
            }
            if (highCount > dominantValue) {
                dominantBand = "high";
            }

            boolean sparseData = totalReports < 3 || points.size() < 2;

            return new TrendSnapshot(
                    windowDays,
                    points,
                    points.size(),
                    totalReports,
                    overallAverageRisk,
                    lowCount,
                    mediumCount,
                    highCount,
                    highRiskDays,
                    maxRisk - minRisk,
                    delta,
                    trendDirection,
                    dominantBand,
                    sparseData,
                    mediumThreshold,
                    highThreshold,
                    firstPoint,
                    latestPoint,
                    peakPoint
            );
        }

        private String averageBandLabel() {
            if (overallAverageRisk >= highThreshold) {
                return "高风险区间";
            }
            if (overallAverageRisk >= mediumThreshold) {
                return "中风险区间";
            }
            return "低风险区间";
        }
    }
}
