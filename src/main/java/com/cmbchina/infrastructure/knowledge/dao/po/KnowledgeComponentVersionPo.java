package com.cmbchina.infrastructure.knowledge.dao.po;
import lombok.Getter;
import lombok.Setter;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
/** 知识版本持久化边界数据。 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeComponentVersionPo {
    private String id;
    private String componentId;
    private int versionNo;
    private String previousVersionId;
    private String manifest;
    private String draftId;
    private String summary;
    private String restoredFromVersionId;
    private boolean current;
    private String createdAt;
}
