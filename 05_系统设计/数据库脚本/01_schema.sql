-- CIDAS Oracle Database 26ai schema
-- All business tables include id, create_time and update_time.
SET DEFINE OFF;

-- 用户主表: 保存校内用户、统一身份认证映射和本地兼容账号。
CREATE TABLE edudoc_user (
    id RAW(16) DEFAULT SYS_GUID() NOT NULL,
    create_time TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    update_time TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    username VARCHAR2(64) NOT NULL,
    external_subject_id VARCHAR2(128),
    display_name VARCHAR2(100) NOT NULL,
    email VARCHAR2(200),
    department VARCHAR2(200),
    auth_source VARCHAR2(20) DEFAULT 'LOCAL' NOT NULL,
    password_hash VARCHAR2(255),
    account_status CHAR(1) DEFAULT 'A' NOT NULL,
    last_login_time TIMESTAMP(6) WITH TIME ZONE,
    remark VARCHAR2(500),
    CONSTRAINT pk_user PRIMARY KEY (id),
    CONSTRAINT uk_user_1 UNIQUE (username),
    CONSTRAINT uk_user_2 UNIQUE (external_subject_id),
    CONSTRAINT ck_user_auth CHECK (auth_source IN ('LOCAL','SSO')),
    CONSTRAINT ck_user_status CHECK (account_status IN ('A','I','L','D'))
);

COMMENT ON TABLE edudoc_user IS '保存校内用户、统一身份认证映射和本地兼容账号。';
COMMENT ON COLUMN edudoc_user.id IS '主键，数据库自动生成 GUID';
COMMENT ON COLUMN edudoc_user.create_time IS '创建时间，默认数据库当前时间';
COMMENT ON COLUMN edudoc_user.update_time IS '最后更新时间，由触发器维护';
COMMENT ON COLUMN edudoc_user.username IS '校内登录名';
COMMENT ON COLUMN edudoc_user.external_subject_id IS '统一身份认证 subject 标识';
COMMENT ON COLUMN edudoc_user.display_name IS '显示姓名';
COMMENT ON COLUMN edudoc_user.email IS '校内邮箱';
COMMENT ON COLUMN edudoc_user.department IS '所属院系或部门';
COMMENT ON COLUMN edudoc_user.auth_source IS '认证来源：LOCAL 或 SSO';
COMMENT ON COLUMN edudoc_user.password_hash IS '本地兼容账号密码哈希，SSO 用户为空';
COMMENT ON COLUMN edudoc_user.account_status IS '账号状态：A 正常、I 未激活、L 锁定、D 注销';
COMMENT ON COLUMN edudoc_user.last_login_time IS '最近登录时间';
COMMENT ON COLUMN edudoc_user.remark IS '管理备注';

-- 角色表: 保存学生、教师、运维人员及自定义角色。
CREATE TABLE edudoc_role (
    id RAW(16) DEFAULT SYS_GUID() NOT NULL,
    create_time TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    update_time TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    role_code VARCHAR2(40) NOT NULL,
    role_name VARCHAR2(80) NOT NULL,
    is_builtin CHAR(1) DEFAULT 'N' NOT NULL,
    description VARCHAR2(500),
    status CHAR(1) DEFAULT 'A' NOT NULL,
    CONSTRAINT pk_role PRIMARY KEY (id),
    CONSTRAINT uk_role_1 UNIQUE (role_code),
    CONSTRAINT ck_role_flag CHECK (is_builtin IN ('Y','N') AND status IN ('A','D'))
);

COMMENT ON TABLE edudoc_role IS '保存学生、教师、运维人员及自定义角色。';
COMMENT ON COLUMN edudoc_role.id IS '主键，数据库自动生成 GUID';
COMMENT ON COLUMN edudoc_role.create_time IS '创建时间，默认数据库当前时间';
COMMENT ON COLUMN edudoc_role.update_time IS '最后更新时间，由触发器维护';
COMMENT ON COLUMN edudoc_role.role_code IS '角色编码';
COMMENT ON COLUMN edudoc_role.role_name IS '角色名称';
COMMENT ON COLUMN edudoc_role.is_builtin IS '是否内置角色：Y/N';
COMMENT ON COLUMN edudoc_role.description IS '角色职责说明';
COMMENT ON COLUMN edudoc_role.status IS '状态：A 启用、D 停用';

-- 权限点表: 定义资源、动作与权限编码，用于 RBAC 授权。
CREATE TABLE edudoc_permission (
    id RAW(16) DEFAULT SYS_GUID() NOT NULL,
    create_time TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    update_time TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    permission_code VARCHAR2(80) NOT NULL,
    permission_name VARCHAR2(100) NOT NULL,
    resource_type VARCHAR2(40) NOT NULL,
    action_code VARCHAR2(40) NOT NULL,
    description VARCHAR2(500),
    CONSTRAINT pk_permission PRIMARY KEY (id),
    CONSTRAINT uk_permission_1 UNIQUE (permission_code)
);

COMMENT ON TABLE edudoc_permission IS '定义资源、动作与权限编码，用于 RBAC 授权。';
COMMENT ON COLUMN edudoc_permission.id IS '主键，数据库自动生成 GUID';
COMMENT ON COLUMN edudoc_permission.create_time IS '创建时间，默认数据库当前时间';
COMMENT ON COLUMN edudoc_permission.update_time IS '最后更新时间，由触发器维护';
COMMENT ON COLUMN edudoc_permission.permission_code IS '权限编码';
COMMENT ON COLUMN edudoc_permission.permission_name IS '权限名称';
COMMENT ON COLUMN edudoc_permission.resource_type IS '资源类型，如 DOCUMENT、TEMPLATE、AUDIT';
COMMENT ON COLUMN edudoc_permission.action_code IS '动作编码，如 READ、EDIT、DELETE、EXPORT';
COMMENT ON COLUMN edudoc_permission.description IS '权限说明';

-- 用户角色关联表: 记录用户与角色的生效关系及有效期。
CREATE TABLE edudoc_user_role (
    id RAW(16) DEFAULT SYS_GUID() NOT NULL,
    create_time TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    update_time TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    user_id RAW(16) NOT NULL,
    role_id RAW(16) NOT NULL,
    effective_from TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    effective_to TIMESTAMP(6) WITH TIME ZONE,
    granted_by RAW(16),
    CONSTRAINT pk_user_role PRIMARY KEY (id),
    CONSTRAINT fk_user_role_1 FOREIGN KEY (user_id) REFERENCES edudoc_user (id),
    CONSTRAINT fk_user_role_2 FOREIGN KEY (role_id) REFERENCES edudoc_role (id),
    CONSTRAINT fk_user_role_3 FOREIGN KEY (granted_by) REFERENCES edudoc_user (id)
);

COMMENT ON TABLE edudoc_user_role IS '记录用户与角色的生效关系及有效期。';
COMMENT ON COLUMN edudoc_user_role.id IS '主键，数据库自动生成 GUID';
COMMENT ON COLUMN edudoc_user_role.create_time IS '创建时间，默认数据库当前时间';
COMMENT ON COLUMN edudoc_user_role.update_time IS '最后更新时间，由触发器维护';
COMMENT ON COLUMN edudoc_user_role.user_id IS '用户 ID';
COMMENT ON COLUMN edudoc_user_role.role_id IS '角色 ID';
COMMENT ON COLUMN edudoc_user_role.effective_from IS '生效时间';
COMMENT ON COLUMN edudoc_user_role.effective_to IS '失效时间，空表示长期有效';
COMMENT ON COLUMN edudoc_user_role.granted_by IS '授权人 ID';

-- Oracle disallows UNIQUE constraints on TIMESTAMP WITH TIME ZONE columns,
-- so enforce the same rule on the UTC instant with a function-based index.
CREATE UNIQUE INDEX uk_user_role_1
ON edudoc_user_role (user_id, role_id, SYS_EXTRACT_UTC(effective_from));

-- 角色权限关联表: 记录角色拥有的权限点。
CREATE TABLE edudoc_role_permission (
    id RAW(16) DEFAULT SYS_GUID() NOT NULL,
    create_time TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    update_time TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    role_id RAW(16) NOT NULL,
    permission_id RAW(16) NOT NULL,
    CONSTRAINT pk_role_permission PRIMARY KEY (id),
    CONSTRAINT uk_role_permission_1 UNIQUE (role_id, permission_id),
    CONSTRAINT fk_role_permission_1 FOREIGN KEY (role_id) REFERENCES edudoc_role (id),
    CONSTRAINT fk_role_permission_2 FOREIGN KEY (permission_id) REFERENCES edudoc_permission (id)
);

COMMENT ON TABLE edudoc_role_permission IS '记录角色拥有的权限点。';
COMMENT ON COLUMN edudoc_role_permission.id IS '主键，数据库自动生成 GUID';
COMMENT ON COLUMN edudoc_role_permission.create_time IS '创建时间，默认数据库当前时间';
COMMENT ON COLUMN edudoc_role_permission.update_time IS '最后更新时间，由触发器维护';
COMMENT ON COLUMN edudoc_role_permission.role_id IS '角色 ID';
COMMENT ON COLUMN edudoc_role_permission.permission_id IS '权限 ID';

-- 课程表: 保存课程、学期和课程负责人信息。
CREATE TABLE edudoc_course (
    id RAW(16) DEFAULT SYS_GUID() NOT NULL,
    create_time TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    update_time TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    course_code VARCHAR2(40) NOT NULL,
    course_name VARCHAR2(200) NOT NULL,
    term_code VARCHAR2(30) NOT NULL,
    teacher_id RAW(16),
    department VARCHAR2(200),
    start_date DATE,
    end_date DATE,
    status CHAR(1) DEFAULT 'A' NOT NULL,
    CONSTRAINT pk_course PRIMARY KEY (id),
    CONSTRAINT uk_course_1 UNIQUE (course_code, term_code),
    CONSTRAINT fk_course_1 FOREIGN KEY (teacher_id) REFERENCES edudoc_user (id),
    CONSTRAINT ck_course_status CHECK (status IN ('A','C'))
);

COMMENT ON TABLE edudoc_course IS '保存课程、学期和课程负责人信息。';
COMMENT ON COLUMN edudoc_course.id IS '主键，数据库自动生成 GUID';
COMMENT ON COLUMN edudoc_course.create_time IS '创建时间，默认数据库当前时间';
COMMENT ON COLUMN edudoc_course.update_time IS '最后更新时间，由触发器维护';
COMMENT ON COLUMN edudoc_course.course_code IS '课程编码';
COMMENT ON COLUMN edudoc_course.course_name IS '课程名称';
COMMENT ON COLUMN edudoc_course.term_code IS '学期编码，如 2026-FALL';
COMMENT ON COLUMN edudoc_course.teacher_id IS '课程负责人 ID';
COMMENT ON COLUMN edudoc_course.department IS '开课院系';
COMMENT ON COLUMN edudoc_course.start_date IS '课程开始日期';
COMMENT ON COLUMN edudoc_course.end_date IS '课程结束日期';
COMMENT ON COLUMN edudoc_course.status IS '状态：A 启用、C 关闭';

