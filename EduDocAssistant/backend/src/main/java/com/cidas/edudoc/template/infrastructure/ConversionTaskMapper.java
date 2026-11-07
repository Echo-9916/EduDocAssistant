package com.cidas.edudoc.template.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ConversionTaskMapper {

    int insert(@Param("task") ConversionTask task);

    ConversionTask selectById(@Param("taskId") String taskIdHex);

    int markRunning(@Param("taskId") String taskIdHex);

    int updateSucceeded(@Param("taskId") String taskIdHex, @Param("resultObjectId") String resultObjectIdHex);

    int updateFailed(@Param("taskId") String taskIdHex, @Param("failureCode") String failureCode,
                     @Param("failureMessage") String failureMessage);

    List<ConversionTask> selectByDocument(@Param("documentId") String documentIdHex,
                                          @Param("requestedBy") String requestedByHex);
}
