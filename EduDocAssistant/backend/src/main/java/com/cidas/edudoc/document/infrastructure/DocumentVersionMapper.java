package com.cidas.edudoc.document.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DocumentVersionMapper {

    int insert(@Param("version") DocumentVersion version);

    List<DocumentVersionRow> selectListByDocument(@Param("documentIdHex") String documentIdHex);

    Integer selectMaxVersionNo(@Param("documentIdHex") String documentIdHex);

    DocumentVersion selectByDocumentAndVersionNo(@Param("documentIdHex") String documentIdHex,
                                                 @Param("versionNo") int versionNo);

    DocumentVersion selectBySourceObject(@Param("sourceObjectIdHex") String sourceObjectIdHex);

    DocumentVersion selectById(@Param("idHex") String idHex);
}
