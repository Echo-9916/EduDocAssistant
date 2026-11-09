package com.cidas.edudoc.document.infrastructure;

import lombok.Data;

/** 文档版本历史行投影（验收决议④：版本列表只读界面，含可下载的源对象）。 */
@Data
public class DocumentVersionRow {

    private String versionId;
    private int versionNo;
    private String versionType;
    private String createTime;
    private String changeSummary;
    private String sourceObjectId;
}
