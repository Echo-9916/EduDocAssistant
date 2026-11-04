package com.cidas.edudoc.ai.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AiTaskMapper {

    int insert(@Param("task") AiTask task);

    AiTask selectById(@Param("idHex") String idHex);

    AiTask selectByIdempotencyKey(@Param("idempotencyKey") String idempotencyKey);

    /** 同 (版本, 类型, 发起人) 的活跃（QUEUED/RUNNING）任务，取最早一条；无则 null。 */
    AiTask selectActive(@Param("versionIdHex") String versionIdHex,
                        @Param("taskType") String taskType,
                        @Param("requestedByHex") String requestedByHex);

    /** CAS：QUEUED→RUNNING，返回 1 表示本消费者取得执行权。 */
    int casStart(@Param("idHex") String idHex);

    /** 仅 RUNNING 可写进度（取消/终态后静默）。 */
    int updateProgress(@Param("idHex") String idHex, @Param("progress") int progress);

    /** 仅 RUNNING→SUCCEEDED（取消竞争由守卫兜住）。 */
    int markSucceeded(@Param("idHex") String idHex);

    /** 仅 RUNNING→FAILED，带错误码。 */
    int markFailed(@Param("idHex") String idHex, @Param("errorCode") String errorCode);

    /** QUEUED→CANCELED，返回 0 表示已是 RUNNING/终态（改走 cancel_flag 语义，见 C-04）。 */
    int cancelQueued(@Param("idHex") String idHex);

    /** RUNNING 置取消标记：C-04 裁决用 status=CANCELED + 结果写回守卫，这里直接迁移。 */
    int cancelRunning(@Param("idHex") String idHex);

    int incrementRetry(@Param("idHex") String idHex);
}
