package com.cmbchina.application.knowledge.support;

import com.cmbchina.domain.knowledge.support.KnowledgeVersionSupport;
import com.cmbchina.domain.knowledge.version.KnowledgeComponentVersion;
import com.cmbchina.domain.knowledge.version.KnowledgeCurrentReference;
import com.cmbchina.domain.knowledge.version.KnowledgeObjectVersion;
import com.cmbchina.domain.knowledge.version.KnowledgeVersionQuery;
import com.cmbchina.infrastructure.common.ResponseEnum;
import com.cmbchina.infrastructure.common.util.CUuid;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** 同一应用事务中的版本写入，调用方先锁组件及草稿。 */
@Component
public class KnowledgeVersionWriter {
    @Autowired
    private KnowledgeVersionSupport support;
    @Autowired
    private KnowledgeMaintenanceCodec json;

    /** 对象头忽略迁移历史专用版本；仅有基线时保留初始化状态。 */
    public KnowledgeObjectVersion head(KnowledgeVersionQuery query) {
        List<KnowledgeObjectVersion> values = support.lockObject(query);
        KnowledgeObjectVersion head = null;
        for (KnowledgeObjectVersion value : values) {
            if ("NORMAL".equals(value.getVersionPurpose())) {
                head = value;
            }
        }
        return head == null && !values.isEmpty() ? values.get(values.size() - 1) : head;
    }

    /** 新增完整对象版本，不能覆盖已经保存的历史内容。 */
    public KnowledgeObjectVersion save(KnowledgeVersionQuery query, KnowledgeObjectVersion proposed, String expectedHead) {
        List<KnowledgeObjectVersion> rows = support.lockObject(query);
        KnowledgeObjectVersion head = head(query);
        if (expectedHead != null) {
            json.require(head != null && expectedHead.equals(head.getId()), ResponseEnum.KNOWLEDGE_BASE_CONFLICT);
        }
        if (head != null && json.parse(head.getContent()).equals(json.parse(proposed.getContent()))
                && java.util.Objects.equals(head.getConfirmationStatus(), proposed.getConfirmationStatus())
                && java.util.Objects.equals(head.getLifecycleState(), proposed.getLifecycleState())) {
            return head;
        }
        int next = rows.isEmpty() ? 1 : rows.get(rows.size() - 1).getVersionNo() + 1;
        KnowledgeObjectVersion value = KnowledgeObjectVersion.builder().id(CUuid.nextId()).systemCode(query.getSystemCode())
                .projectCode(query.getProjectCode()).objectType(query.getObjectType()).objectId(query.getObjectId()).versionNo(next)
                .previousVersionId(head == null ? null : head.getId()).schemaVersion(1).versionPurpose(proposed.getVersionPurpose())
                .lifecycleState(proposed.getLifecycleState()).confirmationStatus(proposed.getConfirmationStatus())
                .content(contractIds(proposed, query)).evidence(proposed.getEvidence()).draftId(proposed.getDraftId())
                .createdAt(Instant.now().toString()).build();
        support.insertObject(value);
        return value;
    }

    private String contractIds(KnowledgeObjectVersion proposed, KnowledgeVersionQuery query) {
        if (!"INTERFACE".equals(query.getObjectType())) {
            return proposed.getContent();
        }
        JsonNode content = json.parse(proposed.getContent());
        assignIds(content, Maps.newHashMap());
        return content.toString();
    }

    private void assignIds(JsonNode value, java.util.Map<String, String> ids) {
        if (value.isObject()) {
            com.fasterxml.jackson.databind.node.ObjectNode object = (com.fasterxml.jackson.databind.node.ObjectNode) value;
            String id = object.path("id").asText();
            if (id.startsWith("new:")) {
                object.put("id", ids.computeIfAbsent(id, key -> CUuid.nextId()));
            }
        }
        value.elements().forEachRemaining(child -> assignIds(child, ids));
    }

    /** 发布组件清单并替换可重建当前引用索引。 */
    public KnowledgeComponentVersion publish(KnowledgeVersionQuery query, JsonNode manifest, String draftId, String restoredFrom, String summary) {
        KnowledgeComponentVersion previous = support.currentVersion(query);
        KnowledgeComponentVersion value = KnowledgeComponentVersion.builder().id(CUuid.nextId()).componentId(query.getComponentId())
                .versionNo(previous == null ? 1 : previous.getVersionNo() + 1).previousVersionId(previous == null ? null : previous.getId())
                .manifest(manifest.toString()).draftId(draftId).summary(summary).restoredFromVersionId(restoredFrom).current(true)
                .createdAt(Instant.now().toString()).build();
        support.clearCurrent(query);
        support.insertComponentVersion(value);
        support.clearReferences(query);
        for (JsonNode entry : manifest) {
            support.insertReference(
                    KnowledgeCurrentReference.builder().id(CUuid.nextId()).systemCode(query.getSystemCode()).projectCode(query.getProjectCode())
                            .componentId(query.getComponentId()).componentVersionId(value.getId()).objectType(entry.path("objectType").asText())
                            .objectId(entry.path("objectId").asText()).objectVersionId(entry.path("versionId").asText()).build());
        }
        return value;
    }

    /** 固定顺序获取已存在对象锁，避免候选次序导致交叉锁。 */
    public void lockAll(KnowledgeVersionQuery query, JsonNode changes) {
        List<String> keys = Lists.newArrayList();
        for (JsonNode change : changes) {
            keys.add(change.path("objectType").asText() + ":" + change.path("objectId").asText());
        }
        java.util.Collections.sort(keys);
        for (String key : keys) {
            int separator = key.indexOf(':');
            query.setObjectType(key.substring(0, separator));
            query.setObjectId(key.substring(separator + 1));
            support.lockObject(query);
        }
    }
}
