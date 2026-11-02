package com.cidas.edudoc.access.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 课程与成员关系查询（SQL 见 resources/mapper/CourseMapper.xml）。 */
@Mapper
public interface CourseMapper {

    List<Course> selectCoursesByUser(@Param("userIdHex") String userIdHex);
}
