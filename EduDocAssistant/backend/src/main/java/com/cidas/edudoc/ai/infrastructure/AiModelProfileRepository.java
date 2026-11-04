package com.cidas.edudoc.ai.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** 模型档案只读访问。 */
@Repository
@RequiredArgsConstructor
public class AiModelProfileRepository {

    private final AiModelProfileMapper mapper;

    public Optional<AiModelProfile> findByCode(String profileCode) {
        return Optional.ofNullable(mapper.selectByCode(profileCode));
    }
}
