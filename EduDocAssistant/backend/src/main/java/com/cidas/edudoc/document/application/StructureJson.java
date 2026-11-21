package com.cidas.edudoc.document.application;

import com.cidas.edudoc.document.domain.StructureDocument;
import com.cidas.edudoc.document.domain.StructureNode;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

/** structure_json 编解码：节点只存区间/元数据，null 字段不落库。 */
public final class StructureJson {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private StructureJson() {
    }

    /** 空结构返回 null（不写库）；序列化失败视为解析失败，由事务层置 FAILED。 */
    public static String write(List<StructureNode> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(StructureDocument.of(nodes));
        } catch (Exception e) {
            throw new IllegalStateException("结构层序列化失败", e);
        }
    }

    /** 老数据/损坏数据一律降级为空结构，预览不因结构层问题整体失败。 */
    public static List<StructureNode> read(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            StructureDocument doc = MAPPER.readValue(json, StructureDocument.class);
            return doc.nodes() == null ? List.of() : doc.nodes();
        } catch (Exception e) {
            return List.of();
        }
    }
}