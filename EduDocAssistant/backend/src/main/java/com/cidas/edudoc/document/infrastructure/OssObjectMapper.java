package com.cidas.edudoc.document.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OssObjectMapper {

    int insert(@Param("object") OssObject object);

    OssObject selectByObject(@Param("bucketName") String bucketName, @Param("objectKey") String objectKey);
}
