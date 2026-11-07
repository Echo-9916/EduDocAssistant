package com.cidas.edudoc.template.infrastructure;

import com.cidas.edudoc.template.domain.ElementDraft;
import com.cidas.edudoc.template.domain.IssueDraft;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 终审 I4/I5：大批量写入分片（沿 AiResultRepository 50/批先例）、列宽溢出落库前裁剪
 * （expected/actual 1000、guidance 2000、caption_text 1000）。超长或超限一次插入必须拆多条语句，
 * 否则单语句数万绑定/ORA-12899 会回滚整次校验事务。
 */
class TemplateRepositoriesBatchTest {

    private final FormatIssueMapper issueMapper = mock(FormatIssueMapper.class);
    private final FormatIssueRepository issueRepository = new FormatIssueRepository(issueMapper);
    private final DocumentElementMapper elementMapper = mock(DocumentElementMapper.class);
    private final DocumentElementRepository elementRepository = new DocumentElementRepository(elementMapper);

    private static IssueDraft longIssue(String pad) {
        return new IssueDraft("R1", "FONT", "PARAGRAPH", "{}", pad, pad, "BLOCKER", "Y", pad);
    }

    private static DocumentElement longElement(String pad) {
        return new DocumentElement(null, "V1", "K", "BODYISH", "1", 1, pad, pad, "{}");
    }

    @Test
    @SuppressWarnings("unchecked")
    void issueInsertsAreShardedAndTruncated() {
        String pad = "字".repeat(1500);
        List<IssueDraft> drafts = java.util.stream.IntStream.range(0, 250)
                .mapToObj(i -> longIssue(pad)).toList();

        issueRepository.insertDrafts("C1", drafts);

        ArgumentCaptor<List<FormatIssue>> captor = ArgumentCaptor.forClass(List.class);
        verify(issueMapper, times(3)).insertBatch(captor.capture());
        assertEquals(100, captor.getAllValues().get(0).size());
        assertEquals(50, captor.getAllValues().get(2).size());
        FormatIssue first = captor.getAllValues().get(0).get(0);
        assertTrue(first.getExpectedValue().length() <= 1000);
        assertTrue(first.getGuidance().length() <= 2000);
    }

    @Test
    @SuppressWarnings("unchecked")
    void elementReplacesAreShardedAndTruncated() {
        List<DocumentElement> elements = java.util.stream.IntStream.range(0, 150)
                .mapToObj(i -> longElement("长".repeat(1500))).toList();

        elementRepository.replace("V1", elements);

        verify(elementMapper).deleteByVersion("V1");
        ArgumentCaptor<List<DocumentElement>> captor = ArgumentCaptor.forClass(List.class);
        verify(elementMapper, times(2)).insertBatch(captor.capture());
        assertEquals(100, captor.getAllValues().get(0).size());
        assertEquals(50, captor.getAllValues().get(1).size());
        assertTrue(captor.getAllValues().get(0).get(0).getCaptionText().length() <= 1000);
    }

    @Test
    void emptyDraftsSkipSql() {
        issueRepository.insertDrafts("C1", List.of());
        verify(issueMapper, times(0)).insertBatch(anyList());
    }
}
