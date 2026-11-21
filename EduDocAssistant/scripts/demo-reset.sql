-- 演示库复位：把库恢复到演示起点（仅演示库执行）。
-- 用法：docker exec -i -e NLS_LANG=AMERICAN_AMERICA.AL32UTF8 oracle-dev sqlplus -s user_cidas/cidas123@//localhost:1521/FREEPDB1 < demo-reset.sql
-- 或由 scripts/demo-reset.ps1 调用（推荐，负责 UTF-8 与基线快照）。
-- 幂等：每一步都带 NOT EXISTS / 状态条件，连续执行两次，第二次各步影响行数为 0。
-- 基线清单存在 edudoc_demo_baseline_doc：默认包含演示文档 E62B... 与种子提交文档；
-- 演示前额外上传的文档用 demo-reset.ps1 -Snapshot -KeepIds <docId> 加入基线。
SET SERVEROUTPUT ON SIZE UNLIMITED
SET FEEDBACK OFF
SET DEFINE OFF
WHENEVER SQLERROR EXIT FAILURE

-- 0. 基线表（演示辅助表，不属于业务表；已存在则跳过）
DECLARE
    v_exists NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_exists FROM user_tables WHERE table_name = 'EDUDOC_DEMO_BASELINE_DOC';
    IF v_exists = 0 THEN
        EXECUTE IMMEDIATE 'CREATE TABLE edudoc_demo_baseline_doc (
            document_id RAW(16) NOT NULL,
            snapshot_at TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
            CONSTRAINT pk_demo_baseline_doc PRIMARY KEY (document_id))';
    END IF;
END;
/

BEGIN
    -- 0.1 基线：版本对比演示文档（固定 ID；换库后按标题兜底）
    INSERT INTO edudoc_demo_baseline_doc (document_id)
    SELECT d.id FROM edudoc_document d
     WHERE (d.id = HEXTORAW('E62B210C072D4737B1864B5BD878FBC2')
            OR d.title = '课程设计报告（演示：版本对比）')
       AND NOT EXISTS (SELECT 1 FROM edudoc_demo_baseline_doc b WHERE b.document_id = d.id);
    DBMS_OUTPUT.PUT_LINE('STEP 0.1 基线-演示文档 rows=' || SQL%ROWCOUNT);

    -- 0.2 基线：SeedDataRunner 生成的 4 份提交文档（以及演示前按同名规则上传的文档）
    INSERT INTO edudoc_demo_baseline_doc (document_id)
    SELECT d.id FROM edudoc_document d JOIN edudoc_user u ON u.id = d.owner_id
     WHERE u.username IN ('student', 'teacher', '2023001', '2023002', '2023003', '2023004')
       AND d.title LIKE 'Web开发课程设计报告\_%' ESCAPE '\'
       AND NOT EXISTS (SELECT 1 FROM edudoc_demo_baseline_doc b WHERE b.document_id = d.id);
    DBMS_OUTPUT.PUT_LINE('STEP 0.2 基线-种子提交文档 rows=' || SQL%ROWCOUNT);
    COMMIT;
END;
/

-- 1. 站内消息全清（种子没有消息行）
BEGIN
    DELETE FROM edudoc_notification;
    DBMS_OUTPUT.PUT_LINE('STEP 1 通知 rows=' || SQL%ROWCOUNT);
END;
/

-- 2. 清掉演示提交触发的未投递事件，避免复位后 Worker 又补发
BEGIN
    DELETE FROM edudoc_outbox_event
     WHERE aggregate_type = 'SUBMISSION' AND status IN ('NEW', 'FAILED');
    DBMS_OUTPUT.PUT_LINE('STEP 2 Outbox rows=' || SQL%ROWCOUNT);
END;
/

-- 3. 删除演示现场发布的作业及其提交，保留两条种子作业
BEGIN
    DELETE FROM edudoc_submission
     WHERE assignment_id IN (
       SELECT id FROM edudoc_assignment
        WHERE title NOT IN ('Web开发课程设计报告', '索引设计实验'));
    DBMS_OUTPUT.PUT_LINE('STEP 3 非种子提交 rows=' || SQL%ROWCOUNT);
