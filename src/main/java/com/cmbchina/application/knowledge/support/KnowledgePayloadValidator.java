package com.cmbchina.application.knowledge.support;

import com.cmbchina.application.knowledge.api.dto.maintenance.KnowledgeCandidate;
import com.cmbchina.infrastructure.common.ResponseEnum;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** 按对象类型校验知识载荷，不接受任意SQL或未知对象。 */
@Component
public class KnowledgePayloadValidator {
    @Autowired
    private KnowledgeMaintenanceCodec json;

    /** 校验候选身份、操作及独立载荷。 */
    public void validate(KnowledgeCandidate value) {
        json.require(value != null && json.text(value.getChangeId()) && json.text(value.getObjectId()), invalid());
        json.require(Arrays
                .asList("COMPONENT", "PAGE", "FLOW", "KNOWLEDGE", "INTERFACE", "APPLICABILITY", "PAGE_FLOW", "INTERACTION", "COMPONENT_RELATION")
                .contains(value.getObjectType()), invalid());
        json.require(Arrays.asList("CREATE", "UPDATE", "LINK", "UNLINK", "UPGRADE_REFERENCE", "EXPIRE").contains(value.getOperation()), invalid());
        if ("CREATE".equals(value.getOperation()) || "UPDATE".equals(value.getOperation())) {
            content(value.getObjectType(), value.getContent());
        }
        if ("CREATE".equals(value.getOperation())) {
            json.require(value.getObjectId().startsWith("new:"), invalid());
        }
        if ("LINK".equals(value.getOperation()) || "UPGRADE_REFERENCE".equals(value.getOperation())) {
            json.require(json.text(value.getTargetVersionId()), invalid());
        }
        if (value.getConfirmationStatus() != null) {
            json.require(Arrays.asList("CONFIRMED", "PENDING").contains(value.getConfirmationStatus()), invalid());
        }
        evidence(value);
    }

    private void evidence(KnowledgeCandidate value) {
        json.require(value.getEvidence() != null && value.getEvidence().isArray(), invalid());
        for (JsonNode item : value.getEvidence()) {
            fields(item, "kind", "source", "excerpt");
            json.require(Arrays.asList("FACT", "REQUIREMENT", "INFERENCE").contains(item.path("kind").asText()), invalid());
        }
    }

    /** 校验每种对象必需字段及集合形状。 */
    public void content(String type, JsonNode value) {
        json.require(value != null && value.isObject(), invalid());
        if ("COMPONENT".equals(type)) {
            nullableFields(value, "name", "description");
            details(value.path("details"));
            return;
        }
        if (Arrays.asList("PAGE", "FLOW").contains(type)) {
            nullableFields(value, "name", "description");
            return;
        }
        if ("KNOWLEDGE".equals(type)) {
            fields(value, "title", "type", "kind", "content");
            json.require(Arrays.asList("BUSINESS", "TECHNICAL").contains(value.path("type").asText()), invalid());
            json.require(Arrays.asList("BUSINESS_RULE", "METRIC_RULE", "TECHNICAL_RULE", "CONFLICT").contains(value.path("kind").asText()),
                    invalid());
            return;
        }
        if ("INTERFACE".equals(type)) {
            fields(value, "method", "path", "address", "serviceCode");
            arrays(value, "inputs", "outputs", "errors", "examples", "ruleIds");
            contract(value);
            return;
        }
        if ("APPLICABILITY".equals(type)) {
            fields(value, "knowledgeId", "targetType", "targetId");
            json.require(value.path("unconditional").isBoolean(), invalid());
            json.require(Arrays.asList("COMPONENT", "PAGE", "FLOW", "INTERFACE").contains(value.path("targetType").asText()), invalid());
            return;
        }
        if ("PAGE_FLOW".equals(type)) {
            fields(value, "pageId", "flowId");
            return;
        }
        if ("INTERACTION".equals(type)) {
            fields(value, "componentId", "action");
            return;
        }
        if ("COMPONENT_RELATION".equals(type)) {
            fields(value, "interactionId", "targetComponentId", "effect");
            return;
        }
        json.require(false, invalid());
    }

    /** 未登记集合与明确空集合保持区别。 */
    public void details(JsonNode value) {
        json.require(value.isObject(), invalid());
        for (String field : Arrays.asList("accepts", "presents", "behaviors", "stateFlows")) {
            json.require(value.has(field) && (value.path(field).isNull() || value.path(field).isArray()), invalid());
        }
        for (JsonNode item : value.path("accepts")) {
            fields(item, "label");
            nullableString(item, "detail");
        }
        presents(value.path("presents"));
        for (JsonNode item : value.path("behaviors")) {
            json.require(item.isTextual(), invalid());
        }
        for (JsonNode item : value.path("stateFlows")) {
            strings(item, "label", "managedBy", "input", "update", "impact");
        }
    }

    private void presents(JsonNode values) {
        for (JsonNode item : values) {
            fields(item, "label");
            nullableString(item, "note");
            JsonNode variants = item.path("variants");
            json.require(variants.isMissingNode() || variants.isNull() || variants.isArray(), invalid());
            for (JsonNode variant : variants) {
                json.require(variant.isTextual(), invalid());
            }
        }
    }

    private void fields(JsonNode value, String... fields) {
        strings(value, fields);
        for (String field : fields) {
            json.require(json.text(value.path(field).asText()), invalid());
        }
    }

    private void strings(JsonNode value, String... fields) {
        for (String field : fields) {
            json.require(value.path(field).isTextual(), invalid());
        }
    }

    private void arrays(JsonNode value, String... fields) {
        for (String field : fields) {
            json.require(value.path(field).isArray(), invalid());
        }
    }

    private void contract(JsonNode value) {
        for (JsonNode group : value.path("inputs")) {
            fields(group, "location");
            arrays(group, "fields");
            contractFields(group.path("fields"));
        }
        contractFields(value.path("outputs"));
        for (JsonNode error : value.path("errors")) {
            fields(error, "id", "code", "description", "triggerCondition");
            json.require(error.path("httpStatus").isInt(), invalid());
        }
        for (JsonNode example : value.path("examples")) {
            fields(example, "id", "name", "request", "response");
            strings(example, "description");
            json.parse(example.path("request").asText());
            json.parse(example.path("response").asText());
        }
        for (JsonNode id : value.path("ruleIds")) {
            json.require(id.isTextual() && json.text(id.asText()), invalid());
        }
    }

    private void contractFields(JsonNode values) {
        json.require(values.isArray(), invalid());
        for (JsonNode field : values) {
            fields(field, "id", "name", "path", "type", "location");
            json.require(field.path("required").isBoolean() && field.path("nullable").isBoolean(), invalid());
            arrays(field, "children");
            contractFields(field.path("children"));
        }
    }

    private ResponseEnum invalid() {
        return ResponseEnum.KNOWLEDGE_OBJECT_INVALID;
    }

    private void nullableString(JsonNode value, String field) {
        JsonNode node = value.path(field);
        json.require(node.isMissingNode() || node.isNull() || node.isTextual(), invalid());
    }

    private void nullableFields(JsonNode value, String... fields) {
        for (String field : fields) {
            json.require(value.has(field), invalid());
            nullableString(value, field);
        }
    }
}
