-- Maintain update_time for every business table.
SET DEFINE OFF;

CREATE OR REPLACE TRIGGER trg_user_bu
BEFORE UPDATE ON edudoc_user
FOR EACH ROW
BEGIN
    :NEW.update_time := SYSTIMESTAMP;
END;
/

CREATE OR REPLACE TRIGGER trg_role_bu
BEFORE UPDATE ON edudoc_role
FOR EACH ROW
BEGIN
    :NEW.update_time := SYSTIMESTAMP;
END;
/

CREATE OR REPLACE TRIGGER trg_permission_bu
BEFORE UPDATE ON edudoc_permission
FOR EACH ROW
BEGIN
    :NEW.update_time := SYSTIMESTAMP;
END;
/

CREATE OR REPLACE TRIGGER trg_user_role_bu
BEFORE UPDATE ON edudoc_user_role
FOR EACH ROW
BEGIN
    :NEW.update_time := SYSTIMESTAMP;
END;
/

CREATE OR REPLACE TRIGGER trg_role_permission_bu
BEFORE UPDATE ON edudoc_role_permission
FOR EACH ROW
BEGIN
    :NEW.update_time := SYSTIMESTAMP;
END;
/

CREATE OR REPLACE TRIGGER trg_course_bu
BEFORE UPDATE ON edudoc_course
FOR EACH ROW
BEGIN
    :NEW.update_time := SYSTIMESTAMP;
END;
/

CREATE OR REPLACE TRIGGER trg_course_member_bu
BEFORE UPDATE ON edudoc_course_member
FOR EACH ROW
BEGIN
    :NEW.update_time := SYSTIMESTAMP;
END;
/

CREATE OR REPLACE TRIGGER trg_oss_object_bu
BEFORE UPDATE ON edudoc_oss_object
FOR EACH ROW
BEGIN
    :NEW.update_time := SYSTIMESTAMP;
END;
/

CREATE OR REPLACE TRIGGER trg_template_bu
BEFORE UPDATE ON edudoc_template
FOR EACH ROW
BEGIN
    :NEW.update_time := SYSTIMESTAMP;
END;
/

CREATE OR REPLACE TRIGGER trg_template_version_bu
BEFORE UPDATE ON edudoc_template_version
FOR EACH ROW
BEGIN
    :NEW.update_time := SYSTIMESTAMP;
END;
/

CREATE OR REPLACE TRIGGER trg_format_rule_bu
BEFORE UPDATE ON edudoc_format_rule
FOR EACH ROW
BEGIN
    :NEW.update_time := SYSTIMESTAMP;
END;
/

CREATE OR REPLACE TRIGGER trg_document_bu
BEFORE UPDATE ON edudoc_document
FOR EACH ROW
BEGIN
    :NEW.update_time := SYSTIMESTAMP;
END;
/

CREATE OR REPLACE TRIGGER trg_document_version_bu
BEFORE UPDATE ON edudoc_document_version
FOR EACH ROW
BEGIN
    :NEW.update_time := SYSTIMESTAMP;
END;
/

CREATE OR REPLACE TRIGGER trg_derived_file_bu
BEFORE UPDATE ON edudoc_derived_file
FOR EACH ROW
BEGIN
    :NEW.update_time := SYSTIMESTAMP;
END;
/

CREATE OR REPLACE TRIGGER trg_document_element_bu
BEFORE UPDATE ON edudoc_document_element
FOR EACH ROW
BEGIN
    :NEW.update_time := SYSTIMESTAMP;
END;
/

CREATE OR REPLACE TRIGGER trg_document_chunk_bu
BEFORE UPDATE ON edudoc_document_chunk
FOR EACH ROW
BEGIN
    :NEW.update_time := SYSTIMESTAMP;
END;
/

CREATE OR REPLACE TRIGGER trg_ai_model_profile_bu
BEFORE UPDATE ON edudoc_ai_model_profile
FOR EACH ROW
BEGIN
    :NEW.update_time := SYSTIMESTAMP;
END;
/

CREATE OR REPLACE TRIGGER trg_chunk_embedding_bu
BEFORE UPDATE ON edudoc_chunk_embedding
FOR EACH ROW
BEGIN
    :NEW.update_time := SYSTIMESTAMP;
END;
/

CREATE OR REPLACE TRIGGER trg_document_share_bu
BEFORE UPDATE ON edudoc_document_share
FOR EACH ROW
BEGIN
    :NEW.update_time := SYSTIMESTAMP;
END;
/

CREATE OR REPLACE TRIGGER trg_share_grant_bu
BEFORE UPDATE ON edudoc_share_grant
FOR EACH ROW
BEGIN
    :NEW.update_time := SYSTIMESTAMP;
END;
/

CREATE OR REPLACE TRIGGER trg_format_check_bu
BEFORE UPDATE ON edudoc_format_check
FOR EACH ROW
BEGIN
    :NEW.update_time := SYSTIMESTAMP;
END;
/

