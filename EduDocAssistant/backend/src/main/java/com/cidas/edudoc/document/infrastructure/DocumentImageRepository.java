package com.cidas.edudoc.document.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** 文档图片读写（SQL 见 DocumentImageMapper.xml）。 */
@Repository
@RequiredArgsConstructor
public class DocumentImageRepository {

    private final DocumentImageMapper mapper;

    public void insert(DocumentImage image) {
        mapper.insert(image);
    }

    /** 图片必须属于该文档（跨文档引用视为不存在）。 */
    public DocumentImage findByIdAndDocument(String imageIdHex, String documentIdHex) {
        return mapper.selectByIdAndDocument(imageIdHex, documentIdHex);
    }
}