package com.cidas.edudoc.template.infrastructure;

import com.cidas.edudoc.common.persistence.Ids;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** 校验任务读写（SQL 委托给 {@link FormatCheckMapper}）。 */
@Repository
@RequiredArgsConstructor
public class FormatCheckRepository {

    private final FormatCheckMapper mapper;

    public String insert(FormatCheck check) {
        check.setId(Ids.hex16());
        mapper.insert(check);
        return check.getId();
    }

    /** J1 口径：PDF 等非 docx 文档的校验请求直接落 ERROR（不产事件），insert 即带 finished_at。 */
    public String insertError(FormatCheck check) {
        check.setId(Ids.hex16());
        mapper.insertError(check);
        return check.getId();
    }

    public Optional<FormatCheck> findById(String checkIdHex) {
        return Optional.ofNullable(mapper.selectById(checkIdHex));
    }

    /** 活跃去重（03 R1 同口径）：同版本+模板版本+发起人存在 QUEUED/RUNNING 时复用。 */
    public Optional<FormatCheck> findActive(String documentVersionIdHex, String templateVersionIdHex,
                                            String requestedByHex) {
        return Optional.ofNullable(mapper.selectActive(documentVersionIdHex, templateVersionIdHex, requestedByHex));
    }

    /** QUEUED→RUNNING CAS；0 行=迟到/重复投递，worker 跳过。 */
    public int markRunning(String checkIdHex) {
        return mapper.markRunning(checkIdHex);
    }

    public int updateResult(String checkIdHex, int totalIssueCount, int blockingIssueCount, String status,
                            String resultSummary) {
        return mapper.updateResult(checkIdHex, totalIssueCount, blockingIssueCount, status, resultSummary);
    }

    public int updateError(String checkIdHex, String resultSummary) {
        return mapper.updateError(checkIdHex, resultSummary);
    }

    public String selectDocumentTemplateBinding(String documentIdHex) {
        return mapper.selectDocumentTemplateBinding(documentIdHex);
    }

    /** 文档 owner（格式异常消息收件人，2-5 生产者③）；文档不存在回 null。 */
    public String selectDocumentOwner(String documentIdHex) {
        return mapper.selectDocumentOwner(documentIdHex);
    }

    public String selectCoursePublishedTemplate(String courseIdHex) {
        return mapper.selectCoursePublishedTemplate(courseIdHex);
    }
}
