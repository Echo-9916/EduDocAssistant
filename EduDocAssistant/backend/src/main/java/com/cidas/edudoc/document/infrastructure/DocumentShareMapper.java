package com.cidas.edudoc.document.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;

/** 文档共享链接读写（SQL 见 resources/mapper/DocumentShareMapper.xml）。 */
@Mapper
public interface DocumentShareMapper {

    int insertShare(@Param("idHex") String idHex, @Param("documentIdHex") String documentIdHex,
                    @Param("ownerIdHex") String ownerIdHex, @Param("token") String token,
                    @Param("expiresAt") OffsetDateTime expiresAt);

    int insertLinkGrant(@Param("idHex") String idHex, @Param("shareIdHex") String shareIdHex,
                        @Param("permissionLevel") String permissionLevel, @Param("grantedByHex") String grantedByHex);

    List<DocumentShareRow> selectByDocument(@Param("documentIdHex") String documentIdHex);

    DocumentShareRow selectByToken(@Param("token") String token);

    DocumentShareRow selectById(@Param("shareIdHex") String shareIdHex);

    int revoke(@Param("shareIdHex") String shareIdHex);

    int markExpired(@Param("shareIdHex") String shareIdHex);
}