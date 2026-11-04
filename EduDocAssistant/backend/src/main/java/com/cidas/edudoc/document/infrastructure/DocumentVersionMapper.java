package com.cidas.edudoc.document.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DocumentVersionMapper {

    int insert(@Param("version") DocumentVersion version);

    Integer selectMaxVersionNo(@Param("documentIdHex") String documentIdHex);

    DocumentVersion selectByDocumentAndVersionNo(@Param("documentIdHex") String documentIdHex,
                                                 @Param("versionNo") int versionNo);

    DocumentVersion selectBySourceObject(@Param("sourceObjectIdHex") String sourceObjectIdHex);

    DocumentVersion selectById(@Param("idHex") String idHex);
}