-- 课程成员表: 维护课程中的学生、教师与助教成员关系。
CREATE TABLE edudoc_course_member (
    id RAW(16) DEFAULT SYS_GUID() NOT NULL,
    create_time TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    update_time TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    course_id RAW(16) NOT NULL,
    user_id RAW(16) NOT NULL,
    member_role VARCHAR2(20) NOT NULL,
    group_name VARCHAR2(50),
    status CHAR(1) DEFAULT 'A' NOT NULL,
    CONSTRAINT pk_course_member PRIMARY KEY (id),
    CONSTRAINT uk_course_member_1 UNIQUE (course_id, user_id),
    CONSTRAINT fk_course_member_1 FOREIGN KEY (course_id) REFERENCES edudoc_course (id),
    CONSTRAINT fk_course_member_2 FOREIGN KEY (user_id) REFERENCES edudoc_user (id),
    CONSTRAINT ck_member_role CHECK (member_role IN ('STUDENT','TEACHER','ASSISTANT')),
    CONSTRAINT ck_member_status CHECK (status IN ('A','R'))
);

COMMENT ON TABLE edudoc_course_member IS '维护课程中的学生、教师与助教成员关系。';
COMMENT ON COLUMN edudoc_course_member.id IS '主键，数据库自动生成 GUID';
COMMENT ON COLUMN edudoc_course_member.create_time IS '创建时间，默认数据库当前时间';
COMMENT ON COLUMN edudoc_course_member.update_time IS '最后更新时间，由触发器维护';
COMMENT ON COLUMN edudoc_course_member.course_id IS '课程 ID';
COMMENT ON COLUMN edudoc_course_member.user_id IS '用户 ID';
COMMENT ON COLUMN edudoc_course_member.member_role IS '课程内角色：STUDENT、TEACHER、ASSISTANT';
COMMENT ON COLUMN edudoc_course_member.group_name IS '课程组名（成员导入与课程组统计用；课程组实体表属切片 3/4）';
COMMENT ON COLUMN edudoc_course_member.status IS '状态：A 有效、R 移除';

-- OSS 对象登记表: 登记 OSS 中的原始文件、解析产物、导出件与协作快照。
CREATE TABLE edudoc_oss_object (
    id RAW(16) DEFAULT SYS_GUID() NOT NULL,
    create_time TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    update_time TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    bucket_name VARCHAR2(128) NOT NULL,
    region VARCHAR2(40) NOT NULL,
    object_key VARCHAR2(512) NOT NULL,
    object_version_id VARCHAR2(128),
    etag VARCHAR2(128),
    content_hash VARCHAR2(128) NOT NULL,
    size_bytes NUMBER(19) NOT NULL,
    mime_type VARCHAR2(120) NOT NULL,
    encryption_type VARCHAR2(40) NOT NULL,
    object_role VARCHAR2(40) NOT NULL,
    status CHAR(1) DEFAULT 'A' NOT NULL,
    retention_until TIMESTAMP(6) WITH TIME ZONE,
    CONSTRAINT pk_oss_object PRIMARY KEY (id),
    CONSTRAINT uk_oss_object_1 UNIQUE (bucket_name, object_key, object_version_id),
    CONSTRAINT ck_oss_role CHECK (object_role IN ('ORIGINAL','PARSED','PREVIEW','CONVERTED','EXPORT','SNAPSHOT')),
    CONSTRAINT ck_oss_status CHECK (status IN ('A','D','P'))
);

COMMENT ON TABLE edudoc_oss_object IS '登记 OSS 中的原始文件、解析产物、导出件与协作快照。';
COMMENT ON COLUMN edudoc_oss_object.id IS '主键，数据库自动生成 GUID';
COMMENT ON COLUMN edudoc_oss_object.create_time IS '创建时间，默认数据库当前时间';
COMMENT ON COLUMN edudoc_oss_object.update_time IS '最后更新时间，由触发器维护';
COMMENT ON COLUMN edudoc_oss_object.bucket_name IS 'OSS Bucket 名称';
COMMENT ON COLUMN edudoc_oss_object.region IS 'OSS 地域，固定中国大陆地域';
COMMENT ON COLUMN edudoc_oss_object.object_key IS 'OSS 对象键';
COMMENT ON COLUMN edudoc_oss_object.object_version_id IS 'OSS 版本标识';
COMMENT ON COLUMN edudoc_oss_object.etag IS 'OSS ETag';
COMMENT ON COLUMN edudoc_oss_object.content_hash IS 'SHA-256 内容摘要';
COMMENT ON COLUMN edudoc_oss_object.size_bytes IS '对象字节数';
COMMENT ON COLUMN edudoc_oss_object.mime_type IS 'MIME 类型';
COMMENT ON COLUMN edudoc_oss_object.encryption_type IS '加密方式：SSE-KMS、SSE-OSS';
COMMENT ON COLUMN edudoc_oss_object.object_role IS '对象用途：ORIGINAL、PARSED、PREVIEW、CONVERTED、EXPORT、SNAPSHOT';
COMMENT ON COLUMN edudoc_oss_object.status IS '状态：A 可用、D 待清理、P 待保护';
COMMENT ON COLUMN edudoc_oss_object.retention_until IS '对象保留截止时间';

-- 课程模板表: 保存课程模板的逻辑主记录。
CREATE TABLE edudoc_template (
    id RAW(16) DEFAULT SYS_GUID() NOT NULL,
    create_time TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    update_time TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    course_id RAW(16),
    template_name VARCHAR2(200) NOT NULL,
    description VARCHAR2(1000),
    owner_id RAW(16) NOT NULL,
    current_version_id RAW(16),
    status CHAR(1) DEFAULT 'D' NOT NULL,
    CONSTRAINT pk_template PRIMARY KEY (id),
    CONSTRAINT uk_template_1 UNIQUE (course_id, template_name),
    CONSTRAINT fk_template_1 FOREIGN KEY (course_id) REFERENCES edudoc_course (id),
    CONSTRAINT fk_template_2 FOREIGN KEY (owner_id) REFERENCES edudoc_user (id),
    CONSTRAINT ck_template_status CHECK (status IN ('D','P','A'))
);

COMMENT ON TABLE edudoc_template IS '保存课程模板的逻辑主记录。';
COMMENT ON COLUMN edudoc_template.id IS '主键，数据库自动生成 GUID';
COMMENT ON COLUMN edudoc_template.create_time IS '创建时间，默认数据库当前时间';
COMMENT ON COLUMN edudoc_template.update_time IS '最后更新时间，由触发器维护';
COMMENT ON COLUMN edudoc_template.course_id IS '所属课程 ID';
COMMENT ON COLUMN edudoc_template.template_name IS '模板名称';
COMMENT ON COLUMN edudoc_template.description IS '模板说明';
COMMENT ON COLUMN edudoc_template.owner_id IS '模板维护人 ID';
COMMENT ON COLUMN edudoc_template.current_version_id IS '当前发布版本 ID';
COMMENT ON COLUMN edudoc_template.status IS '状态：D 草稿、P 已发布、A 停用';

-- 模板版本表: 保存模板的版本化规则集合与发布信息。
CREATE TABLE edudoc_template_version (
    id RAW(16) DEFAULT SYS_GUID() NOT NULL,
    create_time TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    update_time TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    template_id RAW(16) NOT NULL,
    version_no NUMBER(10) NOT NULL,
    source_object_id RAW(16),
    validation_status VARCHAR2(20) DEFAULT 'PENDING' NOT NULL,
    published_by RAW(16),
    published_at TIMESTAMP(6) WITH TIME ZONE,
    change_summary VARCHAR2(1000),
    parse_error VARCHAR2(1000),
    CONSTRAINT pk_template_version PRIMARY KEY (id),
    CONSTRAINT uk_template_version_1 UNIQUE (template_id, version_no),
    CONSTRAINT fk_template_version_1 FOREIGN KEY (template_id) REFERENCES edudoc_template (id),
    CONSTRAINT fk_template_version_2 FOREIGN KEY (source_object_id) REFERENCES edudoc_oss_object (id),
    CONSTRAINT fk_template_version_3 FOREIGN KEY (published_by) REFERENCES edudoc_user (id),
    CONSTRAINT ck_tplver_status CHECK (validation_status IN ('PENDING','VALID','INVALID'))
);

COMMENT ON TABLE edudoc_template_version IS '保存模板的版本化规则集合与发布信息。';
COMMENT ON COLUMN edudoc_template_version.id IS '主键，数据库自动生成 GUID';
COMMENT ON COLUMN edudoc_template_version.create_time IS '创建时间，默认数据库当前时间';
COMMENT ON COLUMN edudoc_template_version.update_time IS '最后更新时间，由触发器维护';
COMMENT ON COLUMN edudoc_template_version.template_id IS '模板 ID';
COMMENT ON COLUMN edudoc_template_version.version_no IS '版本号';
COMMENT ON COLUMN edudoc_template_version.source_object_id IS '模板源文件 OSS 对象 ID';
COMMENT ON COLUMN edudoc_template_version.validation_status IS '校验状态：PENDING、VALID、INVALID';
COMMENT ON COLUMN edudoc_template_version.published_by IS '发布人 ID';
COMMENT ON COLUMN edudoc_template_version.published_at IS '发布时间';
COMMENT ON COLUMN edudoc_template_version.change_summary IS '版本变更说明';
COMMENT ON COLUMN edudoc_template_version.parse_error IS '解析失败原因（1107 阶段审查 P2-2 最小迁移；validation_status=INVALID 时解释原因，重开编辑页仍可见）';

-- 格式规则表: 保存模板版本中的可执行格式检查规则。
CREATE TABLE edudoc_format_rule (
    id RAW(16) DEFAULT SYS_GUID() NOT NULL,
    create_time TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    update_time TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    template_version_id RAW(16) NOT NULL,
    rule_type VARCHAR2(40) NOT NULL,
    target_selector VARCHAR2(300) NOT NULL,
    severity VARCHAR2(20) NOT NULL,
    auto_fixable CHAR(1) NOT NULL,
    rule_config_json JSON NOT NULL,
    rule_order NUMBER(6) DEFAULT 0 NOT NULL,
    CONSTRAINT pk_format_rule PRIMARY KEY (id),
    CONSTRAINT fk_format_rule_1 FOREIGN KEY (template_version_id) REFERENCES edudoc_template_version (id),
    CONSTRAINT ck_rule_severity CHECK (severity IN ('BLOCKER','WARNING','INFO')),
    CONSTRAINT ck_rule_fixable CHECK (auto_fixable IN ('Y','N'))
);

COMMENT ON TABLE edudoc_format_rule IS '保存模板版本中的可执行格式检查规则。';
COMMENT ON COLUMN edudoc_format_rule.id IS '主键，数据库自动生成 GUID';
COMMENT ON COLUMN edudoc_format_rule.create_time IS '创建时间，默认数据库当前时间';
COMMENT ON COLUMN edudoc_format_rule.update_time IS '最后更新时间，由触发器维护';
COMMENT ON COLUMN edudoc_format_rule.template_version_id IS '模板版本 ID';
COMMENT ON COLUMN edudoc_format_rule.rule_type IS '规则类型：FONT、SIZE、LINE_SPACING、MARGIN、CAPTION、REFERENCE、CODE';
COMMENT ON COLUMN edudoc_format_rule.target_selector IS '规则作用对象，如 HEADING_1、BODY、FIGURE_CAPTION';
COMMENT ON COLUMN edudoc_format_rule.severity IS '严重程度：BLOCKER、WARNING、INFO';
COMMENT ON COLUMN edudoc_format_rule.auto_fixable IS '是否可自动修复：Y/N';
COMMENT ON COLUMN edudoc_format_rule.rule_config_json IS '期望值、容差和修复参数';
COMMENT ON COLUMN edudoc_format_rule.rule_order IS '规则执行顺序';

