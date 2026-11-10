package com.cidas.edudoc.coursework.application;

import com.cidas.edudoc.common.api.CidasBusinessException;
import com.cidas.edudoc.common.api.ErrorCode;
import com.cidas.edudoc.coursework.domain.CourseworkRules.RubricItem;
import com.cidas.edudoc.coursework.domain.CourseworkRules.ScoreItem;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * 评分标准与分项得分在 JSON 列（{@code rubric_json} / {@code score_json}）与领域对象之间的往返。
 *
 * <p>06 计划「速度优先」口径：不建 rubric 明细表，代价是分项不能 SQL 聚合，
 * 一切按项统计都在本类解析后于内存完成。教师端与学生端共用本类，避免各写一套解析。
 */
public final class CourseworkJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private CourseworkJson() {
    }

    public record RubricWire(String name, Integer maxScore) {
    }

    public record ScoreWire(String name, Integer maxScore, Integer score) {
    }

    /** 解析作业评分标准；空或非法 JSON 视为未配置（返回空列表，由业务规则决定是否报错）。 */
    public static List<RubricItem> readRubric(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            RubricWire[] wires = MAPPER.readValue(json, RubricWire[].class);
            return java.util.Arrays.stream(wires)
                    .map(w -> new RubricItem(w.name(), w.maxScore() == null ? 0 : w.maxScore()))
                    .toList();
        } catch (JsonProcessingException e) {
            throw new CidasBusinessException(ErrorCode.INTERNAL_ERROR, "评分标准数据损坏，请重新发布作业");
        }
    }

    /** 教师提交的评分项 → 领域入参。 */
    public static List<ScoreItem> toScoreItems(List<ScoreWire> wires) {
        if (wires == null) {
            return List.of();
        }
        return wires.stream()
                .map(w -> new ScoreItem(w.name(), w.maxScore() == null ? 0 : w.maxScore(),
                        w.score() == null ? 0 : w.score()))
                .toList();
    }

    /** 领域评分项列表 → 存库 JSON（顺序与评分标准一致，见 {@code CourseworkRules#grade}）。 */
    public static String writeScores(List<ScoreItem> items) {
        try {
            return MAPPER.writeValueAsString(items);
        } catch (JsonProcessingException e) {
            throw new CidasBusinessException(ErrorCode.INTERNAL_ERROR, "评分结果序列化失败");
        }
    }

    /** 标准 JSON → 响应给前端的数组字符串（原样回传，避免再包一层 DTO）。 */
    public static List<RubricItem> rubricOrDefault(String json, List<RubricItem> fallback) {
        List<RubricItem> parsed = readRubric(json);
        return parsed.isEmpty() ? fallback : parsed;
    }
}
