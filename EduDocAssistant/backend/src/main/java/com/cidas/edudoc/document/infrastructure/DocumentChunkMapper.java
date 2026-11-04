package com.cidas.edudoc.document.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DocumentChunkMapper {

    int insertBatch(@Param("chunks") List<DocumentChunk> chunks);

    int deleteByVersion(@Param("versionIdHex") String versionIdHex);

    int countByVersion(@Param("versionIdHex") String versionIdHex);

    java.util.List<DocumentChunk> selectAllByVersion(@Param("versionIdHex") String versionIdHex);
}
