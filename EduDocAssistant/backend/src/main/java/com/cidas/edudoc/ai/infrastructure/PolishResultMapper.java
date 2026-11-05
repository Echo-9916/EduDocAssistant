package com.cidas.edudoc.ai.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PolishResultMapper {

    int insertBatch(@Param("rows") List<PolishResult> rows);

    PolishResult selectById(@Param("idHex") String idHex);

    /** 最近一次未撤销采纳（ACCEPT/PARTIAL）：REVERT 仅允许对它。 */
    PolishResult selectLatestApplied(@Param("documentIdHex") String documentIdHex);

    int updateDecision(@Param("idHex") String idHex, @Param("decision") String decision,
                       @Param("acceptedText") String acceptedText,
                       @Param("expectPending") boolean expectPending);

    /** 该行历次 POLISH_APPLIED 审计产生的版本 ID（create_time 升序），撤销链校验用。 */
    List<String> selectAppliedVersionIds(@Param("polishId") String polishId);
}
