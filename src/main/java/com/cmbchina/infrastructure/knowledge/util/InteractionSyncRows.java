package com.cmbchina.infrastructure.knowledge.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Maps;
import java.util.Map;

/** 交互版本内容到基础表字段的纯映射，可独立测试。 */
public final class InteractionSyncRows {
    private InteractionSyncRows() {
    }

    public static Map<String, Object> interaction(String id, JsonNode content, int fallbackOrder) {
        Map<String, Object> row = Maps.newHashMap();
        row.put("id", id);
        row.put("componentId", text(content, "componentId"));
        row.put("actionName", text(content, "action"));
        row.put("triggerCondition", nullable(content, "triggerCondition"));
        row.put("providedContent", nullable(content, "providedContent"));
        row.put("responseText", nullable(content, "response"));
        row.put("sortOrder", order(content, fallbackOrder));
        return row;
    }

    public static Map<String, Object> relation(String id, JsonNode content, int fallbackOrder) {
        Map<String, Object> row = Maps.newHashMap();
        row.put("id", id);
        row.put("interactionId", text(content, "interactionId"));
        row.put("targetComponentId", text(content, "targetComponentId"));
        row.put("effectText", text(content, "effect"));
        row.put("conditionText", nullable(content, "condition"));
        row.put("sortOrder", order(content, fallbackOrder));
        return row;
    }

    public static int order(JsonNode content, int fallback) {
        JsonNode value = content.path("sortOrder");
        return value.isNumber() ? value.asInt() : fallback;
    }

    public static String text(JsonNode content, String field) {
        return nullable(content, field);
    }

    public static String nullable(JsonNode content, String field) {
        JsonNode value = content.path(field);
        return value.isNull() || value.isMissingNode() ? null : value.asText();
    }
}
