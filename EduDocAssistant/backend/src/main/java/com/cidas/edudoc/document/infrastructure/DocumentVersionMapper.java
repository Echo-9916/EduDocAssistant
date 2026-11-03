package com.cidas.edudoc.document.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DocumentVersionMapper {

    int insert(@Param("version") DocumentVersion version);

    DocumentVersion selectBySourceObject(@Param("sourceObjectIdHex") String sourceObjectIdHex);

    DocumentVersion selectById(@Param("idHex") String idHex);
}
