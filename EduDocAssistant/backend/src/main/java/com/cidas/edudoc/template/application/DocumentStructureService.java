package com.cidas.edudoc.template.application;

import com.cidas.edudoc.template.application.DocumentStructureService.ElementView;

import java.util.List;

/** 版本结构树（D-03）：懒解析入库 + 学生规范化页读取；校验 worker 亦复用解析入口。 */
public interface DocumentStructureService {

    List<ElementView> listElements(String documentId);

    /** 解析并整版覆盖落库（校验 worker 执行前调用；返回元素数）。 */
    int parseAndPersist(byte[] docxBytes, String versionIdHex);

    record ElementView(String elementKey, String elementType, String chapterNo, Integer sequenceNo,
                       String displayLabel, String captionText, String locationJson) {
    }
}
