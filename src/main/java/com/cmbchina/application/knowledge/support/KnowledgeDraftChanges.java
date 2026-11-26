package com.cmbchina.application.knowledge.support;

import com.cmbchina.application.knowledge.api.dto.maintenance.KnowledgeCandidate;
import com.cmbchina.domain.knowledge.support.KnowledgeVersionSupport;
import com.cmbchina.domain.knowledge.version.KnowledgeObjectVersion;
import com.cmbchina.domain.knowledge.version.KnowledgeVersionQuery;
import com.cmbchina.infrastructure.common.ResponseEnum;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** 草稿候选的选择、预览与引用完整性。 */
@Component
public class KnowledgeDraftChanges {
    @Autowired
    private KnowledgeMaintenanceCodec json;
    @Autowired
    private KnowledgePayloadValidator validator;
    @Autowired
    private KnowledgeVersionSupport support;
    @Autowired
    private VersionedKnowledgeLoader loader;

    /** 校验稳定变更编号及对象内重复操作。 */
    public void validate(JsonNode changes) {
        json.require(changes.isArray(), ResponseEnum.KNOWLEDGE_OBJECT_INVALID);
        Set<String> ids = Sets.newHashSet();
        Set<String> objects = Sets.newHashSet();
        for (JsonNode node : changes) {
            KnowledgeCandidate value = json.convert(node, KnowledgeCandidate.class);
            validator.validate(value);
            json.require(ids.add(value.getChangeId()) && objects.add(key(node)), ResponseEnum.KNOWLEDGE_OBJECT_INVALID);
        }
    }

    /** 接受原子组必须完整，不允许未声明的选择。 */
    public void selection(JsonNode changes, Set<String> selected) {
        Set<String> known = Sets.newHashSet();
        Map<String, Boolean> groups = Maps.newHashMap();
        for (JsonNode change : changes) {
            String id = change.path("changeId").asText();
            known.add(id);
            String group = change.path("atomicGroupId").asText("");
            if (!group.isEmpty()) {
                Boolean previous = groups.putIfAbsent(group, selected.contains(id));
                json.require(previous == null || previous.booleanValue() == selected.contains(id), ResponseEnum.KNOWLEDGE_DEPENDENCY_INVALID);
            }
        }
        json.require(known.containsAll(selected), ResponseEnum.KNOWLEDGE_OBJECT_INVALID);
    }

    /** 预览复用所有未变对象，仅替换已接受的候选。 */
    public ArrayNode preview(KnowledgeVersionQuery query, JsonNode before, JsonNode changes, Set<String> selected) {
        Map<String, JsonNode> values = Maps.newLinkedHashMap();
        for (JsonNode object : before) {
            values.put(key(object), object.deepCopy());
        }
        String componentId = primaryComponent(before);
        for (JsonNode change : changes) {
            if (!selected.contains(change.path("changeId").asText())) {
                continue;
            }
            String key = key(change);
            if ("COMPONENT".equals(change.path("objectType").asText())) {
                json.require(componentId.equals(change.path("objectId").asText()), ResponseEnum.KNOWLEDGE_SCOPE_MISMATCH);
            }
            String operation = change.path("operation").asText();
            if ("UNLINK".equals(operation)) {
                json.require(!"COMPONENT".equals(change.path("objectType").asText()), ResponseEnum.KNOWLEDGE_OBJECT_INVALID);
                values.remove(key);
            } else if ("LINK".equals(operation) || "UPGRADE_REFERENCE".equals(operation)) {
                query.setVersionId(change.path("targetVersionId").asText());
                KnowledgeObjectVersion target = support.objectVersion(query);
                json.require(target != null && target.getObjectType().equals(change.path("objectType").asText())
                        && target.getObjectId().equals(change.path("objectId").asText()), ResponseEnum.KNOWLEDGE_REFERENCE_INVALID);
                values.put(key, loader.object(target));
            } else {
                ObjectNode object = update(values.get(key), change);
                values.put(key, object);
            }
        }
        ArrayNode result = json.array();
        values.values().forEach(result::add);
        references(result);
        return loader.prepareRelations(result, query);
    }

    private ObjectNode update(JsonNode old, JsonNode change) {
        String operation = change.path("operation").asText();
        json.require("CREATE".equals(operation) ? old == null : old != null, ResponseEnum.KNOWLEDGE_REFERENCE_INVALID);
        ObjectNode value = old == null ? json.object() : old.deepCopy();
        if (old != null && "COMPONENT".equals(change.path("objectType").asText()) && "UPDATE".equals(operation)) {
            json.require(old.path("content").path("componentId").equals(change.path("content").path("componentId")),
                    ResponseEnum.KNOWLEDGE_OBJECT_INVALID);
        }
        value.put("objectType", change.path("objectType").asText());
        value.put("objectId", change.path("objectId").asText());
        value.put("lifecycleState", "EXPIRE".equals(operation) ? "EXPIRED" : value.path("lifecycleState").asText("ACTIVE"));
        if (!"EXPIRE".equals(operation)) {
            value.set("content", change.path("content").deepCopy());
        }
        if (!change.path("confirmationStatus").isNull()) {
            value.set("confirmationStatus", change.path("confirmationStatus"));
        }
        value.set("evidence", change.path("evidence"));
        return value;
    }

    /** 关系必须指向同一清单中存在的对象。 */
    public void references(JsonNode objects) {
        Set<String> keys = Sets.newHashSet();
        for (JsonNode object : objects) {
            keys.add(key(object));
        }
        for (JsonNode object : objects) {
            JsonNode content = object.path("content");
            String type = object.path("objectType").asText();
            if ("APPLICABILITY".equals(type)) {
                exists(keys, "KNOWLEDGE", content.path("knowledgeId").asText());
                exists(keys, content.path("targetType").asText(), content.path("targetId").asText());
            } else if ("INTERFACE".equals(type)) {
                for (JsonNode id : content.path("ruleIds")) {
                    exists(keys, "KNOWLEDGE", id.asText());
                }
            } else if ("COMPONENT_RELATION".equals(type)) {
                exists(keys, "INTERACTION", content.path("interactionId").asText());
            } else if ("PAGE_FLOW".equals(type)) {
                exists(keys, "PAGE", content.path("pageId").asText());
                exists(keys, "FLOW", content.path("flowId").asText());
            }
        }
        InteractionRules.validateGraph(objects);
    }

    private void exists(Set<String> keys, String type, String id) {
        json.require(keys.contains(type + ":" + id), ResponseEnum.KNOWLEDGE_DEPENDENCY_INVALID);
    }

    private String primaryComponent(JsonNode objects) {
        String id = null;
        for (JsonNode object : objects) {
            if ("COMPONENT".equals(object.path("objectType").asText())) {
                json.require(id == null, ResponseEnum.KNOWLEDGE_SCOPE_MISMATCH);
                id = object.path("objectId").asText();
            }
        }
        json.require(json.text(id), ResponseEnum.KNOWLEDGE_SCOPE_MISMATCH);
        return id;
    }

    private String key(JsonNode value) {
        return value.path("objectType").asText() + ":" + value.path("objectId").asText();
    }
}
