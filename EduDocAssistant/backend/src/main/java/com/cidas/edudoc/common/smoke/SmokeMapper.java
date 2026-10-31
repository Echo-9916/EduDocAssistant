package com.cidas.edudoc.common.smoke;

import org.apache.ibatis.annotations.Mapper;

/** 冒烟探针的数据访问（SQL 见 resources/mapper/SmokeMapper.xml）。 */
@Mapper
public interface SmokeMapper {

    int selectRoleCount();
}
