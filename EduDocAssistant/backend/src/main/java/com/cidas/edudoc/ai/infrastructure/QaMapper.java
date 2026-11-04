package com.cidas.edudoc.ai.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 问答会话与消息 SQL 入口（SQL 全在 QaMapper.xml）。 */
@Mapper
public interface QaMapper {

    int insertSession(@Param("session") QaSession session);

    QaSession selectSessionById(@Param("idHex") String idHex);

    /** 同 (文档, 用户) 最近一个活跃会话：createSession 复用口径。 */
    QaSession selectActiveSession(@Param("documentIdHex") String documentIdHex,
                                  @Param("userIdHex") String userIdHex);

    List<QaSession> selectSessionsByDocument(@Param("documentIdHex") String documentIdHex,
                                             @Param("userIdHex") String userIdHex);

    int insertMessages(@Param("messages") List<QaMessage> messages);

    Integer selectMaxSequence(@Param("sessionIdHex") String sessionIdHex);

    List<QaMessage> selectMessages(@Param("sessionIdHex") String sessionIdHex);
}
