package com.cmbchina.application.knowledge.support;

import com.cmbchina.infrastructure.common.ResponseEnum;
import com.cmbchina.infrastructure.common.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** INTERACTION 与 COMPONENT_RELATION 的共享校验规则，不依赖 Spring。 */
public final class InteractionRules {
    private static final String INTERACTION = "INTERACTION";
    private static final String RELATION = "COMPONENT_RELATION";

    private InteractionRules() {
    }

    /** 交互正文必须归属正式组件，动作非空，可选字段只能是字符串或 null。 */
    public static void validateInteraction(JsonNode content) {
        requireObject(content);
        requireText(content, "componentId", ResponseEnum.KNOWLEDGE_SCOPE_MISMATCH);
        require(!text(content, "componentId").startsWith("new:"), ResponseEnum.KNOWLEDGE_SCOPE_MISMATCH);
        requireText(content, "action", ResponseEnum.KNOWLEDGE_OBJECT_INVALID);
        optionalText(content, "triggerCondition");
        optionalText(content, "providedContent");
        optionalText(content, "response");
        optionalInteger(content, "sortOrder");
    }

    /** 联动正文必须指向正式交互与目标组件，影响非空。 */
    public static void validateRelation(JsonNode content) {
        requireObject(content);
        requireText(content, "interactionId", ResponseEnum.KNOWLEDGE_OBJECT_INVALID);
        requireText(content, "targetComponentId", ResponseEnum.KNOWLEDGE_OBJECT_INVALID);
        requireText(content, "effect", ResponseEnum.KNOWLEDGE_OBJECT_INVALID);
        optionalText(content, "condition");
        optionalInteger(content, "sortOrder");
    }

    /** 图内关系必须指向同图交互，且不能自环。 */
    public static void validateGraph(JsonNode objects) {
        require(objects != null && objects.isArray(), ResponseEnum.KNOWLEDGE_OBJECT_INVALID);
        Map<String, String> owners = new HashMap<String, String>();
        Set<String> components = new HashSet<String>();
        for (JsonNode object : objects) {
            String type = object.path("objectType").asText();
            if (isComponent(type)) {
                components.add(object.path("objectId").asText());
            }
            if (INTERACTION.equals(type)) {
                validateInteraction(object.path("content"));
                owners.put(object.path("objectId").asText(), text(object.path("content"), "componentId"));
            }
        }
        for (JsonNode object : objects) {
            if (!RELATION.equals(object.path("objectType").asText())) {
                continue;
            }
            JsonNode content = object.path("content");
            validateRelation(content);
            String owner = owners.get(text(content, "interactionId"));
            require(owner != null, ResponseEnum.KNOWLEDGE_DEPENDENCY_INVALID);
            require(!owner.equals(text(content, "targetComponentId")), ResponseEnum.KNOWLEDGE_DEPENDENCY_INVALID);
        }
    }

    private static boolean isComponent(String type) {
        return "COMPONENT".equals(type);
    }

    private static void requireObject(JsonNode content) {
        require(content != null && content.isObject(), ResponseEnum.KNOWLEDGE_OBJECT_INVALID);
    }

    private static void optionalText(JsonNode content, String field) {
        JsonNode value = content.path(field);
        require(value.isMissingNode() || value.isNull() || value.isTextual(), ResponseEnum.KNOWLEDGE_OBJECT_INVALID);
    }

    private static String text(JsonNode content, String field) {
        JsonNode value = content.path(field);
        return value.isTextual() ? value.asText().trim() : "";
    }

    private static void optionalInteger(JsonNode content, String field) {
        JsonNode value = content.path(field);
        require(value.isMissingNode() || value.isNull() || value.isIntegralNumber(), ResponseEnum.KNOWLEDGE_OBJECT_INVALID);
    }

    private static void requireText(JsonNode content, String field, ResponseEnum error) {
        require(!text(content, field).isEmpty(), error);
    }

    private static void require(boolean condition, ResponseEnum error) {
        if (!condition) {
            throw new BusinessException(error);
        }
    }
}