-- 文档主表: 保存文档身份、归属、当前版本、状态与软删除信息。
CREATE TABLE edudoc_document (
    id RAW(16) DEFAULT SYS_GUID() NOT NULL,
    create_time TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    update_time TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    course_id RAW(16),
    owner_id RAW(16) NOT NULL,
    title VARCHAR2(200) NOT NULL,
    doc_type VARCHAR2(20) NOT NULL,
    status VARCHAR2(20) NOT NULL,
    template_version_id RAW(16),
    current_version_id RAW(16),
    deleted_at TIMESTAMP(6) WITH TIME ZONE,
    purge_at TIMESTAMP(6) WITH TIME ZONE,
    cover_object_id RAW(16),
    CONSTRAINT pk_document PRIMARY KEY (id),
    CONSTRAINT uk_document_1 UNIQUE (owner_id, title, course_id),
    CONSTRAINT fk_document_1 FOREIGN KEY (course_id) REFERENCES edudoc_course (id),
    CONSTRAINT fk_document_2 FOREIGN KEY (owner_id) REFERENCES edudoc_user (id),
    CONSTRAINT fk_document_3 FOREIGN KEY (template_version_id) REFERENCES edudoc_template_version (id),
    CONSTRAINT fk_document_4 FOREIGN KEY (cover_object_id) REFERENCES edudoc_oss_object (id),
    CONSTRAINT ck_doc_type CHECK (doc_type IN ('COURSEWARE','HOMEWORK','THESIS','REPORT')),
    CONSTRAINT ck_doc_status CHECK (status IN ('UPLOADING','PARSING','READY','FAILED','SUBMITTED','ARCHIVED','DELETED'))
);

COMMENT ON TABLE edudoc_document IS '保存文档身份、归属、当前版本、状态与软删除信息。';
COMMENT ON COLUMN edudoc_document.id IS '主键，数据库自动生成 GUID';
COMMENT ON COLUMN edudoc_document.create_time IS '创建时间，默认数据库当前时间';
COMMENT ON COLUMN edudoc_document.update_time IS '最后更新时间，由触发器维护';
COMMENT ON COLUMN edudoc_document.course_id IS '所属课程 ID';
COMMENT ON COLUMN edudoc_document.owner_id IS '文档所有者 ID';
COMMENT ON COLUMN edudoc_document.title IS '文档标题';
COMMENT ON COLUMN edudoc_document.doc_type IS '文档类型：COURSEWARE、HOMEWORK、THESIS、REPORT';
COMMENT ON COLUMN edudoc_document.status IS '状态：UPLOADING、PARSING、READY、FAILED、SUBMITTED、ARCHIVED、DELETED';
COMMENT ON COLUMN edudoc_document.template_version_id IS '绑定的课程模板版本 ID';
COMMENT ON COLUMN edudoc_document.current_version_id IS '当前文档版本 ID';
COMMENT ON COLUMN edudoc_document.deleted_at IS '软删除时间';
COMMENT ON COLUMN edudoc_document.purge_at IS '回收站物理清理时间';
COMMENT ON COLUMN edudoc_document.cover_object_id IS '缩略图或封面对应 OSS 对象 ID';

-- 文档版本表: 记录每次上传、编辑、协作、恢复或提交形成的不可变版本。
CREATE TABLE edudoc_document_version (
    id RAW(16) DEFAULT SYS_GUID() NOT NULL,
    create_time TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    update_time TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    document_id RAW(16) NOT NULL,
    version_no NUMBER(10) NOT NULL,
    parent_version_id RAW(16),
    version_type VARCHAR2(30) NOT NULL,
    source_object_id RAW(16) NOT NULL,
    editor_id RAW(16) NOT NULL,
    change_summary VARCHAR2(1000),
    content_hash VARCHAR2(128) NOT NULL,
    version_status CHAR(1) DEFAULT 'A' NOT NULL,
    CONSTRAINT pk_document_version PRIMARY KEY (id),
    CONSTRAINT uk_document_version_1 UNIQUE (document_id, version_no),
    CONSTRAINT fk_document_version_1 FOREIGN KEY (document_id) REFERENCES edudoc_document (id),
    CONSTRAINT fk_document_version_2 FOREIGN KEY (parent_version_id) REFERENCES edudoc_document_version (id),
    CONSTRAINT fk_document_version_3 FOREIGN KEY (source_object_id) REFERENCES edudoc_oss_object (id),
    CONSTRAINT fk_document_version_4 FOREIGN KEY (editor_id) REFERENCES edudoc_user (id),
    CONSTRAINT ck_docver_type CHECK (version_type IN ('UPLOAD','EDIT','COLLABORATION','RESTORE','SUBMISSION')),
    CONSTRAINT ck_docver_status CHECK (version_status IN ('A','F'))
);

COMMENT ON TABLE edudoc_document_version IS '记录每次上传、编辑、协作、恢复或提交形成的不可变版本。';
COMMENT ON COLUMN edudoc_document_version.id IS '主键，数据库自动生成 GUID';
COMMENT ON COLUMN edudoc_document_version.create_time IS '创建时间，默认数据库当前时间';
COMMENT ON COLUMN edudoc_document_version.update_time IS '最后更新时间，由触发器维护';
COMMENT ON COLUMN edudoc_document_version.document_id IS '文档 ID';
COMMENT ON COLUMN edudoc_document_version.version_no IS '版本序号';
COMMENT ON COLUMN edudoc_document_version.parent_version_id IS '父版本 ID';
COMMENT ON COLUMN edudoc_document_version.version_type IS '版本来源：UPLOAD、EDIT、COLLABORATION、RESTORE、SUBMISSION';
COMMENT ON COLUMN edudoc_document_version.source_object_id IS '该版本主文件 OSS 对象 ID';
COMMENT ON COLUMN edudoc_document_version.editor_id IS '生成版本的操作用户 ID';
COMMENT ON COLUMN edudoc_document_version.change_summary IS '版本变更说明';
COMMENT ON COLUMN edudoc_document_version.content_hash IS '版本内容摘要';
COMMENT ON COLUMN edudoc_document_version.version_status IS '状态：A 有效、F 归档';

-- 派生文件表: 将解析、预览、转换和导出产物与文档版本关联。
CREATE TABLE edudoc_derived_file (
    id RAW(16) DEFAULT SYS_GUID() NOT NULL,
    create_time TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    update_time TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    document_version_id RAW(16) NOT NULL,
    object_id RAW(16) NOT NULL,
    file_role VARCHAR2(30) NOT NULL,
    format_code VARCHAR2(20) NOT NULL,
    status VARCHAR2(20) DEFAULT 'READY' NOT NULL,
    CONSTRAINT pk_derived_file PRIMARY KEY (id),
    CONSTRAINT uk_derived_file_1 UNIQUE (document_version_id, file_role, format_code),
    CONSTRAINT fk_derived_file_1 FOREIGN KEY (document_version_id) REFERENCES edudoc_document_version (id),
    CONSTRAINT fk_derived_file_2 FOREIGN KEY (object_id) REFERENCES edudoc_oss_object (id),
    CONSTRAINT ck_derived_status CHECK (status IN ('READY','FAILED','STALE'))
);

COMMENT ON TABLE edudoc_derived_file IS '将解析、预览、转换和导出产物与文档版本关联。';
COMMENT ON COLUMN edudoc_derived_file.id IS '主键，数据库自动生成 GUID';
COMMENT ON COLUMN edudoc_derived_file.create_time IS '创建时间，默认数据库当前时间';
COMMENT ON COLUMN edudoc_derived_file.update_time IS '最后更新时间，由触发器维护';
COMMENT ON COLUMN edudoc_derived_file.document_version_id IS '文档版本 ID';
COMMENT ON COLUMN edudoc_derived_file.object_id IS 'OSS 对象 ID';
COMMENT ON COLUMN edudoc_derived_file.file_role IS '用途：PARSED_HTML、PREVIEW_PDF、THUMBNAIL、CONVERTED、EXPORT';
COMMENT ON COLUMN edudoc_derived_file.format_code IS '文件格式，如 DOCX、PDF、HTML';
COMMENT ON COLUMN edudoc_derived_file.status IS '状态：READY、FAILED、STALE';

-- 文档结构元素表: 保存标题、图表、代码块、公式等结构元素及编号索引。
CREATE TABLE edudoc_document_element (
    id RAW(16) DEFAULT SYS_GUID() NOT NULL,
    create_time TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    update_time TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    document_version_id RAW(16) NOT NULL,
    element_key VARCHAR2(120) NOT NULL,
    element_type VARCHAR2(20) NOT NULL,
    chapter_no VARCHAR2(20),
    sequence_no NUMBER(8),
    display_label VARCHAR2(80),
    caption_text VARCHAR2(1000),
    location_json JSON,
    CONSTRAINT pk_document_element PRIMARY KEY (id),
    CONSTRAINT uk_document_element_1 UNIQUE (document_version_id, element_key),
    CONSTRAINT fk_document_element_1 FOREIGN KEY (document_version_id) REFERENCES edudoc_document_version (id),
    CONSTRAINT ck_element_type CHECK (element_type IN ('HEADING','FIGURE','TABLE','CODE','EQUATION'))
);

COMMENT ON TABLE edudoc_document_element IS '保存标题、图表、代码块、公式等结构元素及编号索引。';
COMMENT ON COLUMN edudoc_document_element.id IS '主键，数据库自动生成 GUID';
COMMENT ON COLUMN edudoc_document_element.create_time IS '创建时间，默认数据库当前时间';
COMMENT ON COLUMN edudoc_document_element.update_time IS '最后更新时间，由触发器维护';
COMMENT ON COLUMN edudoc_document_element.document_version_id IS '文档版本 ID';
COMMENT ON COLUMN edudoc_document_element.element_key IS '版本内稳定元素键';
COMMENT ON COLUMN edudoc_document_element.element_type IS '元素类型：HEADING、FIGURE、TABLE、CODE、EQUATION';
COMMENT ON COLUMN edudoc_document_element.chapter_no IS '所属章节号';
COMMENT ON COLUMN edudoc_document_element.sequence_no IS '章内序号';
COMMENT ON COLUMN edudoc_document_element.display_label IS '显示编号，如 图 2-1';
COMMENT ON COLUMN edudoc_document_element.caption_text IS '题注或标题';
COMMENT ON COLUMN edudoc_document_element.location_json IS '页码、段落和字符位置';

-- 文档分块表: 保存解析后的文本分块、章节路径与原文定位信息。
CREATE TABLE edudoc_document_chunk (
    id RAW(16) DEFAULT SYS_GUID() NOT NULL,
    create_time TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    update_time TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    document_version_id RAW(16) NOT NULL,
    chunk_no NUMBER(12) NOT NULL,
    heading_path VARCHAR2(1000),
    page_no NUMBER(8),
    char_start NUMBER(12),
    char_end NUMBER(12),
    content_text CLOB NOT NULL,
    token_count NUMBER(8) NOT NULL,
    content_hash VARCHAR2(128) NOT NULL,
    index_status VARCHAR2(20) DEFAULT 'PENDING' NOT NULL,
    CONSTRAINT pk_document_chunk PRIMARY KEY (id),
    CONSTRAINT uk_document_chunk_1 UNIQUE (document_version_id, chunk_no),
    CONSTRAINT fk_document_chunk_1 FOREIGN KEY (document_version_id) REFERENCES edudoc_document_version (id),
    CONSTRAINT ck_chunk_index CHECK (index_status IN ('PENDING','READY','FAILED'))
);

