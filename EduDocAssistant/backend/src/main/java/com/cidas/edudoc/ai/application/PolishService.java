package com.cidas.edudoc.ai.application;

import java.util.List;

/**
 * 润色（C-07，D3 决策）：同步对照 → 采纳成版（派生 .txt → EDIT 新版本 → 简化解析向量化）→ 一次撤销。
 * 采纳替换按「最新版本全文文本匹配」：段落来自预览块文本，恰为全文切片（C-01 密铺不变量保证）。
 */
public interface PolishService {

    record PolishRowView(String polishId, String sourceText, String polishedText, String decision) {
    }

    record AppliedView(String polishId, String documentId, String versionId, int versionNo, String documentStatus) {
    }

    /** 段落文本合计 ≤5000（40001）；只送选中段落（§4.2 不把整份文档发给模型）。 */
    List<PolishRowView> polish(String documentId, List<String> paragraphTexts);

    /** ACCEPT 用润色文本、PARTIAL 用客户端回传文本；单事务成版。 */
    AppliedView apply(String polishId, String decision, String acceptedText);

    void reject(String polishId);

    /** 仅最近一次未撤销采纳可撤销：弹出版本链顶层 EDIT，current 回拨前一版本。 */
    void revert(String polishId);
}
