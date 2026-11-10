package com.cidas.edudoc.access.domain;

/** 权限点编码，与 04_seed.sql 的 20 个权限点保持一致。 */
public final class PermissionCode {

    private PermissionCode() {
    }

    public static final String DOCUMENT_READ = "DOCUMENT_READ";
    public static final String DOCUMENT_CREATE = "DOCUMENT_CREATE";
    public static final String DOCUMENT_EDIT = "DOCUMENT_EDIT";
    public static final String DOCUMENT_DELETE = "DOCUMENT_DELETE";
    public static final String DOCUMENT_DOWNLOAD = "DOCUMENT_DOWNLOAD";
    public static final String DOCUMENT_SHARE = "DOCUMENT_SHARE";
    public static final String AI_SUMMARY = "AI_SUMMARY";
    public static final String AI_KEYPOINT = "AI_KEYPOINT";
    public static final String AI_QA = "AI_QA";
    public static final String AI_POLISH = "AI_POLISH";
    public static final String TEMPLATE_READ = "TEMPLATE_READ";
    public static final String TEMPLATE_MANAGE = "TEMPLATE_MANAGE";
    public static final String FORMAT_CHECK = "FORMAT_CHECK";
    public static final String FORMAT_CONVERT = "FORMAT_CONVERT";
    public static final String COLLAB_EDIT = "COLLAB_EDIT";
    public static final String VERSION_RESTORE = "VERSION_RESTORE";
    public static final String ROLE_MANAGE = "ROLE_MANAGE";
    public static final String AUDIT_READ = "AUDIT_READ";
    public static final String ASSIGNMENT_MANAGE = "ASSIGNMENT_MANAGE";
    public static final String SUBMISSION_READ = "SUBMISSION_READ";
    /** 学生查看本人作业、提交与反馈（06 切片 1）：与 SUBMISSION_READ 的「提交统计」口径区分。 */
    public static final String SUBMISSION_SELF_READ = "SUBMISSION_SELF_READ";
}
