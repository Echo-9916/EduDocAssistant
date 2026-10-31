-- Initial roles, permissions and role-permission mappings.
-- Create the first administrator account through an approved local bootstrap procedure.
SET DEFINE OFF;

INSERT INTO edudoc_role (role_code, role_name, is_builtin, description, status)
VALUES ('STUDENT', '学生', 'Y', '本人及共享文档的查看、编辑、智能处理与提交', 'A');
INSERT INTO edudoc_role (role_code, role_name, is_builtin, description, status)
VALUES ('TEACHER', '教师', 'Y', '所授课程模板、作业、提交统计与文档管理', 'A');
INSERT INTO edudoc_role (role_code, role_name, is_builtin, description, status)
VALUES ('OPS', '运维人员', 'Y', '授权范围内课程配置、模板、统计与审计；账号与角色维护、服务巡检与审计查询', 'A');

INSERT INTO edudoc_permission (permission_code, permission_name, resource_type, action_code, description) VALUES ('DOCUMENT_READ', '查看文档', 'DOCUMENT', 'READ', '查看文档');
INSERT INTO edudoc_permission (permission_code, permission_name, resource_type, action_code, description) VALUES ('DOCUMENT_CREATE', '创建文档', 'DOCUMENT', 'CREATE', '创建文档');
INSERT INTO edudoc_permission (permission_code, permission_name, resource_type, action_code, description) VALUES ('DOCUMENT_EDIT', '编辑文档', 'DOCUMENT', 'EDIT', '编辑文档');
INSERT INTO edudoc_permission (permission_code, permission_name, resource_type, action_code, description) VALUES ('DOCUMENT_DELETE', '删除文档', 'DOCUMENT', 'DELETE', '删除文档');
INSERT INTO edudoc_permission (permission_code, permission_name, resource_type, action_code, description) VALUES ('DOCUMENT_DOWNLOAD', '下载文档', 'DOCUMENT', 'DOWNLOAD', '下载文档');
INSERT INTO edudoc_permission (permission_code, permission_name, resource_type, action_code, description) VALUES ('DOCUMENT_SHARE', '设置文档共享', 'DOCUMENT', 'SHARE', '设置文档共享');
INSERT INTO edudoc_permission (permission_code, permission_name, resource_type, action_code, description) VALUES ('AI_SUMMARY', '生成摘要', 'AI', 'SUMMARY', '生成摘要');
INSERT INTO edudoc_permission (permission_code, permission_name, resource_type, action_code, description) VALUES ('AI_KEYPOINT', '提取要点', 'AI', 'KEYPOINT', '提取要点');
INSERT INTO edudoc_permission (permission_code, permission_name, resource_type, action_code, description) VALUES ('AI_QA', '文档问答', 'AI', 'QA', '文档问答');
INSERT INTO edudoc_permission (permission_code, permission_name, resource_type, action_code, description) VALUES ('AI_POLISH', '文本润色', 'AI', 'POLISH', '文本润色');
INSERT INTO edudoc_permission (permission_code, permission_name, resource_type, action_code, description) VALUES ('TEMPLATE_READ', '查看课程模板', 'TEMPLATE', 'READ', '查看课程模板');
INSERT INTO edudoc_permission (permission_code, permission_name, resource_type, action_code, description) VALUES ('TEMPLATE_MANAGE', '维护课程模板', 'TEMPLATE', 'MANAGE', '维护课程模板');
INSERT INTO edudoc_permission (permission_code, permission_name, resource_type, action_code, description) VALUES ('FORMAT_CHECK', '执行格式校验', 'FORMAT', 'CHECK', '执行格式校验');
INSERT INTO edudoc_permission (permission_code, permission_name, resource_type, action_code, description) VALUES ('FORMAT_CONVERT', '执行格式转换', 'FORMAT', 'CONVERT', '执行格式转换');
INSERT INTO edudoc_permission (permission_code, permission_name, resource_type, action_code, description) VALUES ('COLLAB_EDIT', '参与协作编辑', 'COLLAB', 'EDIT', '参与协作编辑');
INSERT INTO edudoc_permission (permission_code, permission_name, resource_type, action_code, description) VALUES ('VERSION_RESTORE', '恢复文档版本', 'VERSION', 'RESTORE', '恢复文档版本');
INSERT INTO edudoc_permission (permission_code, permission_name, resource_type, action_code, description) VALUES ('ROLE_MANAGE', '维护角色与账号', 'ROLE', 'MANAGE', '维护角色与账号');
INSERT INTO edudoc_permission (permission_code, permission_name, resource_type, action_code, description) VALUES ('AUDIT_READ', '查询审计日志', 'AUDIT', 'READ', '查询审计日志');
INSERT INTO edudoc_permission (permission_code, permission_name, resource_type, action_code, description) VALUES ('ASSIGNMENT_MANAGE', '发布和维护作业', 'ASSIGNMENT', 'MANAGE', '发布和维护作业');
INSERT INTO edudoc_permission (permission_code, permission_name, resource_type, action_code, description) VALUES ('SUBMISSION_READ', '查看提交统计', 'SUBMISSION', 'READ', '查看提交统计');

INSERT INTO edudoc_role_permission (role_id, permission_id) SELECT r.id, p.id FROM edudoc_role r CROSS JOIN edudoc_permission p WHERE r.role_code = 'STUDENT' AND p.permission_code IN ('DOCUMENT_READ', 'DOCUMENT_CREATE', 'DOCUMENT_EDIT', 'DOCUMENT_DELETE', 'DOCUMENT_DOWNLOAD', 'DOCUMENT_SHARE', 'AI_SUMMARY', 'AI_KEYPOINT', 'AI_QA', 'AI_POLISH', 'TEMPLATE_READ', 'FORMAT_CHECK', 'FORMAT_CONVERT', 'COLLAB_EDIT', 'VERSION_RESTORE');
INSERT INTO edudoc_role_permission (role_id, permission_id) SELECT r.id, p.id FROM edudoc_role r CROSS JOIN edudoc_permission p WHERE r.role_code = 'TEACHER' AND p.permission_code IN ('DOCUMENT_READ', 'DOCUMENT_CREATE', 'DOCUMENT_EDIT', 'DOCUMENT_DELETE', 'DOCUMENT_DOWNLOAD', 'DOCUMENT_SHARE', 'AI_SUMMARY', 'AI_KEYPOINT', 'AI_QA', 'AI_POLISH', 'TEMPLATE_READ', 'TEMPLATE_MANAGE', 'FORMAT_CHECK', 'FORMAT_CONVERT', 'COLLAB_EDIT', 'VERSION_RESTORE', 'AUDIT_READ', 'ASSIGNMENT_MANAGE', 'SUBMISSION_READ');
INSERT INTO edudoc_role_permission (role_id, permission_id) SELECT r.id, p.id FROM edudoc_role r CROSS JOIN edudoc_permission p WHERE r.role_code = 'OPS' AND p.permission_code IN ('DOCUMENT_READ', 'DOCUMENT_DOWNLOAD', 'TEMPLATE_READ', 'TEMPLATE_MANAGE', 'FORMAT_CHECK', 'ROLE_MANAGE', 'AUDIT_READ', 'ASSIGNMENT_MANAGE', 'SUBMISSION_READ');

COMMIT;