END;
/

BEGIN
    DELETE FROM edudoc_assignment
     WHERE title NOT IN ('Web开发课程设计报告', '索引设计实验');
    DBMS_OUTPUT.PUT_LINE('STEP 4 非种子作业 rows=' || SQL%ROWCOUNT);
END;
/

-- 5. 演示文档 E62B...：删 v2 之后的版本，当前版本回 v2
--    先清所有引用这些版本的行，再删版本本身，避免外键阻塞。
BEGIN
    DELETE FROM edudoc_document_annotation
     WHERE document_id = HEXTORAW('E62B210C072D4737B1864B5BD878FBC2');
    DBMS_OUTPUT.PUT_LINE('STEP 5 批注 rows=' || SQL%ROWCOUNT);
END;
/

BEGIN
    DELETE FROM edudoc_ai_citation
     WHERE result_id IN (
       SELECT r.id FROM edudoc_ai_result r
        WHERE r.task_id IN (
          SELECT t.id FROM edudoc_ai_task t
           WHERE t.document_version_id IN (
             SELECT v.id FROM edudoc_document_version v
              WHERE v.document_id = HEXTORAW('E62B210C072D4737B1864B5BD878FBC2') AND v.version_no > 2)));
    DBMS_OUTPUT.PUT_LINE('STEP 6 AI 引用 rows=' || SQL%ROWCOUNT);
END;
/

BEGIN
    DELETE FROM edudoc_qa_message
     WHERE ai_task_id IN (
       SELECT t.id FROM edudoc_ai_task t
        WHERE t.document_version_id IN (
          SELECT v.id FROM edudoc_document_version v
           WHERE v.document_id = HEXTORAW('E62B210C072D4737B1864B5BD878FBC2') AND v.version_no > 2));
    DBMS_OUTPUT.PUT_LINE('STEP 7 问答消息 rows=' || SQL%ROWCOUNT);
END;
/

BEGIN
    DELETE FROM edudoc_polish_result
     WHERE document_version_id IN (
       SELECT v.id FROM edudoc_document_version v
        WHERE v.document_id = HEXTORAW('E62B210C072D4737B1864B5BD878FBC2') AND v.version_no > 2);
    DBMS_OUTPUT.PUT_LINE('STEP 8 润色结果 rows=' || SQL%ROWCOUNT);
END;
/

BEGIN
    DELETE FROM edudoc_ai_result
     WHERE task_id IN (
       SELECT t.id FROM edudoc_ai_task t
        WHERE t.document_version_id IN (
          SELECT v.id FROM edudoc_document_version v
           WHERE v.document_id = HEXTORAW('E62B210C072D4737B1864B5BD878FBC2') AND v.version_no > 2));
    DBMS_OUTPUT.PUT_LINE('STEP 9 AI 结果 rows=' || SQL%ROWCOUNT);
END;
/

BEGIN
    DELETE FROM edudoc_ai_task
     WHERE document_version_id IN (
       SELECT v.id FROM edudoc_document_version v
        WHERE v.document_id = HEXTORAW('E62B210C072D4737B1864B5BD878FBC2') AND v.version_no > 2);
    DBMS_OUTPUT.PUT_LINE('STEP 10 AI 任务 rows=' || SQL%ROWCOUNT);
END;
/

BEGIN
    DELETE FROM edudoc_format_issue
     WHERE format_check_id IN (
       SELECT c.id FROM edudoc_format_check c
        WHERE c.document_version_id IN (
          SELECT v.id FROM edudoc_document_version v
           WHERE v.document_id = HEXTORAW('E62B210C072D4737B1864B5BD878FBC2') AND v.version_no > 2));
    DBMS_OUTPUT.PUT_LINE('STEP 11 格式问题 rows=' || SQL%ROWCOUNT);
END;
/

BEGIN
    DELETE FROM edudoc_format_check
     WHERE document_version_id IN (
       SELECT v.id FROM edudoc_document_version v
        WHERE v.document_id = HEXTORAW('E62B210C072D4737B1864B5BD878FBC2') AND v.version_no > 2);
    DBMS_OUTPUT.PUT_LINE('STEP 12 格式校验 rows=' || SQL%ROWCOUNT);
