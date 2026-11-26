package com.cmbchina.infrastructure.knowledge.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/** 交互版本内容到基础表字段的映射测试。 */
public class InteractionSyncRowsTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void mapsInteractionRow() {
        ObjectNode content = mapper.createObjectNode();
        content.put("componentId", "component-1");
        content.put("action", "筛选");
        content.putNull("triggerCondition");
        content.put("providedContent", "条件");
        content.put("response", "响应");
        content.put("sortOrder", 30);
        Map<String, Object> row = InteractionSyncRows.interaction("interaction-1", content, 99);
        assertEquals("interaction-1", row.get("id"));
        assertEquals("component-1", row.get("componentId"));
        assertEquals("筛选", row.get("actionName"));
        assertNull(row.get("triggerCondition"));
        assertEquals(30, row.get("sortOrder"));
    }

    @Test
    public void mapsRelationRowWithFallbackOrder() {
        ObjectNode content = mapper.createObjectNode();
        content.put("interactionId", "interaction-1");
        content.put("targetComponentId", "component-2");
        content.put("effect", "更新");
        content.putNull("condition");
        Map<String, Object> row = InteractionSyncRows.relation("relation-1", content, 20);
        assertEquals("relation-1", row.get("id"));
        assertEquals("interaction-1", row.get("interactionId"));
        assertEquals("更新", row.get("effectText"));
        assertNull(row.get("conditionText"));
        assertEquals(20, row.get("sortOrder"));
    }
}