COMMENT ON TABLE edudoc_document_chunk IS '保存解析后的文本分块、章节路径与原文定位信息。';
COMMENT ON COLUMN edudoc_document_chunk.id IS '主键，数据库自动生成 GUID';
COMMENT ON COLUMN edudoc_document_chunk.create_time IS '创建时间，默认数据库当前时间';
COMMENT ON COLUMN edudoc_document_chunk.update_time IS '最后更新时间，由触发器维护';
COMMENT ON COLUMN edudoc_document_chunk.document_version_id IS '文档版本 ID';
COMMENT ON COLUMN edudoc_document_chunk.chunk_no IS '分块序号';
COMMENT ON COLUMN edudoc_document_chunk.heading_path IS '章节路径';
COMMENT ON COLUMN edudoc_document_chunk.page_no IS '起始页码';
COMMENT ON COLUMN edudoc_document_chunk.char_start IS '原文起始字符位置';
COMMENT ON COLUMN edudoc_document_chunk.char_end IS '原文结束字符位置';
COMMENT ON COLUMN edudoc_document_chunk.content_text IS '分块文本';
COMMENT ON COLUMN edudoc_document_chunk.token_count IS '估算 token 数';
COMMENT ON COLUMN edudoc_document_chunk.content_hash IS '分块内容摘要';
COMMENT ON COLUMN edudoc_document_chunk.index_status IS '索引状态：PENDING、READY、FAILED';

-- 模型配置表: 保存模型类型、服务引用和向量维度，不保存明文密钥。
CREATE TABLE edudoc_ai_model_profile (
    id RAW(16) DEFAULT SYS_GUID() NOT NULL,
    create_time TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    update_time TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    profile_code VARCHAR2(60) NOT NULL,
    provider_type VARCHAR2(40) NOT NULL,
    model_name VARCHAR2(120) NOT NULL,
    endpoint_config_key VARCHAR2(120) NOT NULL,
    embedding_dimension NUMBER(6),
    metric_type VARCHAR2(20),
    status CHAR(1) DEFAULT 'A' NOT NULL,
    profile_json JSON,
    CONSTRAINT pk_ai_model_profile PRIMARY KEY (id),
    CONSTRAINT uk_ai_model_profile_1 UNIQUE (profile_code),
    CONSTRAINT ck_model_status CHECK (status IN ('A','D'))
);

COMMENT ON TABLE edudoc_ai_model_profile IS '保存模型类型、服务引用和向量维度，不保存明文密钥。';
COMMENT ON COLUMN edudoc_ai_model_profile.id IS '主键，数据库自动生成 GUID';
COMMENT ON COLUMN edudoc_ai_model_profile.create_time IS '创建时间，默认数据库当前时间';
COMMENT ON COLUMN edudoc_ai_model_profile.update_time IS '最后更新时间，由触发器维护';
COMMENT ON COLUMN edudoc_ai_model_profile.profile_code IS '配置编码';
COMMENT ON COLUMN edudoc_ai_model_profile.provider_type IS '提供方类型：LLM、EMBEDDING、RERANK';
COMMENT ON COLUMN edudoc_ai_model_profile.model_name IS '模型名称';
COMMENT ON COLUMN edudoc_ai_model_profile.endpoint_config_key IS '服务器配置中心中的端点和密钥键';
COMMENT ON COLUMN edudoc_ai_model_profile.embedding_dimension IS '向量维度';
COMMENT ON COLUMN edudoc_ai_model_profile.metric_type IS '向量距离：COSINE、EUCLIDEAN、DOT';
COMMENT ON COLUMN edudoc_ai_model_profile.status IS '状态：A 启用、D 停用';
COMMENT ON COLUMN edudoc_ai_model_profile.profile_json IS '温度、上下文长度等非敏感参数';

-- 分块向量表: 保存分块在指定模型配置下的向量。
CREATE TABLE edudoc_chunk_embedding (
    id RAW(16) DEFAULT SYS_GUID() NOT NULL,
    create_time TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    update_time TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    chunk_id RAW(16) NOT NULL,
    model_profile_id RAW(16) NOT NULL,
    embedding VECTOR(1024, FLOAT32) NOT NULL,
    embedding_status VARCHAR2(20) DEFAULT 'READY' NOT NULL,
    CONSTRAINT pk_chunk_embedding PRIMARY KEY (id),
    CONSTRAINT uk_chunk_embedding_1 UNIQUE (chunk_id, model_profile_id),
    CONSTRAINT fk_chunk_embedding_1 FOREIGN KEY (chunk_id) REFERENCES edudoc_document_chunk (id),
    CONSTRAINT fk_chunk_embedding_2 FOREIGN KEY (model_profile_id) REFERENCES edudoc_ai_model_profile (id),
    CONSTRAINT ck_embedding_status CHECK (embedding_status IN ('READY','STALE','FAILED'))
);

COMMENT ON TABLE edudoc_chunk_embedding IS '保存分块在指定模型配置下的向量。';
COMMENT ON COLUMN edudoc_chunk_embedding.id IS '主键，数据库自动生成 GUID';
COMMENT ON COLUMN edudoc_chunk_embedding.create_time IS '创建时间，默认数据库当前时间';
COMMENT ON COLUMN edudoc_chunk_embedding.update_time IS '最后更新时间，由触发器维护';
COMMENT ON COLUMN edudoc_chunk_embedding.chunk_id IS '分块 ID';
COMMENT ON COLUMN edudoc_chunk_embedding.model_profile_id IS '模型配置 ID';
COMMENT ON COLUMN edudoc_chunk_embedding.embedding IS '固定维度文本向量';
COMMENT ON COLUMN edudoc_chunk_embedding.embedding_status IS '状态：READY、STALE、FAILED';

-- 共享设置表: 定义文档共享范围、有效期链接和下载策略。
CREATE TABLE edudoc_document_share (
    id RAW(16) DEFAULT SYS_GUID() NOT NULL,
    create_time TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    update_time TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    document_id RAW(16) NOT NULL,
    owner_id RAW(16) NOT NULL,
    share_scope VARCHAR2(20) NOT NULL,
    link_token_hash VARCHAR2(128),
    expires_at TIMESTAMP(6) WITH TIME ZONE,
    max_download_count NUMBER(10),
    download_count NUMBER(10) DEFAULT 0 NOT NULL,
    status CHAR(1) DEFAULT 'A' NOT NULL,
    CONSTRAINT pk_document_share PRIMARY KEY (id),
    CONSTRAINT uk_document_share_1 UNIQUE (link_token_hash),
    CONSTRAINT fk_document_share_1 FOREIGN KEY (document_id) REFERENCES edudoc_document (id),
    CONSTRAINT fk_document_share_2 FOREIGN KEY (owner_id) REFERENCES edudoc_user (id),
    CONSTRAINT ck_share_scope CHECK (share_scope IN ('SELF','MEMBERS','GROUP','COURSE','LINK')),
    CONSTRAINT ck_share_status CHECK (status IN ('A','R','E'))
);

COMMENT ON TABLE edudoc_document_share IS '定义文档共享范围、有效期链接和下载策略。';
COMMENT ON COLUMN edudoc_document_share.id IS '主键，数据库自动生成 GUID';
COMMENT ON COLUMN edudoc_document_share.create_time IS '创建时间，默认数据库当前时间';
COMMENT ON COLUMN edudoc_document_share.update_time IS '最后更新时间，由触发器维护';
COMMENT ON COLUMN edudoc_document_share.document_id IS '文档 ID';
COMMENT ON COLUMN edudoc_document_share.owner_id IS '共享发起人 ID';
COMMENT ON COLUMN edudoc_document_share.share_scope IS '范围：SELF、MEMBERS、GROUP、COURSE、LINK';
COMMENT ON COLUMN edudoc_document_share.link_token_hash IS '共享链接令牌哈希';
COMMENT ON COLUMN edudoc_document_share.expires_at IS '链接过期时间';
COMMENT ON COLUMN edudoc_document_share.max_download_count IS '最大下载次数';
COMMENT ON COLUMN edudoc_document_share.download_count IS '已下载次数';
COMMENT ON COLUMN edudoc_document_share.status IS '状态：A 有效、R 撤销、E 过期';

-- 文档 ACL 表: 记录用户或课程等主体的细粒度文档权限。
CREATE TABLE edudoc_share_grant (
    id RAW(16) DEFAULT SYS_GUID() NOT NULL,
    create_time TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    update_time TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    share_id RAW(16) NOT NULL,
    principal_type VARCHAR2(20) NOT NULL,
    principal_ref VARCHAR2(128) NOT NULL,
    permission_level VARCHAR2(20) NOT NULL,
    valid_from TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    valid_to TIMESTAMP(6) WITH TIME ZONE,
    granted_by RAW(16) NOT NULL,
    CONSTRAINT pk_share_grant PRIMARY KEY (id),
    CONSTRAINT uk_share_grant_1 UNIQUE (share_id, principal_type, principal_ref, permission_level),
    CONSTRAINT fk_share_grant_1 FOREIGN KEY (share_id) REFERENCES edudoc_document_share (id),
    CONSTRAINT fk_share_grant_2 FOREIGN KEY (granted_by) REFERENCES edudoc_user (id),
    CONSTRAINT ck_grant_principal CHECK (principal_type IN ('USER','COURSE','GROUP')),
    CONSTRAINT ck_grant_level CHECK (permission_level IN ('VIEW','COMMENT','EDIT','DOWNLOAD'))
);

COMMENT ON TABLE edudoc_share_grant IS '记录用户或课程等主体的细粒度文档权限。';
COMMENT ON COLUMN edudoc_share_grant.id IS '主键，数据库自动生成 GUID';
COMMENT ON COLUMN edudoc_share_grant.create_time IS '创建时间，默认数据库当前时间';
COMMENT ON COLUMN edudoc_share_grant.update_time IS '最后更新时间，由触发器维护';
COMMENT ON COLUMN edudoc_share_grant.share_id IS '共享设置 ID';
COMMENT ON COLUMN edudoc_share_grant.principal_type IS '授权主体类型：USER、COURSE、GROUP';
COMMENT ON COLUMN edudoc_share_grant.principal_ref IS '主体引用值';
COMMENT ON COLUMN edudoc_share_grant.permission_level IS '权限：VIEW、COMMENT、EDIT、DOWNLOAD';
COMMENT ON COLUMN edudoc_share_grant.valid_from IS '生效时间';
COMMENT ON COLUMN edudoc_share_grant.valid_to IS '失效时间';
COMMENT ON COLUMN edudoc_share_grant.granted_by IS '授权人 ID';

-- 格式校验任务表: 记录文档与模板版本之间的格式校验任务与汇总结果。
CREATE TABLE edudoc_format_check (
    id RAW(16) DEFAULT SYS_GUID() NOT NULL,
    create_time TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    update_time TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    document_id RAW(16) NOT NULL,
    document_version_id RAW(16) NOT NULL,
    template_version_id RAW(16) NOT NULL,
    requested_by RAW(16) NOT NULL,
    status VARCHAR2(20) NOT NULL,
    total_issue_count NUMBER(10) DEFAULT 0 NOT NULL,
    blocking_issue_count NUMBER(10) DEFAULT 0 NOT NULL,
    started_at TIMESTAMP(6) WITH TIME ZONE,
    finished_at TIMESTAMP(6) WITH TIME ZONE,
    result_summary VARCHAR2(2000),
    CONSTRAINT pk_format_check PRIMARY KEY (id),
    CONSTRAINT fk_format_check_1 FOREIGN KEY (document_id) REFERENCES edudoc_document (id),
    CONSTRAINT fk_format_check_2 FOREIGN KEY (document_version_id) REFERENCES edudoc_document_version (id),
    CONSTRAINT fk_format_check_3 FOREIGN KEY (template_version_id) REFERENCES edudoc_template_version (id),
    CONSTRAINT fk_format_check_4 FOREIGN KEY (requested_by) REFERENCES edudoc_user (id),
    CONSTRAINT ck_format_check_status CHECK (status IN ('QUEUED','RUNNING','PASSED','FAILED','ERROR'))
);