END;
/

BEGIN
    DELETE FROM edudoc_conversion_task
     WHERE source_version_id IN (
       SELECT v.id FROM edudoc_document_version v
        WHERE v.document_id = HEXTORAW('E62B210C072D4737B1864B5BD878FBC2') AND v.version_no > 2);
    DBMS_OUTPUT.PUT_LINE('STEP 13 转换任务 rows=' || SQL%ROWCOUNT);
END;
/

BEGIN
    DELETE FROM edudoc_export_task
     WHERE document_version_id IN (
       SELECT v.id FROM edudoc_document_version v
        WHERE v.document_id = HEXTORAW('E62B210C072D4737B1864B5BD878FBC2') AND v.version_no > 2);
    DBMS_OUTPUT.PUT_LINE('STEP 14 导出任务 rows=' || SQL%ROWCOUNT);
END;
/

BEGIN
    DELETE FROM edudoc_reference_item
     WHERE document_version_id IN (
       SELECT v.id FROM edudoc_document_version v
        WHERE v.document_id = HEXTORAW('E62B210C072D4737B1864B5BD878FBC2') AND v.version_no > 2);
    DBMS_OUTPUT.PUT_LINE('STEP 15 参考文献 rows=' || SQL%ROWCOUNT);
END;
/

BEGIN
    DELETE FROM edudoc_submission
     WHERE document_version_id IN (
       SELECT v.id FROM edudoc_document_version v
        WHERE v.document_id = HEXTORAW('E62B210C072D4737B1864B5BD878FBC2') AND v.version_no > 2);
    DBMS_OUTPUT.PUT_LINE('STEP 16 演示文档提交 rows=' || SQL%ROWCOUNT);
END;
/

BEGIN
    DELETE FROM edudoc_collab_snapshot
     WHERE document_version_id IN (
       SELECT v.id FROM edudoc_document_version v
        WHERE v.document_id = HEXTORAW('E62B210C072D4737B1864B5BD878FBC2') AND v.version_no > 2);
    DBMS_OUTPUT.PUT_LINE('STEP 17 协作快照 rows=' || SQL%ROWCOUNT);
END;
/

BEGIN
    DELETE FROM edudoc_collab_member
     WHERE collab_session_id IN (
       SELECT id FROM edudoc_collab_session
        WHERE document_id = HEXTORAW('E62B210C072D4737B1864B5BD878FBC2'));
    DBMS_OUTPUT.PUT_LINE('STEP 18 协作成员 rows=' || SQL%ROWCOUNT);
END;
/

BEGIN
    UPDATE edudoc_collab_session
       SET base_version_id = (
             SELECT id FROM edudoc_document_version
              WHERE document_id = HEXTORAW('E62B210C072D4737B1864B5BD878FBC2') AND version_no = 2),
           update_time = SYSTIMESTAMP
     WHERE document_id = HEXTORAW('E62B210C072D4737B1864B5BD878FBC2')
       AND base_version_id IN (
         SELECT id FROM edudoc_document_version
          WHERE document_id = HEXTORAW('E62B210C072D4737B1864B5BD878FBC2') AND version_no > 2);
    DBMS_OUTPUT.PUT_LINE('STEP 19 协作会话基线 rows=' || SQL%ROWCOUNT);
END;
/

BEGIN
    DELETE FROM edudoc_share_grant
     WHERE share_id IN (
       SELECT id FROM edudoc_document_share
        WHERE document_id = HEXTORAW('E62B210C072D4737B1864B5BD878FBC2'));
    DBMS_OUTPUT.PUT_LINE('STEP 20 共享授权 rows=' || SQL%ROWCOUNT);
END;
/

BEGIN
    DELETE FROM edudoc_document_share
     WHERE document_id = HEXTORAW('E62B210C072D4737B1864B5BD878FBC2');
    DBMS_OUTPUT.PUT_LINE('STEP 21 共享链接 rows=' || SQL%ROWCOUNT);
END;
/

