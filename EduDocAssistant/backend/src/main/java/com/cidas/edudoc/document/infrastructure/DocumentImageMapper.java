package com.cidas.edudoc.document.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DocumentImageMapper {

    int insert(@Param("image") DocumentImage image);

    DocumentImage selectByIdAndDocument(@Param("idHex") String idHex,
                                        @Param("documentIdHex") String documentIdHex);
}