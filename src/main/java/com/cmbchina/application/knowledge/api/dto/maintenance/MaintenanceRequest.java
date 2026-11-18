package com.cmbchina.application.knowledge.api.dto.maintenance;
import lombok.Getter;
import lombok.Setter;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
/** 知识维护用例的显式请求字段，各用例独立校验必需项。 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaintenanceRequest {
    private Boolean objectOnly;
    @javax.validation.constraints.NotBlank
    private String systemCode;
    @javax.validation.constraints.NotBlank
    private String projectCode;
    private String pagePath;
    private String componentId;
    private String objectType;
    private String objectId;
    private String versionId;
    private String draftId;
    private Integer revision;
    private String requestId;
    private String origin;
    private String baseVersionId;
    private String targetVersionId;
    private Boolean confirmRestore;
    private String expectedCurrentVersionId;
    private String sourceRevision;
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private com.fasterxml.jackson.databind.JsonNode expectedPendingDraft;
    private String keyword;
    private Integer offset;
    private Integer limit;
    private com.fasterxml.jackson.databind.JsonNode meta;
    private java.util.List<KnowledgeCandidate> changes;
    private java.util.List<String> selectedChangeIds;
    private java.util.List<com.cmbchina.application.knowledge.api.dto.InterfaceKnowledgeRequest> interfaces;
}