BEGIN
    DELETE FROM edudoc_chunk_embedding
     WHERE chunk_id IN (
       SELECT c.id FROM edudoc_document_chunk c
        WHERE c.document_version_id IN (
          SELECT v.id FROM edudoc_document_version v
           WHERE v.document_id = HEXTORAW('E62B210C072D4737B1864B5BD878FBC2') AND v.version_no > 2));
    DBMS_OUTPUT.PUT_LINE('STEP 22 分块向量 rows=' || SQL%ROWCOUNT);
END;
/

BEGIN
    DELETE FROM edudoc_document_chunk
     WHERE document_version_id IN (
       SELECT v.id FROM edudoc_document_version v
        WHERE v.document_id = HEXTORAW('E62B210C072D4737B1864B5BD878FBC2') AND v.version_no > 2);
    DBMS_OUTPUT.PUT_LINE('STEP 23 分块 rows=' || SQL%ROWCOUNT);
END;
/

BEGIN
    DELETE FROM edudoc_document_element
     WHERE document_version_id IN (
       SELECT v.id FROM edudoc_document_version v
        WHERE v.document_id = HEXTORAW('E62B210C072D4737B1864B5BD878FBC2') AND v.version_no > 2);
    DBMS_OUTPUT.PUT_LINE('STEP 24 文档元素 rows=' || SQL%ROWCOUNT);
END;
/

BEGIN
    DELETE FROM edudoc_derived_file
     WHERE document_version_id IN (
       SELECT v.id FROM edudoc_document_version v
        WHERE v.document_id = HEXTORAW('E62B210C072D4737B1864B5BD878FBC2') AND v.version_no > 2);
    DBMS_OUTPUT.PUT_LINE('STEP 25 派生文件 rows=' || SQL%ROWCOUNT);
END;
/

BEGIN
    DELETE FROM edudoc_document_version
     WHERE document_id = HEXTORAW('E62B210C072D4737B1864B5BD878FBC2') AND version_no > 2;
    DBMS_OUTPUT.PUT_LINE('STEP 26 v2 之后版本 rows=' || SQL%ROWCOUNT);
END;
/

BEGIN
    UPDATE edudoc_document
       SET current_version_id = (
             SELECT id FROM edudoc_document_version
              WHERE document_id = HEXTORAW('E62B210C072D4737B1864B5BD878FBC2') AND version_no = 2),
           update_time = SYSTIMESTAMP
     WHERE id = HEXTORAW('E62B210C072D4737B1864B5BD878FBC2')
       AND current_version_id <> (
             SELECT id FROM edudoc_document_version
              WHERE document_id = HEXTORAW('E62B210C072D4737B1864B5BD878FBC2') AND version_no = 2);
    DBMS_OUTPUT.PUT_LINE('STEP 27 当前版本回 v2 rows=' || SQL%ROWCOUNT);
END;
/

-- 28. student / teacher 恢复 A
BEGIN
    UPDATE edudoc_user
       SET account_status = 'A', update_time = SYSTIMESTAMP
     WHERE username IN ('student', 'teacher') AND account_status <> 'A';
    DBMS_OUTPUT.PUT_LINE('STEP 28 演示账号状态 rows=' || SQL%ROWCOUNT);
END;
/

-- 29. student / teacher 名下不在基线清单里的文档软删除（E62B 与种子提交文档已在基线）
BEGIN
    UPDATE edudoc_document d
       SET status = 'DELETED', deleted_at = SYSTIMESTAMP,
           purge_at = SYSTIMESTAMP + NUMTODSINTERVAL(30, 'DAY'),
           update_time = SYSTIMESTAMP
     WHERE d.status <> 'DELETED'
       AND d.deleted_at IS NULL
       AND d.owner_id IN (SELECT id FROM edudoc_user WHERE username IN ('student', 'teacher'))
       AND NOT EXISTS (SELECT 1 FROM edudoc_demo_baseline_doc b WHERE b.document_id = d.id);
    DBMS_OUTPUT.PUT_LINE('STEP 29 非基线文档软删除 rows=' || SQL%ROWCOUNT);
END;
/

COMMIT;

PROMPT 演示库复位完成。