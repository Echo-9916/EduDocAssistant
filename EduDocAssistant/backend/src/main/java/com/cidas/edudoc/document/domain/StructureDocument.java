package com.cidas.edudoc.document.domain;

import java.util.List;

/** structure_json 的顶层结构：revision 给后续 kind 扩展留升级位。 */
public record StructureDocument(int revision, List<StructureNode> nodes) {

    public static final int CURRENT_REVISION = 1;

    public static StructureDocument of(List<StructureNode> nodes) {
        return new StructureDocument(CURRENT_REVISION, nodes);
    }
}