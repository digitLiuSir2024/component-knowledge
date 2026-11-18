package com.cmbchina.application.knowledge.support;

import com.cmbchina.infrastructure.common.util.CUuid;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Maps;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** 将原始详情分解为独立对象，原始快照仍单独保留。 */
@Component
public class KnowledgeBaselineAssembler {
    @Autowired
    private KnowledgeMaintenanceCodec json;

    /** 原始上下文只读分解，不用候选覆盖原始内容。 */
    public ArrayNode collect(JsonNode snapshot) {
        Map<String, ObjectNode> objects = Maps.newLinkedHashMap();
        ObjectNode component = snapshot.path("component").deepCopy();
        ObjectNode details = json.object();
        for (String field : new String[]{"accepts", "presents", "behaviors", "stateFlows"}) {
            details.set(field, snapshot.path("componentDetails").path(field).deepCopy());
            if (details.path(field).isMissingNode()) {
                details.putNull(field);
            }
        }
        component.set("details", details);
        add(objects, "COMPONENT", component.path("id").asText(), component, "CONFIRMED");
        if (snapshot.path("page").isObject()) {
            add(objects, "PAGE", snapshot.path("page").path("id").asText(), snapshot.path("page"), "CONFIRMED");
        }
        flows(objects, snapshot);
        for (String group : new String[]{"businessKnowledge", "technicalKnowledge"}) {
            for (JsonNode rule : snapshot.path(group)) {
                knowledge(objects, rule, snapshot.path("knowledgeSources"));
            }
        }
        for (JsonNode api : snapshot.path("interfaceContexts")) {
            api(objects, api, snapshot.path("knowledgeSources"));
        }
        interactions(objects, snapshot, component);
        ArrayNode result = json.array();
        objects.values().forEach(result::add);
        return result;
    }

    private void flows(Map<String, ObjectNode> objects, JsonNode snapshot) {
        for (JsonNode row : snapshot.path("flowContexts")) {
            ObjectNode flow = json.object();
            flow.put("name", row.path("name").asText(""));
            flow.put("description", "");
            add(objects, "FLOW", row.path("targetId").asText(), flow, null);
            ObjectNode relation = json.object();
            relation.put("pageId", snapshot.path("page").path("id").asText());
            relation.put("flowId", row.path("targetId").asText());
            add(objects, "PAGE_FLOW", row.path("id").asText(), relation, null);
        }
    }

    private void interactions(Map<String, ObjectNode> objects, JsonNode snapshot, ObjectNode component) {
        for (JsonNode interaction : snapshot.path("interactions")) {
            ObjectNode content = interaction.deepCopy();
            content.put("componentId", component.path("id").asText());
            add(objects, "INTERACTION", content.path("id").asText(), content, null);
        }
        for (String group : new String[]{"inputs", "outputs"}) {
            for (JsonNode other : snapshot.path("collaboration").path(group)) {
                for (JsonNode relation : other.path("items")) {
                    relation(objects, relation);
                }
            }
        }
    }

    private void knowledge(Map<String, ObjectNode> objects, JsonNode rule, JsonNode sources) {
        String id = rule.path("id").asText(rule.path("knowledgeId").asText());
        ObjectNode content = rule.deepCopy();
        content.remove("conditions");
        if (sources.has(id)) {
            content.set("source", sources.path(id));
        }
        add(objects, "KNOWLEDGE", id, content, rule.path("status").asText("PENDING"));
        for (JsonNode condition : rule.path("conditions")) {
            ObjectNode relation = condition.deepCopy();
            relation.put("knowledgeId", id);
            add(objects, "APPLICABILITY", condition.path("id").asText(), relation, null);
            if ("FLOW".equals(condition.path("targetType").asText())) {
                ObjectNode flow = json.object();
                flow.put("name", "");
                flow.put("description", "");
                flow.put("unregisteredDescription", true);
                add(objects, "FLOW", condition.path("targetId").asText(), flow, null);
            }
        }
    }

    private void api(Map<String, ObjectNode> objects, JsonNode api, JsonNode sources) {
        if (!api.path("interfaceId").isTextual()) {
            return;
        }
        ObjectNode content = api.deepCopy();
        ArrayNode rules = json.array();
        for (JsonNode rule : api.path("rules")) {
            rules.add(rule.path("knowledgeId").asText());
            knowledge(objects, rule, sources);
        }
        content.remove("rules");
        content.set("ruleIds", rules);
        add(objects, "INTERFACE", api.path("interfaceId").asText(), content, "CONFIRMED");
    }

    private void relation(Map<String, ObjectNode> objects, JsonNode relation) {
        ObjectNode content = relation.deepCopy();
        content.put("targetComponentId", relation.path("target").path("id").asText());
        add(objects, "COMPONENT_RELATION", content.path("id").asText(), content, null);
        String interactionId = relation.path("interactionId").asText();
        if (!objects.containsKey("INTERACTION:" + interactionId)) {
            ObjectNode interaction = json.object();
            for (String field : new String[]{"action", "triggerCondition", "providedContent", "response"}) {
                interaction.set(field, relation.path(field));
            }
            interaction.put("componentId", relation.path("source").path("id").asText());
            add(objects, "INTERACTION", interactionId, interaction, null);
        }
    }

    private void add(Map<String, ObjectNode> objects, String type, String id, JsonNode content, String status) {
        ObjectNode value = json.object();
        value.put("objectType", type);
        value.put("objectId", id.isEmpty() ? CUuid.nextId() : id);
        value.put("confirmationStatus", status);
        value.put("lifecycleState", "ACTIVE");
        value.set("content", content.deepCopy());
        value.set("evidence", json.array());
        objects.putIfAbsent(type + ":" + value.path("objectId").asText(), value);
    }
}
