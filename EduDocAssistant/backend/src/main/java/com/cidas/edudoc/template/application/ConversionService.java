package com.cidas.edudoc.template.application;

import java.util.List;

/** Word/PDF 格式转换（D-06，详细设计表 3-6 conversions 接口；需求 §5.3.3）。 */
public interface ConversionService {

    ConversionView create(String documentId, String targetFormat);

    ConversionView get(String taskId);

    List<ConversionView> listByDocument(String documentId);

    /** 预览字节流（格式视图 blob 方案：不受 OSS 默认域强制下载策略与 CORS 影响）。 */
    byte[] previewPdf(String taskId);

    /** downloadUrl=附件语义（下载）；previewUrl=内联语义（兜底备用，iframe 主路径走 previewPdf）。 */
    record ConversionView(String id, String documentId, String targetFormat, String status, int progress,
                          String failureCode, String failureMessage, String downloadUrl, String previewUrl,
                          String startedAt, String finishedAt) {
    }
}
