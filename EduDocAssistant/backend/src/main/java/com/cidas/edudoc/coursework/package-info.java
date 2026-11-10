/**
 * coursework 业务域（06 切片 1：课程与作业）。
 *
 * <p>承载教师课程管理、作业发布与提交统计、学生作业列表与提交、批改评分，
 * 对应概要设计 §4.1「导出与统计」模块中的作业链路（统计口径与产物导出分属 Task 1-9 与切片 4）。
 * 域内含 api / application / domain / infrastructure 四层，调用顺序固定为
 * Controller → ApplicationService → Domain/Repository，SQL 只落在 {@code resources/mapper/*.xml}。
 *
 * <p>评分标准与分项得分按 06 计划「速度优先」口径存 JSON 列（{@code rubric_json} / {@code score_json}），
 * 不建 rubric 明细表：分项不做 SQL 聚合，总分由服务端按 JSON 现算。
 */
package com.cidas.edudoc.coursework;
