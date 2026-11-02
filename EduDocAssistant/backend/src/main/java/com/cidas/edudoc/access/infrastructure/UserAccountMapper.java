package com.cidas.edudoc.access.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 用户账号、角色、权限点的查询（SQL 见 resources/mapper/UserAccountMapper.xml）。 */
@Mapper
public interface UserAccountMapper {

    UserAccount selectByUsername(@Param("username") String username);

    String selectAccountStatus(@Param("userIdHex") String userIdHex);

    String selectDisplayName(@Param("userIdHex") String userIdHex);

    List<String> selectRoleCodes(@Param("userIdHex") String userIdHex);

    List<String> selectPermissionCodes(@Param("userIdHex") String userIdHex);

    int updateLastLoginTime(@Param("userIdHex") String userIdHex);
}
