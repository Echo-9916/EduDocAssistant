package com.cidas.edudoc.template.application;

import java.util.List;

/** Word/PDF 格式转换（D-06，详细设计表 3-6 conversions 接口；需求 §5.3.3）。 */
public interface ConversionService {

    ConversionView create(String documentId, String targetFormat);

    ConversionView get(String taskId);

    List<ConversionView> listByDocument(String documentId);

    record ConversionView(String id, String documentId, String targetFormat, String status, int progress,
                          String failureCode, String failureMessage, String downloadUrl,
                          String startedAt, String finishedAt) {
    }
}
