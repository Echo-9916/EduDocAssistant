package com.cidas.edudoc.ai.application;

import java.util.List;

/**
 * 模型调用网关端口（概要 §7 I04）：embed/summarize/extractKeypoints/answer/polish/rerank。
 * 实现只在 ai.infrastructure（HTTP 客户端红线）；失败以 {@code CidasBusinessException}
 * 表达（50201 模型服务失败 / 50401 超时 / 50301 服务间凭据缺失）。
 */
public interface AiGateway {

    /** 批量向量化（档内默认超时）；返回向量与入参等长（顺序一致）。 */
    EmbedOutcome embed(List<String> texts);

    /** 带超时上限的向量化（QA 端到端预算传递，1105 审查 P1-3）。 */
    EmbedOutcome embed(List<String> texts, long timeoutMs);

    SummarizeOutcome summarize(List<SourceChunk> chunks);

    /** 摘要 reduce 段：对 map 段再做一次汇总结论（1105 审查 P3-2 真 map-reduce）。 */
    SummarizeOutcome summarizeReduce(List<SourceChunk> segments);

    KeypointOutcome extractKeypoints(List<SourceChunk> chunks);

    /** evidence-only 问答（档内默认超时）；refused=true 表示模型按证据判定无法作答。 */
    AnswerOutcome answer(String question, List<SourceChunk> evidence, List<HistoryMessage> history);

    /** 带超时上限的问答生成（QA 端到端预算传递）。 */
    AnswerOutcome answer(String question, List<SourceChunk> evidence, List<HistoryMessage> history,
                         long timeoutMs);

    PolishOutcome polish(List<String> paragraphs);

    /** 相关性重排（档内默认超时）；返回按分数降序的原文档下标。 */
    List<RerankHit> rerank(String query, List<String> documents, int topN);

    /** 带超时上限的重排（QA 端到端预算传递）。 */
    List<RerankHit> rerank(String query, List<String> documents, int topN, long timeoutMs);

    record SourceChunk(String chunkId, String text) {
    }

    record HistoryMessage(String role, String content) {
    }

    record EmbedOutcome(String model, List<float[]> vectors) {
    }

    record CitationRef(String chunkId, String quote) {
    }

    record TokenUsage(Integer inputTokens, Integer outputTokens) {
    }

    record SummarySegment(String text, List<String> chunkIds) {
    }

    record SummarizeOutcome(String model, String promptVersion, TokenUsage usage,
                            List<SummarySegment> segments) {
    }

    record Keypoint(String text, String chunkId) {
    }

    record KeypointOutcome(String model, String promptVersion, TokenUsage usage, List<Keypoint> keypoints) {
    }

    record AnswerOutcome(String model, String promptVersion, TokenUsage usage,
                         String answer, List<CitationRef> citations, boolean refused) {
    }

    record PolishOutcome(String model, String promptVersion, TokenUsage usage, List<String> polished) {
    }

    record RerankHit(int index, double score) {
    }
}
