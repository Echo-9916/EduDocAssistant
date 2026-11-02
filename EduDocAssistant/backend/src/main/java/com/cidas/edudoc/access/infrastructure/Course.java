package com.cidas.edudoc.access.infrastructure;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 课程持久化对象（edudoc_course，id 为 RAW(16) 转 hex 字符串；memberRole 为当前用户的成员角色）。 */
@Data
@AllArgsConstructor
public class Course {

    private String id;
    private String courseCode;
    private String courseName;
    private String termCode;
    private String memberRole;
}