CREATE OR REPLACE TRIGGER trg_format_issue_bu
BEFORE UPDATE ON edudoc_format_issue
FOR EACH ROW
BEGIN
    :NEW.update_time := SYSTIMESTAMP;
END;
/

CREATE OR REPLACE TRIGGER trg_conversion_task_bu
BEFORE UPDATE ON edudoc_conversion_task
FOR EACH ROW
BEGIN
    :NEW.update_time := SYSTIMESTAMP;
END;
/

CREATE OR REPLACE TRIGGER trg_ai_task_bu
BEFORE UPDATE ON edudoc_ai_task
FOR EACH ROW
BEGIN
    :NEW.update_time := SYSTIMESTAMP;
END;
/

CREATE OR REPLACE TRIGGER trg_ai_result_bu
BEFORE UPDATE ON edudoc_ai_result
FOR EACH ROW
BEGIN
    :NEW.update_time := SYSTIMESTAMP;
END;
/

CREATE OR REPLACE TRIGGER trg_ai_citation_bu
BEFORE UPDATE ON edudoc_ai_citation
FOR EACH ROW
BEGIN
    :NEW.update_time := SYSTIMESTAMP;
END;
/

CREATE OR REPLACE TRIGGER trg_qa_session_bu
BEFORE UPDATE ON edudoc_qa_session
FOR EACH ROW
BEGIN
    :NEW.update_time := SYSTIMESTAMP;
END;
/

CREATE OR REPLACE TRIGGER trg_qa_message_bu
BEFORE UPDATE ON edudoc_qa_message
FOR EACH ROW
BEGIN
    :NEW.update_time := SYSTIMESTAMP;
END;
/

CREATE OR REPLACE TRIGGER trg_polish_result_bu
BEFORE UPDATE ON edudoc_polish_result
FOR EACH ROW
BEGIN
    :NEW.update_time := SYSTIMESTAMP;
END;
/

CREATE OR REPLACE TRIGGER trg_document_annotation_bu
BEFORE UPDATE ON edudoc_document_annotation
FOR EACH ROW
BEGIN
    :NEW.update_time := SYSTIMESTAMP;
END;
/

CREATE OR REPLACE TRIGGER trg_collab_session_bu
BEFORE UPDATE ON edudoc_collab_session
FOR EACH ROW
BEGIN
    :NEW.update_time := SYSTIMESTAMP;
END;
/

CREATE OR REPLACE TRIGGER trg_collab_member_bu
BEFORE UPDATE ON edudoc_collab_member
FOR EACH ROW
BEGIN
    :NEW.update_time := SYSTIMESTAMP;
END;
/

CREATE OR REPLACE TRIGGER trg_collab_snapshot_bu
BEFORE UPDATE ON edudoc_collab_snapshot
FOR EACH ROW
BEGIN
    :NEW.update_time := SYSTIMESTAMP;
END;
/

CREATE OR REPLACE TRIGGER trg_assignment_bu
BEFORE UPDATE ON edudoc_assignment
FOR EACH ROW
BEGIN
    :NEW.update_time := SYSTIMESTAMP;
END;
/

CREATE OR REPLACE TRIGGER trg_submission_bu
BEFORE UPDATE ON edudoc_submission
FOR EACH ROW
BEGIN
    :NEW.update_time := SYSTIMESTAMP;
END;
/

CREATE OR REPLACE TRIGGER trg_export_task_bu
BEFORE UPDATE ON edudoc_export_task
FOR EACH ROW
BEGIN
    :NEW.update_time := SYSTIMESTAMP;
END;
/

CREATE OR REPLACE TRIGGER trg_reference_item_bu
BEFORE UPDATE ON edudoc_reference_item
FOR EACH ROW
BEGIN
    :NEW.update_time := SYSTIMESTAMP;
END;
/

CREATE OR REPLACE TRIGGER trg_audit_log_bu
BEFORE UPDATE ON edudoc_audit_log
FOR EACH ROW
BEGIN
    :NEW.update_time := SYSTIMESTAMP;
END;
/

CREATE OR REPLACE TRIGGER trg_outbox_event_bu
BEFORE UPDATE ON edudoc_outbox_event
FOR EACH ROW
BEGIN
    :NEW.update_time := SYSTIMESTAMP;
END;
/

CREATE OR REPLACE TRIGGER trg_notification_bu
BEFORE UPDATE ON edudoc_notification
FOR EACH ROW
BEGIN
    :NEW.update_time := SYSTIMESTAMP;
END;
/

CREATE OR REPLACE TRIGGER trg_notification_pref_bu
BEFORE UPDATE ON edudoc_notification_preference
FOR EACH ROW
BEGIN
    :NEW.update_time := SYSTIMESTAMP;
END;
/

CREATE OR REPLACE TRIGGER trg_access_policy_bu
BEFORE UPDATE ON edudoc_access_policy
FOR EACH ROW
BEGIN
    :NEW.update_time := SYSTIMESTAMP;
END;
/
