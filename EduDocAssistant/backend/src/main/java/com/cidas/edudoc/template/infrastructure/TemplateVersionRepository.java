package com.cidas.edudoc.template.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** 模板版本读写（SQL 委托给 {@link TemplateVersionMapper}）。 */
@Repository
@RequiredArgsConstructor
public class TemplateVersionRepository {

    private final TemplateVersionMapper mapper;

    public void insert(TemplateVersion version) {
        mapper.insert(version);
    }

    public Optional<TemplateVersion> findById(String versionIdHex) {
        return Optional.ofNullable(mapper.selectById(versionIdHex));
    }

    public int maxVersionNo(String templateIdHex) {
        Integer max = mapper.selectMaxVersionNo(templateIdHex);
        return max == null ? 0 : max;
    }

    public List<TemplateVersion> listByTemplate(String templateIdHex) {
        return mapper.selectListByTemplate(templateIdHex);
    }

    public void updateValidation(String versionIdHex, String status) {
        mapper.updateValidation(versionIdHex, status);
    }

    /** 返回 false=已被并发发布（published_at IS NULL 守卫输者），调用方映射 40901。 */
    public boolean markPublished(String versionIdHex, String publishedByHex) {
        return mapper.markPublished(versionIdHex, publishedByHex) == 1;
    }
}
