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

    /** 采纳时回写「被替换版本」到 document_version_id：撤销=回拨至该行（D3 公式，链式正确）。 */
    int updateDecisionApplied(@Param("idHex") String idHex, @Param("decision") String decision,
                              @Param("acceptedText") String acceptedText,
                              @Param("replacedVersionIdHex") String replacedVersionIdHex);
}
