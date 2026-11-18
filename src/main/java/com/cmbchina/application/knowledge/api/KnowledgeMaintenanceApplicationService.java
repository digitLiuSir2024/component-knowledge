package com.cmbchina.application.knowledge.api;
import com.cmbchina.application.knowledge.api.dto.maintenance.MaintenanceRequest;
import com.fasterxml.jackson.databind.JsonNode;
/** 知识维护应用边界。 */
public interface KnowledgeMaintenanceApplicationService {
    /** readContext知识维护用例。 */
    JsonNode readContext(MaintenanceRequest request);

    /** searchObjects知识维护用例。 */
    JsonNode searchObjects(MaintenanceRequest request);

    /** readObjectVersion知识维护用例。 */
    JsonNode readObjectVersion(MaintenanceRequest request);

    /** readObjectHistory知识维护用例。 */
    JsonNode readObjectHistory(MaintenanceRequest request);

    /** 分页发现当前组件待确认更新。 */
    JsonNode listDrafts(MaintenanceRequest request);

    /** createDraft知识维护用例。 */
    JsonNode createDraft(MaintenanceRequest request);

    /** updateDraft知识维护用例。 */
    JsonNode updateDraft(MaintenanceRequest request);

    /** readDraft知识维护用例。 */
    JsonNode readDraft(MaintenanceRequest request);

    /** discardDraft知识维护用例。 */
    JsonNode discardDraft(MaintenanceRequest request);

    /** publishDraft知识维护用例。 */
    JsonNode publishDraft(MaintenanceRequest request);

    /** listComponentVersions知识维护用例。 */
    JsonNode listComponentVersions(MaintenanceRequest request);

    /** createRestoreDraft知识维护用例。 */
    JsonNode createRestoreDraft(MaintenanceRequest request);

    /** 永久清理组件知识。 */
    void purgeComponent(MaintenanceRequest request);

    /** createBaselineDraft知识维护用例。 */
    JsonNode createBaselineDraft(MaintenanceRequest request);
}
