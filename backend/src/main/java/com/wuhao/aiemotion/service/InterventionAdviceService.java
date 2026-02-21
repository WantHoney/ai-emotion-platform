package com.wuhao.aiemotion.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

@Service
public class InterventionAdviceService {

    private static final double HIGH_TEXT_NEG = 0.70D;
    private static final double HIGH_ANGRY = 0.35D;
    private static final double HIGH_SAD = 0.45D;
    private static final double HIGH_VAR_CONF = 0.10D;

    public String buildAdvice(String riskLevel,
                              double riskScore,
                              double pSad,
                              double pAngry,
                              double varConf,
                              double textNeg) {
        String normalizedLevel = normalizeRiskLevel(riskLevel);

        List<String> instant = new ArrayList<>();
        List<String> shortTerm = new ArrayList<>();
        List<String> resource = new ArrayList<>();

        switch (normalizedLevel) {
            case "HIGH" -> {
                instant.add("先暂停当前高压任务，做 2-3 分钟呼吸放松或地面化练习。");
                instant.add("今天优先减少冲突性沟通，避免在情绪峰值时做重要决定。");
                shortTerm.add("48 小时内安排一次与可信赖同伴的深度沟通，记录触发事件与身体反应。");
                shortTerm.add("未来 1 周固定睡眠与起床时间，限制夜间连续用屏时长。");
                resource.add("若连续出现明显痛苦或功能受损，请尽快联系学校/社区心理咨询资源。");
            }
            case "ATTENTION" -> {
                instant.add("先进行 5 分钟节律呼吸，降低当前生理唤醒水平。");
                instant.add("将当下压力源拆分为可执行小任务，按优先级逐一处理。");
                shortTerm.add("未来 3 天保持规律作息，并每天记录一次情绪波动触发点。");
                shortTerm.add("每周安排 3 次中等强度运动，帮助缓冲情绪负荷。");
                resource.add("若波动持续存在，可预约心理咨询做进一步评估与支持。");
            }
            default -> {
                instant.add("保持当前节奏，继续进行日常放松与自我觉察。");
                shortTerm.add("维持规律睡眠、运动与社交连接，降低后续波动风险。");
                resource.add("建议每周进行一次情绪自检，持续跟踪趋势变化。");
            }
        }

        if (pSad >= HIGH_SAD) {
            instant.add("悲伤成分较高，建议优先增加现实支持连接，避免长时间独处反刍。");
        }
        if (pAngry >= HIGH_ANGRY) {
            instant.add("愤怒成分较高，建议先离开冲突场景，使用延迟回应策略。");
        }
        if (varConf >= HIGH_VAR_CONF) {
            shortTerm.add("情绪波动较大，建议固定作息并减少咖啡因/熬夜等波动放大因素。");
        }
        if (textNeg >= HIGH_TEXT_NEG) {
            resource.add("文本负向强度较高，建议尽早与专业支持渠道建立联系。");
        }

        if ("HIGH".equals(normalizedLevel) || riskScore >= 80.0D) {
            resource.add("如出现持续失眠、明显绝望或自伤相关想法，请立即联系当地紧急援助与心理热线。");
        }

        List<String> merged = new ArrayList<>();
        merged.addAll(uniqueOrdered(instant, 2));
        merged.addAll(uniqueOrdered(shortTerm, 2));
        merged.addAll(uniqueOrdered(resource, 3));
        merged.add("以上结果仅用于情绪风险提示，不构成医疗诊断。");

        return sanitizeText(String.join("；", merged));
    }

    private String normalizeRiskLevel(String riskLevel) {
        if (riskLevel == null) {
            return "NORMAL";
        }
        String normalized = riskLevel.trim().toUpperCase(Locale.ROOT);
        if ("HIGH".equals(normalized)) {
            return "HIGH";
        }
        if ("ATTENTION".equals(normalized) || "MEDIUM".equals(normalized)) {
            return "ATTENTION";
        }
        return "NORMAL";
    }

    private List<String> uniqueOrdered(List<String> source, int limit) {
        if (source == null || source.isEmpty() || limit <= 0) {
            return List.of();
        }
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String item : source) {
            if (item == null) {
                continue;
            }
            String text = item.trim();
            if (!text.isEmpty()) {
                set.add(text);
            }
            if (set.size() >= limit) {
                break;
            }
        }
        return new ArrayList<>(set);
    }

    private String sanitizeText(String text) {
        if (text == null || text.isBlank()) {
            return "建议保持规律作息与情绪记录；以上结果仅用于情绪风险提示，不构成医疗诊断。";
        }
        String sanitized = text
                .replace("确诊", "评估")
                .replace("诊断为", "评估为")
                .replace("患有", "存在")
                .replace("治愈", "改善")
                .replace('\n', ' ')
                .replace('\r', ' ')
                .trim();
        return sanitized;
    }
}

