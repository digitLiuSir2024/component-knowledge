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
public class KnowledgeChangeDraft {
    private String id;
    private String systemCode;
    private String projectCode;
    private String componentId;
    private String origin;
    private int revision;
    private String status;
    private String baseVersionId;
    private String objectBases;
    private String payload;
    private String selectedChanges;
    private String publishedResult;
    private String requestId;
    private String requestDigest;
    private String createdAt;
    private String updatedAt;
}
