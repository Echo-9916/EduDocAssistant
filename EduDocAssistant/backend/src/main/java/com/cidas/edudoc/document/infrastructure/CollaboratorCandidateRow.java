package com.cidas.edudoc.document.infrastructure;

import lombok.Data;

/** 课程成员候选：文档所属课程内的教师/学生（协作者邀请选择器）。 */
@Data
public class CollaboratorCandidateRow {

    private String userId;
    private String username;
    private String displayName;
    private String role;
    private String groupName;
}