COMMENT ON TABLE edudoc_format_check IS '记录文档与模板版本之间的格式校验任务与汇总结果。';
COMMENT ON COLUMN edudoc_format_check.id IS '主键，数据库自动生成 GUID';
COMMENT ON COLUMN edudoc_format_check.create_time IS '创建时间，默认数据库当前时间';
COMMENT ON COLUMN edudoc_format_check.update_time IS '最后更新时间，由触发器维护';
COMMENT ON COLUMN edudoc_format_check.document_id IS '文档 ID';
COMMENT ON COLUMN edudoc_format_check.document_version_id IS '文档版本 ID';
COMMENT ON COLUMN edudoc_format_check.template_version_id IS '模板版本 ID';
COMMENT ON COLUMN edudoc_format_check.requested_by IS '发起人 ID';
COMMENT ON COLUMN edudoc_format_check.status IS '状态：QUEUED、RUNNING、PASSED、FAILED、ERROR';
COMMENT ON COLUMN edudoc_format_check.total_issue_count IS '问题总数';
COMMENT ON COLUMN edudoc_format_check.blocking_issue_count IS '阻断问题数';
COMMENT ON COLUMN edudoc_format_check.started_at IS '开始时间';
COMMENT ON COLUMN edudoc_format_check.finished_at IS '结束时间';
COMMENT ON COLUMN edudoc_format_check.result_summary IS '校验结论摘要';

-- 格式问题表: 保存格式校验发现的逐项问题和修复状态。
CREATE TABLE edudoc_format_issue (
    id RAW(16) DEFAULT SYS_GUID() NOT NULL,
    create_time TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    update_time TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    format_check_id RAW(16) NOT NULL,
    rule_id RAW(16),
    issue_type VARCHAR2(60) NOT NULL,
    location_type VARCHAR2(20) NOT NULL,
    location_ref JSON,
    expected_value VARCHAR2(1000),
    actual_value VARCHAR2(1000),
    severity VARCHAR2(20) NOT NULL,
    auto_fixable CHAR(1) NOT NULL,
    fix_status VARCHAR2(20) DEFAULT 'OPEN' NOT NULL,
    guidance VARCHAR2(2000),
    CONSTRAINT pk_format_issue PRIMARY KEY (id),
    CONSTRAINT fk_format_issue_1 FOREIGN KEY (format_check_id) REFERENCES edudoc_format_check (id),
    CONSTRAINT fk_format_issue_2 FOREIGN KEY (rule_id) REFERENCES edudoc_format_rule (id),
    CONSTRAINT ck_issue_severity CHECK (severity IN ('BLOCKER','WARNING','INFO')),
    CONSTRAINT ck_issue_fix CHECK (fix_status IN ('OPEN','FIXED','IGNORED') AND auto_fixable IN ('Y','N'))
);

COMMENT ON TABLE edudoc_format_issue IS '保存格式校验发现的逐项问题和修复状态。';
COMMENT ON COLUMN edudoc_format_issue.id IS '主键，数据库自动生成 GUID';
COMMENT ON COLUMN edudoc_format_issue.create_time IS '创建时间，默认数据库当前时间';
COMMENT ON COLUMN edudoc_format_issue.update_time IS '最后更新时间，由触发器维护';
COMMENT ON COLUMN edudoc_format_issue.format_check_id IS '格式校验任务 ID';
COMMENT ON COLUMN edudoc_format_issue.rule_id IS '命中的规则 ID';
COMMENT ON COLUMN edudoc_format_issue.issue_type IS '问题类型';
COMMENT ON COLUMN edudoc_format_issue.location_type IS '位置类型：PAGE、PARAGRAPH、ELEMENT、REFERENCE';
COMMENT ON COLUMN edudoc_format_issue.location_ref IS '页码、段落、元素键和字符范围';
COMMENT ON COLUMN edudoc_format_issue.expected_value IS '期望值';
COMMENT ON COLUMN edudoc_format_issue.actual_value IS '当前值';
COMMENT ON COLUMN edudoc_format_issue.severity IS '严重程度：BLOCKER、WARNING、INFO';
COMMENT ON COLUMN edudoc_format_issue.auto_fixable IS '是否可自动修复：Y/N';
COMMENT ON COLUMN edudoc_format_issue.fix_status IS '状态：OPEN、FIXED、IGNORED';
COMMENT ON COLUMN edudoc_format_issue.guidance IS '人工修改指引';

-- 格式转换任务表: 记录 Word/PDF 转换任务、输入输出对象与失败原因。
CREATE TABLE edudoc_conversion_task (
    id RAW(16) DEFAULT SYS_GUID() NOT NULL,
    create_time TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    update_time TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    document_id RAW(16) NOT NULL,
    source_version_id RAW(16) NOT NULL,
    source_object_id RAW(16) NOT NULL,
    target_format VARCHAR2(20) NOT NULL,
    status VARCHAR2(20) NOT NULL,
    progress NUMBER(5) DEFAULT 0 NOT NULL,
    result_object_id RAW(16),
    failure_code VARCHAR2(60),
    failure_message VARCHAR2(1000),
    requested_by RAW(16) NOT NULL,
    retry_count NUMBER(5) DEFAULT 0 NOT NULL,
    started_at TIMESTAMP(6) WITH TIME ZONE,
    finished_at TIMESTAMP(6) WITH TIME ZONE,
    CONSTRAINT pk_conversion_task PRIMARY KEY (id),
    CONSTRAINT fk_conversion_task_1 FOREIGN KEY (document_id) REFERENCES edudoc_document (id),
    CONSTRAINT fk_conversion_task_2 FOREIGN KEY (source_version_id) REFERENCES edudoc_document_version (id),
    CONSTRAINT fk_conversion_task_3 FOREIGN KEY (source_object_id) REFERENCES edudoc_oss_object (id),
    CONSTRAINT fk_conversion_task_4 FOREIGN KEY (result_object_id) REFERENCES edudoc_oss_object (id),
    CONSTRAINT fk_conversion_task_5 FOREIGN KEY (requested_by) REFERENCES edudoc_user (id),
    CONSTRAINT ck_conv_status CHECK (status IN ('QUEUED','RUNNING','SUCCEEDED','FAILED','CANCELED')),
    CONSTRAINT ck_conv_format CHECK (target_format IN ('DOCX','PDF')),
    CONSTRAINT ck_conv_progress CHECK (progress BETWEEN 0 AND 100)
);

COMMENT ON TABLE edudoc_conversion_task IS '记录 Word/PDF 转换任务、输入输出对象与失败原因。';
COMMENT ON COLUMN edudoc_conversion_task.id IS '主键，数据库自动生成 GUID';
COMMENT ON COLUMN edudoc_conversion_task.create_time IS '创建时间，默认数据库当前时间';
COMMENT ON COLUMN edudoc_conversion_task.update_time IS '最后更新时间，由触发器维护';
COMMENT ON COLUMN edudoc_conversion_task.document_id IS '文档 ID';
COMMENT ON COLUMN edudoc_conversion_task.source_version_id IS '源文档版本 ID';
COMMENT ON COLUMN edudoc_conversion_task.source_object_id IS '源 OSS 对象 ID';
COMMENT ON COLUMN edudoc_conversion_task.target_format IS '目标格式：DOCX、PDF';
COMMENT ON COLUMN edudoc_conversion_task.status IS '状态：QUEUED、RUNNING、SUCCEEDED、FAILED、CANCELED';
COMMENT ON COLUMN edudoc_conversion_task.progress IS '进度百分比';
COMMENT ON COLUMN edudoc_conversion_task.result_object_id IS '结果 OSS 对象 ID';
COMMENT ON COLUMN edudoc_conversion_task.failure_code IS '失败错误码';
COMMENT ON COLUMN edudoc_conversion_task.failure_message IS '失败说明';
COMMENT ON COLUMN edudoc_conversion_task.requested_by IS '发起人 ID';
COMMENT ON COLUMN edudoc_conversion_task.retry_count IS '重试次数';
COMMENT ON COLUMN edudoc_conversion_task.started_at IS '开始时间';
COMMENT ON COLUMN edudoc_conversion_task.finished_at IS '结束时间';

-- 智能处理任务表: 保存摘要、要点、问答、润色与向量化任务的状态和幂等信息。
CREATE TABLE edudoc_ai_task (
    id RAW(16) DEFAULT SYS_GUID() NOT NULL,
    create_time TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    update_time TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    document_id RAW(16) NOT NULL,
    document_version_id RAW(16) NOT NULL,
    task_type VARCHAR2(20) NOT NULL,
    status VARCHAR2(20) NOT NULL,
    priority NUMBER(3) DEFAULT 5 NOT NULL,
    model_profile_id RAW(16),
    request_payload JSON,
    requested_by RAW(16) NOT NULL,
    progress NUMBER(5) DEFAULT 0 NOT NULL,
    idempotency_key VARCHAR2(128),
    error_code VARCHAR2(60),
    retry_count NUMBER(5) DEFAULT 0 NOT NULL,
    started_at TIMESTAMP(6) WITH TIME ZONE,
    finished_at TIMESTAMP(6) WITH TIME ZONE,
    CONSTRAINT pk_ai_task PRIMARY KEY (id),
    CONSTRAINT uk_ai_task_1 UNIQUE (idempotency_key),
    CONSTRAINT fk_ai_task_1 FOREIGN KEY (document_id) REFERENCES edudoc_document (id),
    CONSTRAINT fk_ai_task_2 FOREIGN KEY (document_version_id) REFERENCES edudoc_document_version (id),
    CONSTRAINT fk_ai_task_3 FOREIGN KEY (model_profile_id) REFERENCES edudoc_ai_model_profile (id),
    CONSTRAINT fk_ai_task_4 FOREIGN KEY (requested_by) REFERENCES edudoc_user (id),
    CONSTRAINT ck_ai_task_type CHECK (task_type IN ('SUMMARY','KEYPOINT','QA','POLISH','EMBED')),
    CONSTRAINT ck_ai_task_status CHECK (status IN ('QUEUED','RUNNING','SUCCEEDED','FAILED','CANCELED')),
    CONSTRAINT ck_ai_progress CHECK (progress BETWEEN 0 AND 100)
);

COMMENT ON TABLE edudoc_ai_task IS '保存摘要、要点、问答、润色与向量化任务的状态和幂等信息。';
COMMENT ON COLUMN edudoc_ai_task.id IS '主键，数据库自动生成 GUID';
COMMENT ON COLUMN edudoc_ai_task.create_time IS '创建时间，默认数据库当前时间';
COMMENT ON COLUMN edudoc_ai_task.update_time IS '最后更新时间，由触发器维护';
COMMENT ON COLUMN edudoc_ai_task.document_id IS '文档 ID';
COMMENT ON COLUMN edudoc_ai_task.document_version_id IS '文档版本 ID';
COMMENT ON COLUMN edudoc_ai_task.task_type IS '任务类型：SUMMARY、KEYPOINT、QA、POLISH、EMBED';
COMMENT ON COLUMN edudoc_ai_task.status IS '状态：QUEUED、RUNNING、SUCCEEDED、FAILED、CANCELED';
COMMENT ON COLUMN edudoc_ai_task.priority IS '优先级，数值越小越优先';
COMMENT ON COLUMN edudoc_ai_task.model_profile_id IS '模型配置 ID';
COMMENT ON COLUMN edudoc_ai_task.request_payload IS '请求参数，不保存明文敏感凭据';
COMMENT ON COLUMN edudoc_ai_task.requested_by IS '发起人 ID';
COMMENT ON COLUMN edudoc_ai_task.progress IS '进度百分比';
COMMENT ON COLUMN edudoc_ai_task.idempotency_key IS '幂等键';
COMMENT ON COLUMN edudoc_ai_task.error_code IS '失败错误码';
COMMENT ON COLUMN edudoc_ai_task.retry_count IS '重试次数';
COMMENT ON COLUMN edudoc_ai_task.started_at IS '开始时间';
COMMENT ON COLUMN edudoc_ai_task.finished_at IS '结束时间';

