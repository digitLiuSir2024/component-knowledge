package com.cmbchina.domain.knowledge.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** legacy 白名单解析与列名约束测试。 */
public class LegacyEntityTest {
    @Test
    public void parsesEnumNameAndTableName() {
        assertEquals(LegacyEntity.COMPONENT_INTERACTION, LegacyEntity.parse("component_interaction"));
        assertEquals(LegacyEntity.COMPONENT_INTERACTION, LegacyEntity.parse("KNOWLEDGE_COMPONENT_INTERACTION"));
        assertNull(LegacyEntity.parse("knowledge_secret"));
    }

    @Test
    public void exposesOnlyWhitelistedColumns() {
        LegacyEntity entity = LegacyEntity.COMPONENT_RELATION;
        assertTrue(entity.has("target_component_id"));
        assertFalse(entity.has("id;drop"));
        assertEquals("id", entity.primaryKey());
        assertEquals(14, LegacyEntity.values().length);
    }
}
