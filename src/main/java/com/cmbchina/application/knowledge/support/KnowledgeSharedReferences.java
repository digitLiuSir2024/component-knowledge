package com.cmbchina.application.knowledge.support;

import com.cmbchina.application.knowledge.api.dto.ComponentKnowledgeResponse;
import com.cmbchina.application.knowledge.api.dto.KnowledgeItemResponse;
import com.cmbchina.application.knowledge.api.dto.RelatedComponent;
import com.cmbchina.application.knowledge.api.dto.RelatedComponentGroup;
import com.cmbchina.application.knowledge.api.dto.LinkedComponentResponse;
import com.cmbchina.domain.knowledge.model.KnowledgeItem;
import com.cmbchina.domain.knowledge.model.KnowledgeQuery;
import com.cmbchina.domain.knowledge.support.KnowledgeDataSupport;
import com.cmbchina.domain.knowledge.support.KnowledgeVersionSupport;
import com.cmbchina.domain.knowledge.version.KnowledgeComponentVersion;
import com.cmbchina.domain.knowledge.version.KnowledgeObjectVersion;
import com.cmbchina.domain.knowledge.version.KnowledgeVersionQuery;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** 用当前引用索引限定候选组件，再批量读取关系版本。 */
@Component
public class KnowledgeSharedReferences {
    @Autowired
    private KnowledgeVersionSupport versions;
    @Autowired
    private KnowledgeDataSupport legacy;
    @Autowired
    private KnowledgeMaintenanceCodec json;
    @Autowired
    private KnowledgeContextAssembler assembler;

    /** 模型与详情共用引用查询，不通过展示HTTP接口读取。 */
    public List<RelatedComponentGroup> read(KnowledgeQuery input, String componentId, List<String> knowledgeIds) {
        ComponentKnowledgeResponse source = ComponentKnowledgeResponse.builder()
                .component(com.cmbchina.application.knowledge.api.dto.ComponentResponse.builder().id(componentId).build())
                .businessKnowledge(Lists.newArrayList()).technicalKnowledge(Lists.newArrayList()).relatedComponents(Lists.newArrayList()).build();
        for (String id : knowledgeIds) {
            source.getBusinessKnowledge().add(KnowledgeItemResponse.builder().id(id).build());
        }
        fill(input, source);
        return source.getRelatedComponents();
    }

    /** 纳管与未纳管关系合并，旧表不得覆盖新清单。 */
    public void fill(KnowledgeQuery input, ComponentKnowledgeResponse response) {
        Set<String> ids = Sets.newLinkedHashSet();
        for (KnowledgeItemResponse item : response.getBusinessKnowledge()) {
            ids.add(item.getId());
        }
        for (KnowledgeItemResponse item : response.getTechnicalKnowledge()) {
            ids.add(item.getId());
        }
        if (ids.isEmpty()) {
            return;
        }
        KnowledgeQuery oldQuery = KnowledgeQuery.builder().systemCode(input.getSystemCode()).projectCode(input.getProjectCode())
                .knowledgeIds(Lists.newArrayList(ids)).currentComponentId(response.getComponent().getId()).build();
        List<KnowledgeItem> old = legacy.relatedComponents(oldQuery);
        KnowledgeVersionQuery query = KnowledgeVersionQuery.builder().systemCode(input.getSystemCode()).projectCode(input.getProjectCode())
                .objectType("KNOWLEDGE").objectIds(Lists.newArrayList(ids)).build();
        Set<String> components = Sets.newLinkedHashSet(versions.referencingComponents(query));
        old.forEach(item -> components.add(item.getTargetId()));
        components.remove(response.getComponent().getId());
        Map<String, RelatedComponentGroup> groups = Maps.newLinkedHashMap();
        if (!components.isEmpty()) {
            query.setComponentIds(Lists.newArrayList(components));
            List<KnowledgeComponentVersion> current = versions.currentVersions(query);
            Map<String, KnowledgeObjectVersion> objects = objects(query, current);
            Set<String> managed = Sets.newHashSet();
            for (KnowledgeComponentVersion version : current) {
                managed.add(version.getComponentId());
                append(groups, ids, version, objects);
            }
            for (KnowledgeItem item : old) {
                if (!managed.contains(item.getTargetId())) {
                    group(groups, item.getKnowledgeId()).getItems().add(assembler.relation(item));
                }
            }
        }
        response.setRelatedComponents(Lists.newArrayList(groups.values()));
    }

    private Map<String, KnowledgeObjectVersion> objects(KnowledgeVersionQuery query, List<KnowledgeComponentVersion> current) {
        Set<String> ids = Sets.newLinkedHashSet();
        for (KnowledgeComponentVersion version : current) {
            for (JsonNode entry : json.parse(version.getManifest())) {
                ids.add(entry.path("versionId").asText());
            }
        }
        Map<String, KnowledgeObjectVersion> result = Maps.newHashMap();
        if (!ids.isEmpty()) {
            query.setVersionIds(Lists.newArrayList(ids));
            for (KnowledgeObjectVersion value : versions.objectVersions(query)) {
                result.put(value.getId(), value);
            }
        }
        return result;
    }

    private void append(Map<String, RelatedComponentGroup> groups, Set<String> selected, KnowledgeComponentVersion version,
            Map<String, KnowledgeObjectVersion> objects) {
        JsonNode component = json.object();
        String pagePath = "";
        for (JsonNode entry : json.parse(version.getManifest())) {
            KnowledgeObjectVersion value = objects.get(entry.path("versionId").asText());
            if (value != null && "COMPONENT".equals(value.getObjectType()) && version.getComponentId().equals(value.getObjectId())) {
                component = json.parse(value.getContent());
            }
            if (value != null && "PAGE".equals(value.getObjectType())) {
                pagePath = json.parse(value.getContent()).path("path").asText();
            }
        }
        LinkedComponentResponse identity = LinkedComponentResponse.builder().id(version.getComponentId())
                .componentId(component.path("componentId").asText()).name(component.path("name").asText()).pagePath(pagePath).build();
        appendRelations(groups, selected, version, objects, identity);
    }

    private void appendRelations(Map<String, RelatedComponentGroup> groups, Set<String> selected, KnowledgeComponentVersion version,
            Map<String, KnowledgeObjectVersion> objects, LinkedComponentResponse identity) {
        for (JsonNode entry : json.parse(version.getManifest())) {
            KnowledgeObjectVersion value = objects.get(entry.path("versionId").asText());
            if (value != null && "APPLICABILITY".equals(value.getObjectType())) {
                JsonNode relation = json.parse(value.getContent());
                String knowledgeId = relation.path("knowledgeId").asText();
                if (selected.contains(knowledgeId) && "COMPONENT".equals(relation.path("targetType").asText())
                        && version.getComponentId().equals(relation.path("targetId").asText())) {
                    group(groups, knowledgeId).getItems()
                            .add(RelatedComponent.builder().id(value.getObjectId()).targetType("COMPONENT").componentId(version.getComponentId())
                                    .component(identity).unconditional(relation.path("unconditional").asBoolean())
                                    .condition(relation.path("condition").asText(null)).exception(relation.path("exception").asText(null))
                                    .replacesKnowledgeId(relation.path("replacesKnowledgeId").asText(null)).build());
                }
            }
        }
    }

    private RelatedComponentGroup group(Map<String, RelatedComponentGroup> groups, String knowledgeId) {
        return groups.computeIfAbsent(knowledgeId, id -> RelatedComponentGroup.builder().knowledgeId(id).build());
    }
}
