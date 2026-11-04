package com.cidas.edudoc.ai.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AiResultMapper {

    int insert(@Param("result") AiResult result);

    /** 任务最新结果（重生成会产生新任务，一任务一结果）。 */
    AiResult selectByTaskId(@Param("taskIdHex") String taskIdHex);

    AiResult selectById(@Param("idHex") String idHex);

    int insertCitations(@Param("citations") List<AiCitation> citations);

    List<AiCitation> selectCitations(@Param("resultIdHex") String resultIdHex);
}
