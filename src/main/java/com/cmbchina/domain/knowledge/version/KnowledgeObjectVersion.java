package com.cmbchina.domain.knowledge.version;
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
public class KnowledgeObjectVersion {
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
