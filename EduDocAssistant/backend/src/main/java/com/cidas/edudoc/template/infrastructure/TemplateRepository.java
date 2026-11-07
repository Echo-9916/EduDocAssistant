package com.cidas.edudoc.template.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** 模板主档读写（SQL 委托给 {@link TemplateMapper}）。 */
@Repository
@RequiredArgsConstructor
public class TemplateRepository {

    private final TemplateMapper mapper;

    public int insert(Template template) {
        return mapper.insert(template);
    }

    public Optional<Template> findById(String templateIdHex) {
        return Optional.ofNullable(mapper.selectById(templateIdHex));
    }

    public Optional<TemplateViewRow> findView(String templateIdHex) {
        return Optional.ofNullable(mapper.selectView(templateIdHex));
    }

    public List<TemplateViewRow> page(String role, String ownerId, List<String> courseScope,
                                      String courseId, String status, boolean unrestricted,
                                      long offset, int limit) {
        return mapper.selectPage(role, ownerId, courseScope, courseId, status, unrestricted, offset, limit);
    }

    public long count(String role, String ownerId, List<String> courseScope,
                      String courseId, String status, boolean unrestricted) {
        return mapper.countPage(role, ownerId, courseScope, courseId, status, unrestricted);
    }

    public int updateStatus(String templateIdHex, String status) {
        return mapper.updateStatus(templateIdHex, status);
    }

    /** 发布前移当前版本指针（D-02）。 */
    public void updateCurrentVersion(String templateIdHex, String versionIdHex) {
        mapper.updateCurrentVersion(templateIdHex, versionIdHex);
    }

    /** 返回删除行数；0 表示不是「无版本草稿」，由服务层映射 40901。 */
    public int deleteDraft(String templateIdHex) {
        return mapper.deleteDraft(templateIdHex);
    }
}
