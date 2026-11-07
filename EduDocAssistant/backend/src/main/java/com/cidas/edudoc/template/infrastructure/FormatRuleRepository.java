package com.cidas.edudoc.template.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/** 格式规则读写（SQL 委托给 {@link FormatRuleMapper}）。 */
@Repository
@RequiredArgsConstructor
public class FormatRuleRepository {

    private final FormatRuleMapper mapper;

    public void insertBatch(List<FormatRule> rules) {
        if (!rules.isEmpty()) {
            mapper.insertBatch(rules);
        }
    }

    public void deleteByVersion(String versionIdHex) {
        mapper.deleteByVersion(versionIdHex);
    }

    public List<FormatRule> findByVersion(String versionIdHex) {
        return mapper.selectByVersion(versionIdHex);
    }

    public List<FormatRule> findByIds(List<String> ruleIdsHex) {
        return ruleIdsHex.isEmpty() ? List.of() : mapper.selectByIds(ruleIdsHex);
    }
}