-- 智能处理结果表: 保存摘要、要点和润色等结构化结果及模型元数据。
CREATE TABLE edudoc_ai_result (
    id RAW(16) DEFAULT SYS_GUID() NOT NULL,
    create_time TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    update_time TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    task_id RAW(16) NOT NULL,
    result_type VARCHAR2(30) NOT NULL,
    content_text CLOB,
    structured_json JSON,
    confidence NUMBER(5,4),
    model_name VARCHAR2(120) NOT NULL,
    prompt_version VARCHAR2(40) NOT NULL,
    input_tokens NUMBER(12),
    output_tokens NUMBER(12),
    CONSTRAINT pk_ai_result PRIMARY KEY (id),
    CONSTRAINT fk_ai_result_1 FOREIGN KEY (task_id) REFERENCES edudoc_ai_task (id),
    CONSTRAINT ck_result_type CHECK (result_type IN ('SUMMARY','KEYPOINT','POLISH','ANSWER'))
);

COMMENT ON TABLE edudoc_ai_result IS '保存摘要、要点和润色等结构化结果及模型元数据。';
COMMENT ON COLUMN edudoc_ai_result.id IS '主键，数据库自动生成 GUID';
COMMENT ON COLUMN edudoc_ai_result.create_time IS '创建时间，默认数据库当前时间';
COMMENT ON COLUMN edudoc_ai_result.update_time IS '最后更新时间，由触发器维护';
COMMENT ON COLUMN edudoc_ai_result.task_id IS '智能任务 ID';
COMMENT ON COLUMN edudoc_ai_result.result_type IS '结果类型：SUMMARY、KEYPOINT、POLISH、ANSWER';
COMMENT ON COLUMN edudoc_ai_result.content_text IS '结果正文';
COMMENT ON COLUMN edudoc_ai_result.structured_json IS '结构化结果，如摘要段落与要点清单';
COMMENT ON COLUMN edudoc_ai_result.confidence IS '模型置信度，范围 0 至 1';
COMMENT ON COLUMN edudoc_ai_result.model_name IS '实际模型名称';
COMMENT ON COLUMN edudoc_ai_result.prompt_version IS '提示词版本';
COMMENT ON COLUMN edudoc_ai_result.input_tokens IS '输入 token 数';
COMMENT ON COLUMN edudoc_ai_result.output_tokens IS '输出 token 数';

-- 智能结果引用表: 将智能结果关联到原文分块与页码，支持回跳核验。
CREATE TABLE edudoc_ai_citation (
    id RAW(16) DEFAULT SYS_GUID() NOT NULL,
    create_time TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    update_time TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    result_id RAW(16) NOT NULL,
    chunk_id RAW(16) NOT NULL,
    page_no NUMBER(8),
    char_start NUMBER(12),
    char_end NUMBER(12),
    quote_text VARCHAR2(2000),
    rank_no NUMBER(5) DEFAULT 1 NOT NULL,
    CONSTRAINT pk_ai_citation PRIMARY KEY (id),
    CONSTRAINT uk_ai_citation_1 UNIQUE (result_id, chunk_id, rank_no),
    CONSTRAINT fk_ai_citation_1 FOREIGN KEY (result_id) REFERENCES edudoc_ai_result (id),
    CONSTRAINT fk_ai_citation_2 FOREIGN KEY (chunk_id) REFERENCES edudoc_document_chunk (id)
);

COMMENT ON TABLE edudoc_ai_citation IS '将智能结果关联到原文分块与页码，支持回跳核验。';
COMMENT ON COLUMN edudoc_ai_citation.id IS '主键，数据库自动生成 GUID';
COMMENT ON COLUMN edudoc_ai_citation.create_time IS '创建时间，默认数据库当前时间';
COMMENT ON COLUMN edudoc_ai_citation.update_time IS '最后更新时间，由触发器维护';
COMMENT ON COLUMN edudoc_ai_citation.result_id IS '智能结果 ID';
COMMENT ON COLUMN edudoc_ai_citation.chunk_id IS '原文分块 ID';
COMMENT ON COLUMN edudoc_ai_citation.page_no IS '页码';
COMMENT ON COLUMN edudoc_ai_citation.char_start IS '引用起始字符';
COMMENT ON COLUMN edudoc_ai_citation.char_end IS '引用结束字符';
COMMENT ON COLUMN edudoc_ai_citation.quote_text IS '引用原文片段';
COMMENT ON COLUMN edudoc_ai_citation.rank_no IS '引用排序';

-- 问答会话表: 保存围绕单份文档建立的问答会话。
CREATE TABLE edudoc_qa_session (
    id RAW(16) DEFAULT SYS_GUID() NOT NULL,
    create_time TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    update_time TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    document_id RAW(16) NOT NULL,
    user_id RAW(16) NOT NULL,
    title VARCHAR2(200),
    status CHAR(1) DEFAULT 'A' NOT NULL,
    CONSTRAINT pk_qa_session PRIMARY KEY (id),
    CONSTRAINT fk_qa_session_1 FOREIGN KEY (document_id) REFERENCES edudoc_document (id),
    CONSTRAINT fk_qa_session_2 FOREIGN KEY (user_id) REFERENCES edudoc_user (id),
    CONSTRAINT ck_qa_session_status CHECK (status IN ('A','C'))
);

COMMENT ON TABLE edudoc_qa_session IS '保存围绕单份文档建立的问答会话。';
COMMENT ON COLUMN edudoc_qa_session.id IS '主键，数据库自动生成 GUID';
COMMENT ON COLUMN edudoc_qa_session.create_time IS '创建时间，默认数据库当前时间';
COMMENT ON COLUMN edudoc_qa_session.update_time IS '最后更新时间，由触发器维护';
COMMENT ON COLUMN edudoc_qa_session.document_id IS '文档 ID';
COMMENT ON COLUMN edudoc_qa_session.user_id IS '会话用户 ID';
COMMENT ON COLUMN edudoc_qa_session.title IS '会话标题';
COMMENT ON COLUMN edudoc_qa_session.status IS '状态：A 活跃、C 关闭';

-- 问答消息表: 保存用户问题、助手回答、任务关联和连续追问顺序。
CREATE TABLE edudoc_qa_message (
    id RAW(16) DEFAULT SYS_GUID() NOT NULL,
    create_time TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    update_time TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    session_id RAW(16) NOT NULL,
    role VARCHAR2(20) NOT NULL,
    content_text CLOB NOT NULL,
    ai_task_id RAW(16),
    sequence_no NUMBER(10) NOT NULL,
    status VARCHAR2(20) NOT NULL,
    CONSTRAINT pk_qa_message PRIMARY KEY (id),
    CONSTRAINT uk_qa_message_1 UNIQUE (session_id, sequence_no),
    CONSTRAINT fk_qa_message_1 FOREIGN KEY (session_id) REFERENCES edudoc_qa_session (id),
    CONSTRAINT fk_qa_message_2 FOREIGN KEY (ai_task_id) REFERENCES edudoc_ai_task (id),
    CONSTRAINT ck_qa_role CHECK (role IN ('USER','ASSISTANT','SYSTEM')),
    CONSTRAINT ck_qa_status CHECK (status IN ('READY','FAILED','REFUSED'))
);

COMMENT ON TABLE edudoc_qa_message IS '保存用户问题、助手回答、任务关联和连续追问顺序。';
COMMENT ON COLUMN edudoc_qa_message.id IS '主键，数据库自动生成 GUID';
COMMENT ON COLUMN edudoc_qa_message.create_time IS '创建时间，默认数据库当前时间';
COMMENT ON COLUMN edudoc_qa_message.update_time IS '最后更新时间，由触发器维护';
COMMENT ON COLUMN edudoc_qa_message.session_id IS '问答会话 ID';
COMMENT ON COLUMN edudoc_qa_message.role IS '消息角色：USER、ASSISTANT、SYSTEM';
COMMENT ON COLUMN edudoc_qa_message.content_text IS '消息正文';
COMMENT ON COLUMN edudoc_qa_message.ai_task_id IS '关联的智能任务 ID';
COMMENT ON COLUMN edudoc_qa_message.sequence_no IS '会话内消息顺序';
COMMENT ON COLUMN edudoc_qa_message.status IS '状态：READY、FAILED、REFUSED';

-- 润色结果表: 保存原文、润色文本、采纳结果与撤销依据。
CREATE TABLE edudoc_polish_result (
    id RAW(16) DEFAULT SYS_GUID() NOT NULL,
    create_time TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    update_time TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    document_id RAW(16) NOT NULL,
    document_version_id RAW(16) NOT NULL,
    user_id RAW(16) NOT NULL,
    ai_task_id RAW(16) NOT NULL,
    source_text CLOB NOT NULL,
    polished_text CLOB NOT NULL,
    accepted_text CLOB,
    decision VARCHAR2(20) NOT NULL,
    CONSTRAINT pk_polish_result PRIMARY KEY (id),
    CONSTRAINT fk_polish_result_1 FOREIGN KEY (document_id) REFERENCES edudoc_document (id),
    CONSTRAINT fk_polish_result_2 FOREIGN KEY (document_version_id) REFERENCES edudoc_document_version (id),
    CONSTRAINT fk_polish_result_3 FOREIGN KEY (user_id) REFERENCES edudoc_user (id),
    CONSTRAINT fk_polish_result_4 FOREIGN KEY (ai_task_id) REFERENCES edudoc_ai_task (id),
    CONSTRAINT ck_polish_decision CHECK (decision IN ('PENDING','ACCEPT','PARTIAL','REJECT'))
);

COMMENT ON TABLE edudoc_polish_result IS '保存原文、润色文本、采纳结果与撤销依据。';
COMMENT ON COLUMN edudoc_polish_result.id IS '主键，数据库自动生成 GUID';
COMMENT ON COLUMN edudoc_polish_result.create_time IS '创建时间，默认数据库当前时间';
COMMENT ON COLUMN edudoc_polish_result.update_time IS '最后更新时间，由触发器维护';
COMMENT ON COLUMN edudoc_polish_result.document_id IS '文档 ID';
COMMENT ON COLUMN edudoc_polish_result.document_version_id IS '原始文档版本 ID';
COMMENT ON COLUMN edudoc_polish_result.user_id IS '操作用户 ID';
COMMENT ON COLUMN edudoc_polish_result.ai_task_id IS '智能任务 ID';
COMMENT ON COLUMN edudoc_polish_result.source_text IS '润色前文本';
COMMENT ON COLUMN edudoc_polish_result.polished_text IS '模型润色文本';
COMMENT ON COLUMN edudoc_polish_result.accepted_text IS '实际采纳文本';
COMMENT ON COLUMN edudoc_polish_result.decision IS '处理结果：PENDING、ACCEPT、PARTIAL、REJECT';

