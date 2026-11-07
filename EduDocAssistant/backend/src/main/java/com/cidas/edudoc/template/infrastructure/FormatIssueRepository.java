package com.cidas.edudoc.template.infrastructure;

import com.cidas.edudoc.common.persistence.Ids;
import com.cidas.edudoc.template.domain.IssueDraft;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/** 格式问题读写（SQL 委托给 {@link FormatIssueMapper}）。 */
@Repository
@RequiredArgsConstructor
public class FormatIssueRepository {

    private final FormatIssueMapper mapper;

    /** 终审 I4/I5：列宽裁剪 + 100 行/语句分片（沿 AiResultRepository 先例），超长文本不再撑爆 ORA-12899/绑定数。 */
    public void insertDrafts(String checkId, List<IssueDraft> drafts) {
        if (drafts.isEmpty()) {
            return;
        }
        List<FormatIssue> rows = drafts.stream()
                .map(draft -> new FormatIssue(Ids.hex16(), checkId, draft.ruleId(), draft.issueType(),
                        draft.locationType(), draft.locationRef(), clamp(draft.expectedValue(), 1000),
                        clamp(draft.actualValue(), 1000), draft.severity(), draft.autoFixable(), "OPEN",
                        clamp(draft.guidance(), 2000)))
                .toList();
        for (int from = 0; from < rows.size(); from += BATCH_SIZE) {
            mapper.insertBatch(rows.subList(from, Math.min(rows.size(), from + BATCH_SIZE)));
        }
    }

    static String clamp(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max - 1) + "…";
    }

    private static final int BATCH_SIZE = 100;

    public List<FormatIssue> page(String checkIdHex, String severity, long offset, int limit) {
        return mapper.selectByCheck(checkIdHex, severity, offset, limit);
    }

    public long count(String checkIdHex, String severity) {
        return mapper.countByCheck(checkIdHex, severity);
    }

    public List<FormatIssue> findByIds(List<String> issueIdsHex) {
        return issueIdsHex.isEmpty() ? List.of() : mapper.selectByIds(issueIdsHex);
    }

    public int markFixed(List<String> issueIdsHex) {
        return issueIdsHex.isEmpty() ? 0 : mapper.updateFixStatusByIds(issueIdsHex, "FIXED");
    }
}
