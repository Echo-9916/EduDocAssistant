package com.cidas.edudoc.template.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 结构元素读写（SQL 委托给 {@link DocumentElementMapper}）。 */
@Repository
@RequiredArgsConstructor
public class DocumentElementRepository {

    private final DocumentElementMapper mapper;

    /** 重解析幂等：整版换元素行，删+插同事务（概要 §2.4 任务状态为最终事实）。
     * 终审 I4/I5：100 行/语句分片 + 列宽裁剪（element_key 120、chapter_no 20、display_label 80、caption_text 1000）。 */
    @Transactional
    public void replace(String versionIdHex, List<DocumentElement> elements) {
        mapper.deleteByVersion(versionIdHex);
        if (elements.isEmpty()) {
            return;
        }
        for (DocumentElement element : elements) {
            element.setElementKey(clamp(element.getElementKey(), 120));
            element.setChapterNo(clamp(element.getChapterNo(), 20));
            element.setDisplayLabel(clamp(element.getDisplayLabel(), 80));
            element.setCaptionText(clamp(element.getCaptionText(), 1000));
        }
        for (int from = 0; from < elements.size(); from += BATCH_SIZE) {
            mapper.insertBatch(elements.subList(from, Math.min(elements.size(), from + BATCH_SIZE)));
        }
    }

    private static final int BATCH_SIZE = 100;

    private static String clamp(String value, int max) {
        return FormatIssueRepository.clamp(value, max);
    }

    public List<DocumentElement> findByVersion(String versionIdHex) {
        return mapper.selectByVersion(versionIdHex);
    }

    public int countByVersion(String versionIdHex) {
        return mapper.countByVersion(versionIdHex);
    }
}
