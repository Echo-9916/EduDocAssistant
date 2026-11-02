package com.cidas.edudoc.document.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** OSS 对象登记读写（SQL 委托给 {@link OssObjectMapper}）。 */
@Repository
@RequiredArgsConstructor
public class OssObjectRepository {

    private final OssObjectMapper mapper;

    public void insert(OssObject object) {
        mapper.insert(object);
    }

    public Optional<OssObject> findByObject(String bucketName, String objectKey) {
        return Optional.ofNullable(mapper.selectByObject(bucketName, objectKey));
    }
}
