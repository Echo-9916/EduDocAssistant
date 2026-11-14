package com.cidas.edudoc.document.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 文档协作者读写：复用 collab_session（每文档一个会话）+ collab_member。 */
@Mapper
public interface DocumentCollaboratorMapper {

    String selectSessionIdByDocument(@Param("documentIdHex") String documentIdHex);

    int insertSession(@Param("idHex") String idHex, @Param("documentIdHex") String documentIdHex,
                      @Param("baseVersionIdHex") String baseVersionIdHex,
                      @Param("providerDocKey") String providerDocKey, @Param("createdByHex") String createdByHex);

    List<CollaboratorRow> selectMembers(@Param("sessionIdHex") String sessionIdHex);

    CollaboratorRow selectMember(@Param("sessionIdHex") String sessionIdHex, @Param("userIdHex") String userIdHex);

    int countActiveMember(@Param("documentIdHex") String documentIdHex, @Param("userIdHex") String userIdHex);

    int insertMember(@Param("idHex") String idHex, @Param("sessionIdHex") String sessionIdHex,
                     @Param("userIdHex") String userIdHex, @Param("permissionLevel") String permissionLevel);

    int reactivateMember(@Param("sessionIdHex") String sessionIdHex, @Param("userIdHex") String userIdHex,
                         @Param("permissionLevel") String permissionLevel);

    int removeMember(@Param("sessionIdHex") String sessionIdHex, @Param("userIdHex") String userIdHex);

    CollaboratorRow selectUserByUsername(@Param("username") String username);
}