package com.cmbchina.application.knowledge.api.dto.maintenance;
import lombok.Getter;
import lombok.Setter;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
/** 草稿内稳定标识的候选变化。 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeCandidate {
    private String changeId;
    private String operation;
    private String objectType;
    private String objectId;
    private String baseObjectVersionId;
    private String targetVersionId;
    private com.fasterxml.jackson.databind.JsonNode content;
    private String confirmationStatus;
    private com.fasterxml.jackson.databind.JsonNode evidence;
    private String atomicGroupId;
}
