package com.cidas.edudoc.access.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 运维用户目录与冻结/解冻（SQL 见 resources/mapper/OpsUserMapper.xml）。 */
@Mapper
public interface OpsUserMapper {

    List<OpsUserRow> selectPage(@Param("keyword") String keyword, @Param("role") String role,
                                @Param("status") String status, @Param("offset") long offset,
                                @Param("limit") int limit);

    long count(@Param("keyword") String keyword, @Param("role") String role,
               @Param("status") String status);

    OpsUserRow selectById(@Param("userIdHex") String userIdHex);

    int countRole(@Param("userIdHex") String userIdHex, @Param("roleCode") String roleCode);

    /** 条件迁移账号状态：只有当前状态等于 expected 才更新，返回 0 表示状态已变化。 */
    int updateStatus(@Param("userIdHex") String userIdHex, @Param("expected") String expected,
                     @Param("newStatus") String newStatus);
}