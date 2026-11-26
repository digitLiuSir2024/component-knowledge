package com.cmbchina.application.knowledge.support;

import com.cmbchina.infrastructure.common.ResponseEnum;
import com.cmbchina.infrastructure.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/** 交互与联动规则的纯逻辑测试，不启动 Spring。 */
public class InteractionRulesTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void acceptsValidInteraction() {
        ObjectNode content = mapper.createObjectNode();
        content.put("componentId", "component-1");
        content.put("action", "筛选");
        content.putNull("triggerCondition");
        InteractionRules.validateInteraction(content);
    }

    @Test
    public void rejectsNonIntegerSortOrder() {
        ObjectNode content = mapper.createObjectNode();
        content.put("componentId", "component-1");
        content.put("action", "筛选");
        content.put("sortOrder", "ten");
        assertCode(ResponseEnum.KNOWLEDGE_OBJECT_INVALID, () -> InteractionRules.validateInteraction(content));
    }

    @Test
    public void rejectsTemporaryComponentId() {
        ObjectNode content = mapper.createObjectNode();
        content.put("componentId", "new:component");
        content.put("action", "筛选");
        assertCode(ResponseEnum.KNOWLEDGE_SCOPE_MISMATCH, () -> InteractionRules.validateInteraction(content));
    }

    @Test
    public void acceptsGraphWithResolvedRelation() {
        ArrayNode objects = mapper.createArrayNode();
        objects.add(object("COMPONENT", "component-1"));
        objects.add(object("COMPONENT", "component-2"));
        ObjectNode interaction = object("INTERACTION", "interaction-1");
        interaction.set("content", interactionContent("component-1"));
        objects.add(interaction);
        ObjectNode relation = object("COMPONENT_RELATION", "relation-1");
        ObjectNode relationContent = mapper.createObjectNode();
        relationContent.put("interactionId", "interaction-1");
        relationContent.put("targetComponentId", "component-2");
        relationContent.put("effect", "更新结果");
        relation.set("content", relationContent);
        objects.add(relation);
        InteractionRules.validateGraph(objects);
    }

    @Test
    public void rejectsSelfRelation() {
        ArrayNode objects = mapper.createArrayNode();
        ObjectNode interaction = object("INTERACTION", "interaction-1");
        interaction.set("content", interactionContent("component-1"));
        objects.add(interaction);
        ObjectNode relation = object("COMPONENT_RELATION", "relation-1");
        ObjectNode relationContent = mapper.createObjectNode();
        relationContent.put("interactionId", "interaction-1");
        relationContent.put("targetComponentId", "component-1");
        relationContent.put("effect", "更新结果");
        relation.set("content", relationContent);
        objects.add(relation);
        assertCode(ResponseEnum.KNOWLEDGE_DEPENDENCY_INVALID, () -> InteractionRules.validateGraph(objects));
    }

    private ObjectNode object(String type, String id) {
        ObjectNode result = mapper.createObjectNode();
        result.put("objectType", type);
        result.put("objectId", id);
        return result;
    }

    private ObjectNode interactionContent(String componentId) {
        ObjectNode content = mapper.createObjectNode();
        content.put("componentId", componentId);
        content.put("action", "筛选");
        return content;
    }

    private void assertCode(ResponseEnum expected, Runnable action) {
        try {
            action.run();
            fail("expected business exception " + expected);
        } catch (BusinessException exception) {
            assertEquals(expected.getCode(), exception.getCode());
        }
    }
}