-- 协作会话表: 保存实时协作会话、基线版本与同步状态。
CREATE TABLE edudoc_collab_session (
    id RAW(16) DEFAULT SYS_GUID() NOT NULL,
    create_time TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    update_time TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    document_id RAW(16) NOT NULL,
    base_version_id RAW(16) NOT NULL,
    provider_doc_key VARCHAR2(120) NOT NULL,
    status VARCHAR2(20) NOT NULL,
    created_by RAW(16) NOT NULL,
    opened_at TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    closed_at TIMESTAMP(6) WITH TIME ZONE,
    last_snapshot_at TIMESTAMP(6) WITH TIME ZONE,
    CONSTRAINT pk_collab_session PRIMARY KEY (id),
    CONSTRAINT uk_collab_session_1 UNIQUE (provider_doc_key),
    CONSTRAINT fk_collab_session_1 FOREIGN KEY (document_id) REFERENCES edudoc_document (id),
    CONSTRAINT fk_collab_session_2 FOREIGN KEY (base_version_id) REFERENCES edudoc_document_version (id),
    CONSTRAINT fk_collab_session_3 FOREIGN KEY (created_by) REFERENCES edudoc_user (id),
    CONSTRAINT ck_collab_status CHECK (status IN ('OPEN','CLOSED','SNAPSHOTTED'))
);

COMMENT ON TABLE edudoc_collab_session IS '保存实时协作会话、基线版本与同步状态。';
COMMENT ON COLUMN edudoc_collab_session.id IS '主键，数据库自动生成 GUID';
COMMENT ON COLUMN edudoc_collab_session.create_time IS '创建时间，默认数据库当前时间';
COMMENT ON COLUMN edudoc_collab_session.update_time IS '最后更新时间，由触发器维护';
COMMENT ON COLUMN edudoc_collab_session.document_id IS '文档 ID';
COMMENT ON COLUMN edudoc_collab_session.base_version_id IS '协作开始时的版本 ID';
COMMENT ON COLUMN edudoc_collab_session.provider_doc_key IS 'Yjs 网关中的文档键';
COMMENT ON COLUMN edudoc_collab_session.status IS '状态：OPEN、CLOSED、SNAPSHOTTED';
COMMENT ON COLUMN edudoc_collab_session.created_by IS '创建人 ID';
COMMENT ON COLUMN edudoc_collab_session.opened_at IS '打开时间';
COMMENT ON COLUMN edudoc_collab_session.closed_at IS '关闭时间';
COMMENT ON COLUMN edudoc_collab_session.last_snapshot_at IS '最近快照时间';

-- 协作成员表: 保存协作会话的参与成员、权限和在线状态。
CREATE TABLE edudoc_collab_member (
    id RAW(16) DEFAULT SYS_GUID() NOT NULL,
    create_time TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    update_time TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    collab_session_id RAW(16) NOT NULL,
    user_id RAW(16) NOT NULL,
    permission_level VARCHAR2(20) NOT NULL,
    connection_status VARCHAR2(20) NOT NULL,
    joined_at TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    left_at TIMESTAMP(6) WITH TIME ZONE,
    CONSTRAINT pk_collab_member PRIMARY KEY (id),
    CONSTRAINT uk_collab_member_1 UNIQUE (collab_session_id, user_id),
    CONSTRAINT fk_collab_member_1 FOREIGN KEY (collab_session_id) REFERENCES edudoc_collab_session (id),
    CONSTRAINT fk_collab_member_2 FOREIGN KEY (user_id) REFERENCES edudoc_user (id),
    CONSTRAINT ck_collab_level CHECK (permission_level IN ('VIEW','COMMENT','EDIT')),
    CONSTRAINT ck_conn_status CHECK (connection_status IN ('ONLINE','OFFLINE','REMOVED'))
);

COMMENT ON TABLE edudoc_collab_member IS '保存协作会话的参与成员、权限和在线状态。';
COMMENT ON COLUMN edudoc_collab_member.id IS '主键，数据库自动生成 GUID';
COMMENT ON COLUMN edudoc_collab_member.create_time IS '创建时间，默认数据库当前时间';
COMMENT ON COLUMN edudoc_collab_member.update_time IS '最后更新时间，由触发器维护';
COMMENT ON COLUMN edudoc_collab_member.collab_session_id IS '协作会话 ID';
COMMENT ON COLUMN edudoc_collab_member.user_id IS '成员用户 ID';
COMMENT ON COLUMN edudoc_collab_member.permission_level IS '权限：VIEW、COMMENT、EDIT';
COMMENT ON COLUMN edudoc_collab_member.connection_status IS '连接状态：ONLINE、OFFLINE、REMOVED';
COMMENT ON COLUMN edudoc_collab_member.joined_at IS '加入时间';
COMMENT ON COLUMN edudoc_collab_member.left_at IS '离开时间';

-- 协作快照表: 保存 Yjs 快照对应的文档版本和 OSS 对象。
CREATE TABLE edudoc_collab_snapshot (
    id RAW(16) DEFAULT SYS_GUID() NOT NULL,
    create_time TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    update_time TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    collab_session_id RAW(16) NOT NULL,
    document_version_id RAW(16) NOT NULL,
    snapshot_object_id RAW(16) NOT NULL,
    state_vector_hash VARCHAR2(128) NOT NULL,
    last_update_no NUMBER(19) NOT NULL,
    CONSTRAINT pk_collab_snapshot PRIMARY KEY (id),
    CONSTRAINT uk_collab_snapshot_1 UNIQUE (collab_session_id, document_version_id),
    CONSTRAINT fk_collab_snapshot_1 FOREIGN KEY (collab_session_id) REFERENCES edudoc_collab_session (id),
    CONSTRAINT fk_collab_snapshot_2 FOREIGN KEY (document_version_id) REFERENCES edudoc_document_version (id),
    CONSTRAINT fk_collab_snapshot_3 FOREIGN KEY (snapshot_object_id) REFERENCES edudoc_oss_object (id)
);

COMMENT ON TABLE edudoc_collab_snapshot IS '保存 Yjs 快照对应的文档版本和 OSS 对象。';
COMMENT ON COLUMN edudoc_collab_snapshot.id IS '主键，数据库自动生成 GUID';
COMMENT ON COLUMN edudoc_collab_snapshot.create_time IS '创建时间，默认数据库当前时间';
COMMENT ON COLUMN edudoc_collab_snapshot.update_time IS '最后更新时间，由触发器维护';
COMMENT ON COLUMN edudoc_collab_snapshot.collab_session_id IS '协作会话 ID';
COMMENT ON COLUMN edudoc_collab_snapshot.document_version_id IS '落库的文档版本 ID';
COMMENT ON COLUMN edudoc_collab_snapshot.snapshot_object_id IS 'Yjs 快照 OSS 对象 ID';
COMMENT ON COLUMN edudoc_collab_snapshot.state_vector_hash IS 'Yjs 状态向量摘要';
COMMENT ON COLUMN edudoc_collab_snapshot.last_update_no IS '快照覆盖的更新序号';

-- 作业表: 保存教师发布的课程作业、模板和截止时间。
CREATE TABLE edudoc_assignment (
    id RAW(16) DEFAULT SYS_GUID() NOT NULL,
    create_time TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    update_time TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    course_id RAW(16) NOT NULL,
    title VARCHAR2(200) NOT NULL,
    description VARCHAR2(2000),
    template_version_id RAW(16),
    published_by RAW(16) NOT NULL,
    publish_time TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    due_time TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    allow_late CHAR(1) DEFAULT 'N' NOT NULL,
    submission_format VARCHAR2(10) DEFAULT 'BOTH' NOT NULL,
    require_format_check CHAR(1) DEFAULT 'N' NOT NULL,
    rubric_json VARCHAR2(2000),
    status VARCHAR2(20) NOT NULL,
    CONSTRAINT pk_assignment PRIMARY KEY (id),
    CONSTRAINT fk_assignment_1 FOREIGN KEY (course_id) REFERENCES edudoc_course (id),
    CONSTRAINT fk_assignment_2 FOREIGN KEY (template_version_id) REFERENCES edudoc_template_version (id),
    CONSTRAINT fk_assignment_3 FOREIGN KEY (published_by) REFERENCES edudoc_user (id),
    CONSTRAINT ck_assignment_late CHECK (allow_late IN ('Y','N')),
    CONSTRAINT ck_assignment_format CHECK (submission_format IN ('DOCX','PDF','BOTH')),
    CONSTRAINT ck_assignment_reqcheck CHECK (require_format_check IN ('Y','N')),
    CONSTRAINT ck_assignment_status CHECK (status IN ('DRAFT','PUBLISHED','CLOSED'))
);

COMMENT ON TABLE edudoc_assignment IS '保存教师发布的课程作业、模板和截止时间。';
COMMENT ON COLUMN edudoc_assignment.id IS '主键，数据库自动生成 GUID';
COMMENT ON COLUMN edudoc_assignment.create_time IS '创建时间，默认数据库当前时间';
COMMENT ON COLUMN edudoc_assignment.update_time IS '最后更新时间，由触发器维护';
COMMENT ON COLUMN edudoc_assignment.course_id IS '课程 ID';
COMMENT ON COLUMN edudoc_assignment.title IS '作业标题';
COMMENT ON COLUMN edudoc_assignment.description IS '作业说明';
COMMENT ON COLUMN edudoc_assignment.template_version_id IS '要求使用的模板版本 ID';
COMMENT ON COLUMN edudoc_assignment.published_by IS '发布教师 ID';
COMMENT ON COLUMN edudoc_assignment.publish_time IS '发布时间';
COMMENT ON COLUMN edudoc_assignment.due_time IS '截止时间';
COMMENT ON COLUMN edudoc_assignment.allow_late IS '是否允许迟交：Y/N';
COMMENT ON COLUMN edudoc_assignment.submission_format IS '要求提交格式：DOCX、PDF、BOTH';
COMMENT ON COLUMN edudoc_assignment.require_format_check IS '提交前是否强制格式校验：Y/N';
COMMENT ON COLUMN edudoc_assignment.rubric_json IS '评分标准 JSON：[{"name":"…","maxScore":20}]，分项明细不做 SQL 聚合';
COMMENT ON COLUMN edudoc_assignment.status IS '状态：DRAFT、PUBLISHED、CLOSED';

-- 作业提交表: 记录学生某次提交的文档版本、格式校验与评分信息。
CREATE TABLE edudoc_submission (
    id RAW(16) DEFAULT SYS_GUID() NOT NULL,
    create_time TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    update_time TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    assignment_id RAW(16) NOT NULL,
    student_id RAW(16) NOT NULL,
    document_id RAW(16) NOT NULL,
    document_version_id RAW(16) NOT NULL,
    format_check_id RAW(16),
    attempt_no NUMBER(5) DEFAULT 1 NOT NULL,
    submitted_at TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    status VARCHAR2(20) NOT NULL,
    score NUMBER(6,2),
    feedback VARCHAR2(2000),
    score_json VARCHAR2(2000),
    CONSTRAINT pk_submission PRIMARY KEY (id),
    CONSTRAINT uk_submission_1 UNIQUE (assignment_id, student_id, attempt_no),
    CONSTRAINT fk_submission_1 FOREIGN KEY (assignment_id) REFERENCES edudoc_assignment (id),
    CONSTRAINT fk_submission_2 FOREIGN KEY (student_id) REFERENCES edudoc_user (id),
    CONSTRAINT fk_submission_3 FOREIGN KEY (document_id) REFERENCES edudoc_document (id),
    CONSTRAINT fk_submission_4 FOREIGN KEY (document_version_id) REFERENCES edudoc_document_version (id),
    CONSTRAINT fk_submission_5 FOREIGN KEY (format_check_id) REFERENCES edudoc_format_check (id),
    CONSTRAINT ck_submission_status CHECK (status IN ('SUBMITTED','LATE','WITHDRAWN','GRADED'))
);

