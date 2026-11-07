package com.cidas.edudoc.template.infrastructure;

import com.cidas.edudoc.common.persistence.Ids;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** 转换任务读写（SQL 委托给 {@link ConversionTaskMapper}）。 */
@Repository
@RequiredArgsConstructor
public class ConversionTaskRepository {

    private final ConversionTaskMapper mapper;

    public String insert(ConversionTask task) {
        task.setId(Ids.hex16());
        mapper.insert(task);
        return task.getId();
    }

    public Optional<ConversionTask> findById(String taskIdHex) {
        return Optional.ofNullable(mapper.selectById(taskIdHex));
    }

    /** QUEUED→RUNNING CAS（progress=10 粗粒度进度，ponytail：细粒度进度等引擎回调）。 */
    public int markRunning(String taskIdHex) {
        return mapper.markRunning(taskIdHex);
    }

    public int updateSucceeded(String taskIdHex, String resultObjectIdHex) {
        return mapper.updateSucceeded(taskIdHex, resultObjectIdHex);
    }

    public int updateFailed(String taskIdHex, String failureCode, String failureMessage) {
        return mapper.updateFailed(taskIdHex, failureCode, failureMessage);
    }

    public List<ConversionTask> findByDocument(String documentIdHex, String requestedByHex) {
        return mapper.selectByDocument(documentIdHex, requestedByHex);
    }
}
