package com.cidas.edudoc.ai.application;

/**
 * 结果导出（C-04 最小实现）：docx（POI XWPF：标题 + 段落 + 引用脚注），txt 顺带支持。
 * // ponytail: export 域切片（06）落地后迁出 ai 域（任务文档 R6）。
 */
public interface AiResultExportService {

    record ExportFile(String filename, String contentType, byte[] content) {
    }

    ExportFile export(String resultId, String format);
}