COMMENT ON TABLE edudoc_submission IS '记录学生某次提交的文档版本、格式校验与评分信息。';
COMMENT ON COLUMN edudoc_submission.id IS '主键，数据库自动生成 GUID';
COMMENT ON COLUMN edudoc_submission.create_time IS '创建时间，默认数据库当前时间';
COMMENT ON COLUMN edudoc_submission.update_time IS '最后更新时间，由触发器维护';
COMMENT ON COLUMN edudoc_submission.assignment_id IS '作业 ID';
COMMENT ON COLUMN edudoc_submission.student_id IS '学生 ID';
COMMENT ON COLUMN edudoc_submission.document_id IS '提交文档 ID';
COMMENT ON COLUMN edudoc_submission.document_version_id IS '提交版本 ID';
COMMENT ON COLUMN edudoc_submission.format_check_id IS '提交前格式校验 ID';
COMMENT ON COLUMN edudoc_submission.attempt_no IS '提交次数';
COMMENT ON COLUMN edudoc_submission.submitted_at IS '提交时间';
COMMENT ON COLUMN edudoc_submission.status IS '状态：SUBMITTED、LATE、WITHDRAWN、GRADED';
COMMENT ON COLUMN edudoc_submission.score IS '评分（服务端按 score_json 分项求和，不信任前端总分）';
COMMENT ON COLUMN edudoc_submission.feedback IS '教师反馈';
COMMENT ON COLUMN edudoc_submission.score_json IS '分项得分 JSON：[{"name":"…","maxScore":20,"score":18}]';

-- 导出任务表: 记录全文、摘要、要点、问答和指定版本的导出任务。
CREATE TABLE edudoc_export_task (
    id RAW(16) DEFAULT SYS_GUID() NOT NULL,
    create_time TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    update_time TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    document_id RAW(16) NOT NULL,
    document_version_id RAW(16) NOT NULL,
    export_scope VARCHAR2(30) NOT NULL,
    target_format VARCHAR2(20) NOT NULL,
    status VARCHAR2(20) NOT NULL,
    result_object_id RAW(16),
    requested_by RAW(16) NOT NULL,
    failure_message VARCHAR2(1000),
    finished_at TIMESTAMP(6) WITH TIME ZONE,
    CONSTRAINT pk_export_task PRIMARY KEY (id),
    CONSTRAINT fk_export_task_1 FOREIGN KEY (document_id) REFERENCES edudoc_document (id),
    CONSTRAINT fk_export_task_2 FOREIGN KEY (document_version_id) REFERENCES edudoc_document_version (id),
    CONSTRAINT fk_export_task_3 FOREIGN KEY (result_object_id) REFERENCES edudoc_oss_object (id),
    CONSTRAINT fk_export_task_4 FOREIGN KEY (requested_by) REFERENCES edudoc_user (id),
    CONSTRAINT ck_export_scope CHECK (export_scope IN ('FULL','SUMMARY','KEYPOINT','QA','VERSION')),
    CONSTRAINT ck_export_format CHECK (target_format IN ('DOCX','PDF','TXT')),
    CONSTRAINT ck_export_status CHECK (status IN ('QUEUED','RUNNING','SUCCEEDED','FAILED'))
);

COMMENT ON TABLE edudoc_export_task IS '记录全文、摘要、要点、问答和指定版本的导出任务。';
COMMENT ON COLUMN edudoc_export_task.id IS '主键，数据库自动生成 GUID';
COMMENT ON COLUMN edudoc_export_task.create_time IS '创建时间，默认数据库当前时间';
COMMENT ON COLUMN edudoc_export_task.update_time IS '最后更新时间，由触发器维护';
COMMENT ON COLUMN edudoc_export_task.document_id IS '文档 ID';
COMMENT ON COLUMN edudoc_export_task.document_version_id IS '文档版本 ID';
COMMENT ON COLUMN edudoc_export_task.export_scope IS '导出范围：FULL、SUMMARY、KEYPOINT、QA、VERSION';
COMMENT ON COLUMN edudoc_export_task.target_format IS '目标格式：DOCX、PDF、TXT';
COMMENT ON COLUMN edudoc_export_task.status IS '状态：QUEUED、RUNNING、SUCCEEDED、FAILED';
COMMENT ON COLUMN edudoc_export_task.result_object_id IS '结果 OSS 对象 ID';
COMMENT ON COLUMN edudoc_export_task.requested_by IS '发起人 ID';
COMMENT ON COLUMN edudoc_export_task.failure_message IS '失败说明';
COMMENT ON COLUMN edudoc_export_task.finished_at IS '完成时间';

-- 参考文献条目表: 保存解析后的参考文献条目、标准格式与生成结果。
CREATE TABLE edudoc_reference_item (
    id RAW(16) DEFAULT SYS_GUID() NOT NULL,
    create_time TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    update_time TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    document_id RAW(16) NOT NULL,
    document_version_id RAW(16) NOT NULL,
    raw_text VARCHAR2(2000) NOT NULL,
    parsed_json JSON,
    standard_code VARCHAR2(40),
    formatted_text VARCHAR2(2000),
    status VARCHAR2(20) NOT NULL,
    CONSTRAINT pk_reference_item PRIMARY KEY (id),
    CONSTRAINT fk_reference_item_1 FOREIGN KEY (document_id) REFERENCES edudoc_document (id),
    CONSTRAINT fk_reference_item_2 FOREIGN KEY (document_version_id) REFERENCES edudoc_document_version (id),
    CONSTRAINT ck_ref_status CHECK (status IN ('PARSED','FORMATTED','FAILED'))
);

COMMENT ON TABLE edudoc_reference_item IS '保存解析后的参考文献条目、标准格式与生成结果。';
COMMENT ON COLUMN edudoc_reference_item.id IS '主键，数据库自动生成 GUID';
COMMENT ON COLUMN edudoc_reference_item.create_time IS '创建时间，默认数据库当前时间';
COMMENT ON COLUMN edudoc_reference_item.update_time IS '最后更新时间，由触发器维护';
COMMENT ON COLUMN edudoc_reference_item.document_id IS '文档 ID';
COMMENT ON COLUMN edudoc_reference_item.document_version_id IS '文档版本 ID';
COMMENT ON COLUMN edudoc_reference_item.raw_text IS '原始条目';
COMMENT ON COLUMN edudoc_reference_item.parsed_json IS '作者、题名、年份、DOI 等解析结果';
COMMENT ON COLUMN edudoc_reference_item.standard_code IS '引用标准，如 GB/T 7714-2015';
COMMENT ON COLUMN edudoc_reference_item.formatted_text IS '标准格式化结果';
COMMENT ON COLUMN edudoc_reference_item.status IS '状态：PARSED、FORMATTED、FAILED';

-- 审计日志表: 追加写入文档访问、编辑、下载、共享和权限变更事件。
CREATE TABLE edudoc_audit_log (
    id RAW(16) DEFAULT SYS_GUID() NOT NULL,
    create_time TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    update_time TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    actor_id RAW(16),
    actor_role VARCHAR2(40),
    action_type VARCHAR2(60) NOT NULL,
    resource_type VARCHAR2(40) NOT NULL,
    resource_ref VARCHAR2(128) NOT NULL,
    document_id RAW(16),
    result_code VARCHAR2(40) NOT NULL,
    source_ip VARCHAR2(64),
    user_agent VARCHAR2(500),
    trace_id VARCHAR2(64) NOT NULL,
    detail_json JSON,
    occurred_at TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_audit_log PRIMARY KEY (id),
    CONSTRAINT fk_audit_log_1 FOREIGN KEY (actor_id) REFERENCES edudoc_user (id),
    CONSTRAINT fk_audit_log_2 FOREIGN KEY (document_id) REFERENCES edudoc_document (id)
);

COMMENT ON TABLE edudoc_audit_log IS '追加写入文档访问、编辑、下载、共享和权限变更事件。';
COMMENT ON COLUMN edudoc_audit_log.id IS '主键，数据库自动生成 GUID';
COMMENT ON COLUMN edudoc_audit_log.create_time IS '创建时间，默认数据库当前时间';
COMMENT ON COLUMN edudoc_audit_log.update_time IS '最后更新时间，由触发器维护';
COMMENT ON COLUMN edudoc_audit_log.actor_id IS '操作人 ID，系统任务可为空';
COMMENT ON COLUMN edudoc_audit_log.actor_role IS '操作时的角色编码';
COMMENT ON COLUMN edudoc_audit_log.action_type IS '操作类型';
COMMENT ON COLUMN edudoc_audit_log.resource_type IS '资源类型';
COMMENT ON COLUMN edudoc_audit_log.resource_ref IS '资源 ID 或业务键';
COMMENT ON COLUMN edudoc_audit_log.document_id IS '关联文档 ID';
COMMENT ON COLUMN edudoc_audit_log.result_code IS '处理结果码';
COMMENT ON COLUMN edudoc_audit_log.source_ip IS '来源 IP';
COMMENT ON COLUMN edudoc_audit_log.user_agent IS '客户端标识';
COMMENT ON COLUMN edudoc_audit_log.trace_id IS '链路追踪 ID';
COMMENT ON COLUMN edudoc_audit_log.detail_json IS '不含正文和令牌的补充信息';
COMMENT ON COLUMN edudoc_audit_log.occurred_at IS '事件发生时间';

-- 可靠事件表: 通过事务性 Outbox 将解析、转换、通知和同步事件投递到异步任务。
CREATE TABLE edudoc_outbox_event (
    id RAW(16) DEFAULT SYS_GUID() NOT NULL,
    create_time TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    update_time TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    aggregate_type VARCHAR2(60) NOT NULL,
    aggregate_ref VARCHAR2(128) NOT NULL,
    event_type VARCHAR2(80) NOT NULL,
    payload_json JSON NOT NULL,
    status VARCHAR2(20) DEFAULT 'NEW' NOT NULL,
    retry_count NUMBER(5) DEFAULT 0 NOT NULL,
    next_retry_at TIMESTAMP(6) WITH TIME ZONE,
    published_at TIMESTAMP(6) WITH TIME ZONE,
    CONSTRAINT pk_outbox_event PRIMARY KEY (id),
    CONSTRAINT ck_outbox_status CHECK (status IN ('NEW','PUBLISHED','FAILED'))
);

COMMENT ON TABLE edudoc_outbox_event IS '通过事务性 Outbox 将解析、转换、通知和同步事件投递到异步任务。';
COMMENT ON COLUMN edudoc_outbox_event.id IS '主键，数据库自动生成 GUID';
COMMENT ON COLUMN edudoc_outbox_event.create_time IS '创建时间，默认数据库当前时间';
COMMENT ON COLUMN edudoc_outbox_event.update_time IS '最后更新时间，由触发器维护';
COMMENT ON COLUMN edudoc_outbox_event.aggregate_type IS '聚合类型';
COMMENT ON COLUMN edudoc_outbox_event.aggregate_ref IS '聚合业务键';
COMMENT ON COLUMN edudoc_outbox_event.event_type IS '事件类型';
COMMENT ON COLUMN edudoc_outbox_event.payload_json IS '事件负载';
COMMENT ON COLUMN edudoc_outbox_event.status IS '状态：NEW、PUBLISHED、FAILED';
COMMENT ON COLUMN edudoc_outbox_event.retry_count IS '投递重试次数';
COMMENT ON COLUMN edudoc_outbox_event.next_retry_at IS '下次重试时间';
COMMENT ON COLUMN edudoc_outbox_event.published_at IS '投递成功时间';
