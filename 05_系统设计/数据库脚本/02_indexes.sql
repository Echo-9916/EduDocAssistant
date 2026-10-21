-- CIDAS secondary indexes
SET DEFINE OFF;

-- 用户筛选
CREATE INDEX ix_user_status ON edudoc_user (account_status, department);
-- 课程学期筛选
CREATE INDEX ix_course_term ON edudoc_course (term_code, status);
-- 用户课程列表
CREATE INDEX ix_course_member_user ON edudoc_course_member (user_id, status);
-- 我的文档
CREATE INDEX ix_doc_owner_status ON edudoc_document (owner_id, status, update_time);
-- 课程文档
CREATE INDEX ix_doc_course_status ON edudoc_document (course_id, status, update_time);
-- 回收站清理
CREATE INDEX ix_doc_purge ON edudoc_document (status, purge_at);
-- 标题检索前缀
CREATE INDEX ix_doc_title_search ON edudoc_document (title);
-- 版本列表
CREATE INDEX ix_docver_doc_time ON edudoc_document_version (document_id, version_no);
-- 对象巡检
CREATE INDEX ix_oss_role_status ON edudoc_oss_object (object_role, status);
-- 派生文件查询
CREATE INDEX ix_derived_version ON edudoc_derived_file (document_version_id, status);
-- 图表编号顺序
CREATE INDEX ix_element_version ON edudoc_document_element (document_version_id, element_type, chapter_no, sequence_no);
-- 分块顺序
CREATE INDEX ix_chunk_version ON edudoc_document_chunk (document_version_id, chunk_no);
-- 待索引任务
CREATE INDEX ix_chunk_index ON edudoc_document_chunk (index_status, update_time);
-- 文档共享查询
CREATE INDEX ix_share_document ON edudoc_document_share (document_id, status);
-- ACL 鉴权
CREATE INDEX ix_grant_principal ON edudoc_share_grant (principal_type, principal_ref, permission_level);
-- 格式校验历史
CREATE INDEX ix_fmtcheck_doc ON edudoc_format_check (document_id, status, create_time);
-- 问题清单
CREATE INDEX ix_issue_check ON edudoc_format_issue (format_check_id, severity, fix_status);
-- 转换队列
CREATE INDEX ix_conv_status ON edudoc_conversion_task (status, create_time);
-- 智能任务调度
CREATE INDEX ix_ai_status ON edudoc_ai_task (status, priority, create_time);
-- 文档任务历史
CREATE INDEX ix_ai_doc ON edudoc_ai_task (document_id, document_version_id, task_type);
-- 问答历史
CREATE INDEX ix_qa_session_user ON edudoc_qa_session (user_id, document_id, status);
-- 协作会话查询
CREATE INDEX ix_collab_doc ON edudoc_collab_session (document_id, status);
-- 作业列表
CREATE INDEX ix_assignment_course ON edudoc_assignment (course_id, status, due_time);
-- 提交统计
CREATE INDEX ix_submission_assignment ON edudoc_submission (assignment_id, status, student_id);
-- 导出队列
CREATE INDEX ix_export_status ON edudoc_export_task (status, create_time);
-- 审计时间检索
CREATE INDEX ix_audit_time ON edudoc_audit_log (occurred_at);
-- 用户审计
CREATE INDEX ix_audit_actor ON edudoc_audit_log (actor_id, occurred_at);
-- 文档审计
CREATE INDEX ix_audit_document ON edudoc_audit_log (document_id, occurred_at);
-- 事件投递
CREATE INDEX ix_outbox_status ON edudoc_outbox_event (status, next_retry_at);

-- Oracle AI Vector Search index for the fixed 1024-dimensional profile.
CREATE VECTOR INDEX ix_chunk_embedding_vec
ON edudoc_chunk_embedding (embedding)
ORGANIZATION INMEMORY NEIGHBOR GRAPH
DISTANCE COSINE
WITH TARGET ACCURACY 95;