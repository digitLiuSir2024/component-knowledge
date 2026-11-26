package com.cmbchina.infrastructure.knowledge.supportimpl;

import com.cmbchina.domain.knowledge.support.InteractionSyncSupport;
import com.cmbchina.domain.knowledge.version.KnowledgeObjectVersion;
import com.cmbchina.infrastructure.knowledge.dao.mapper.InteractionSyncMapper;
import com.cmbchina.infrastructure.knowledge.util.InteractionSyncRows;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 发布后把交互版本对象同步回基础表，保持两处内容一致。 */
@Component
public class InteractionSyncSupportImpl implements InteractionSyncSupport {
    @Autowired
    private InteractionSyncMapper mapper;
    @Autowired
    private ObjectMapper objects;
    @Value("${knowledge.legacy-interaction-sync.enabled:true}")
    private boolean enabled;

    public void sync(String componentId, List<KnowledgeObjectVersion> values) {
        if (!enabled || componentId == null || componentId.trim().isEmpty()) {
            return;
        }
        Map<String, JsonNode> interactions = Maps.newLinkedHashMap();
        Map<String, JsonNode> relations = Maps.newLinkedHashMap();
        for (KnowledgeObjectVersion value : values) {
            JsonNode content = parse(value.getContent());
            if ("INTERACTION".equals(value.getObjectType())) {
                interactions.put(value.getObjectId(), content);
            } else if ("COMPONENT_RELATION".equals(value.getObjectType())) {
                relations.put(value.getObjectId(), content);
            }
        }
        int index = 0;
        for (Map.Entry<String, JsonNode> entry : interactions.entrySet()) {
            mapper.upsertInteraction(InteractionSyncRows.interaction(entry.getKey(), entry.getValue(), index++ * 10));
        }
        index = 0;
        List<String> retainedRelations = Lists.newArrayList();
        for (Map.Entry<String, JsonNode> entry : relations.entrySet()) {
            JsonNode content = entry.getValue();
            if (!interactions.containsKey(InteractionSyncRows.text(content, "interactionId"))) {
                continue;
            }
            mapper.upsertRelation(InteractionSyncRows.relation(entry.getKey(), content, index++ * 10));
            retainedRelations.add(entry.getKey());
        }
        mapper.deleteRelations(componentId, retainedRelations);
        mapper.deleteInteractions(componentId, Lists.newArrayList(interactions.keySet()));
    }

    private JsonNode parse(String value) {
        try {
            JsonNode result = objects.readTree(value);
            return result == null ? objects.createObjectNode() : result;
        } catch (java.io.IOException cause) {
            throw new IllegalStateException("交互回写内容不是合法 JSON", cause);
        }
    }
}
