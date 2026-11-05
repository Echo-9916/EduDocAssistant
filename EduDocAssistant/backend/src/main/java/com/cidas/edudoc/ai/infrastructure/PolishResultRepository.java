package com.cidas.edudoc.ai.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** 润色结果读写（SQL 委托 {@link PolishResultMapper}）。 */
@Repository
@RequiredArgsConstructor
public class PolishResultRepository {

    private final PolishResultMapper mapper;

    public void insertBatch(List<PolishResult> rows) {
        if (!rows.isEmpty()) {
            mapper.insertBatch(rows);
        }
    }

    public Optional<PolishResult> findById(String idHex) {
        return Optional.ofNullable(mapper.selectById(idHex));
    }

    public Optional<PolishResult> findLatestApplied(String documentIdHex) {
        return Optional.ofNullable(mapper.selectLatestApplied(documentIdHex));
    }

    /** PENDING→目标决策的条件更新；返回 false 表示已被并发决策。 */
    public boolean updateDecisionIfPending(String idHex, String decision, String acceptedText) {
        return mapper.updateDecision(idHex, decision, acceptedText, true) == 1;
    }

    public void updateDecision(String idHex, String decision, String acceptedText) {
        mapper.updateDecision(idHex, decision, acceptedText, false);
    }

    /** 该行采纳产生的版本 ID 链（审计回溯，撤销校验用）。 */
    public List<String> findAppliedVersionIds(String polishIdHex) {
        return mapper.selectAppliedVersionIds(polishIdHex);
    }
}
