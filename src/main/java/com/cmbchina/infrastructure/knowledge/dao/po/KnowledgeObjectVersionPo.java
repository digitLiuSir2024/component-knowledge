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
public class KnowledgeObjectVersionPo {
    private String id;
    private String systemCode;
    private String projectCode;
    private String objectType;
    private String objectId;
    private int versionNo;
    private String previousVersionId;
    private int schemaVersion;
    private String versionPurpose;
    private String lifecycleState;
    private String confirmationStatus;
    private String content;
    private String evidence;
    private String draftId;
    private String createdAt;
}
