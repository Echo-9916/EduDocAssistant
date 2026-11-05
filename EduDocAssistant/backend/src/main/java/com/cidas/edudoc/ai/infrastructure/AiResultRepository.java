package com.cidas.edudoc.ai.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** 智能结果与引用读写（SQL 委托 {@link AiResultMapper}）。 */
@Repository
@RequiredArgsConstructor
public class AiResultRepository {

    private static final int QUOTE_MAX = 600;

    private final AiResultMapper mapper;

    public void insert(AiResult result) {
        mapper.insert(result);
    }

    public Optional<AiResult> findLatestByTask(String taskIdHex) {
        return Optional.ofNullable(mapper.selectByTaskId(taskIdHex));
    }

    public Optional<AiResult> findById(String idHex) {
        return Optional.ofNullable(mapper.selectById(idHex));
    }

    public void insertCitations(List<AiCitation> citations) {
        if (citations.isEmpty()) {
            return;
        }
        for (int from = 0; from < citations.size(); from += 50) {
            mapper.insertCitations(citations.subList(from, Math.min(citations.size(), from + 50)));
        }
    }

    public List<AiCitation> findCitations(String resultIdHex) {
        return mapper.selectCitations(resultIdHex);
    }

    public int deleteCitationsByVersion(String versionIdHex) {
        return mapper.deleteCitationsByVersion(versionIdHex);
    }

    /** 引用片段截断（VARCHAR2(2000) 字节语义下留足多字节余量）。 */
    public static String clipQuote(String text) {
        if (text == null) {
            return null;
        }
        return text.length() > QUOTE_MAX ? text.substring(0, QUOTE_MAX) : text;
    }
}
