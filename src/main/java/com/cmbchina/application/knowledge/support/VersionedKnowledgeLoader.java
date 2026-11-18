package com.cmbchina.application.knowledge.support;

import com.cmbchina.domain.knowledge.support.KnowledgeVersionSupport;
import com.cmbchina.domain.knowledge.version.KnowledgeComponentVersion;
import com.cmbchina.domain.knowledge.version.KnowledgeObjectVersion;
import com.cmbchina.domain.knowledge.version.KnowledgeVersionQuery;
import com.cmbchina.infrastructure.common.ResponseEnum;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Maps;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import com.cmbchina.domain.knowledge.model.KnowledgeQuery;
import com.cmbchina.domain.knowledge.model.KnowledgeItem;
import com.cmbchina.domain.knowledge.support.KnowledgeDataSupport;
import com.cmbchina.application.knowledge.api.dto.ComponentKnowledgeResponse;
import com.cmbchina.application.knowledge.api.dto.CodeLocationResponse;
import java.util.List;

/** 按固定清单解析知识，缺失版本不能回退最新正文。 */
@Component
public class VersionedKnowledgeLoader {
    @Autowired
    private KnowledgeDataSupport legacy;
    @Value("${knowledge.maintenance.enabled:false}")
    private boolean enabled;
    @Autowired
    private KnowledgeVersionSupport support;
    @Autowired
    private KnowledgeMaintenanceCodec json;
    @Autowired
    private KnowledgeSharedReferences references;

    /** 返回补齐联动身份的独立副本，预览与发布使用同一份内容。 */
    public ArrayNode prepareRelations(JsonNode objects, KnowledgeVersionQuery scope) {
        ArrayNode result = objects.deepCopy();
        Map<String, JsonNode> interactions = Maps.newHashMap();
        for (JsonNode value : result) {
            if ("INTERACTION".equals(value.path("objectType").asText())) {
                interactions.put(value.path("objectId").asText(), value.path("content"));
            }
        }
        for (JsonNode value : result) {
            if ("COMPONENT_RELATION".equals(value.path("objectType").asText())) {
                ObjectNode relation = (ObjectNode) value.path("content");
                JsonNode interaction = interactions.get(relation.path("interactionId").asText());
                json.require(interaction != null, ResponseEnum.KNOWLEDGE_REFERENCE_INVALID);
                String source = interaction.path("componentId").asText();
                String target = relation.path("targetComponentId").asText();
                json.require(!source.equals(target), ResponseEnum.KNOWLEDGE_REFERENCE_INVALID);
                relation.set("source", relationIdentity(scope, relation.path("source"), source));
                relation.set("target", relationIdentity(scope, relation.path("target"), target));
                relation.remove(java.util.Arrays.asList("action", "triggerCondition", "providedContent", "response"));
            }
        }
        return result;
    }

    private JsonNode relationIdentity(KnowledgeVersionQuery scope, JsonNode saved, String id) {
        KnowledgeVersionQuery query = KnowledgeVersionQuery.builder().systemCode(scope.getSystemCode()).projectCode(scope.getProjectCode())
                .componentId(id).build();
        KnowledgeItem component = support.componentIdentity(query);
        json.require(component != null, ResponseEnum.KNOWLEDGE_SCOPE_MISMATCH);
        if (saved.isObject() && id.equals(saved.path("id").asText())) {
            return saved.deepCopy();
        }
        ObjectNode result = json.object();
        result.put("id", id);
        result.put("componentId", component.getComponentKey());
        result.put("name", component.getName());
        result.put("pagePath", component.getPath());
        return result;
    }

    /** 页面名称批量读取，不逐组件访问数据库。 */
    public Map<String, String> currentNames(KnowledgeQuery input) {
        Map<String, String> result = Maps.newHashMap();
        if (!enabled) {
            return result;
        }
        KnowledgeVersionQuery query = KnowledgeVersionQuery.builder().systemCode(input.getSystemCode()).projectCode(input.getProjectCode())
                .pagePath(input.getPagePath()).build();
        Map<String, String> versionOwners = Maps.newHashMap();
        for (KnowledgeComponentVersion component : support.pageCurrentVersions(query)) {
            for (JsonNode entry : json.parse(component.getManifest())) {
                if ("COMPONENT".equals(entry.path("objectType").asText()) && component.getComponentId().equals(entry.path("objectId").asText())) {
                    versionOwners.put(entry.path("versionId").asText(), component.getComponentId());
                }
            }
        }
        if (!versionOwners.isEmpty()) {
            query.setVersionIds(com.google.common.collect.Lists.newArrayList(versionOwners.keySet()));
            for (KnowledgeObjectVersion object : support.objectVersions(query)) {
                result.put(versionOwners.get(object.getId()), json.parse(object.getContent()).path("name").asText(null));
            }
        }
        return result;
    }

    /** 建表和配置完成前不改变现有只读查询。 */
    public ComponentKnowledgeResponse currentContext(KnowledgeQuery input) {
        ObjectNode snapshot = currentSnapshot(input);
        if (snapshot == null) {
            return null;
        }
        ComponentKnowledgeResponse response = json.convert(snapshot.path("context"), ComponentKnowledgeResponse.class);
        references.fill(input, response);
        for (KnowledgeItem location : legacy.services(input)) {
            response.getCodeLocations()
                    .add(CodeLocationResponse.builder().id(location.getId()).serviceCode(location.getServiceCode())
                            .serviceType(location.getServiceType()).systemCode(input.getSystemCode()).projectCode(input.getProjectCode())
                            .localPath(location.getLocalPath()).build());
        }
        return response;
    }

    /** 模型和展示共享固定版本源，不依赖对方响应。 */
    public ObjectNode currentSnapshot(KnowledgeQuery input) {
        if (!enabled) {
            return null;
        }
        List<KnowledgeItem> components = legacy.components(input);
        if (components.isEmpty()) {
            return null;
        }
        KnowledgeVersionQuery query = KnowledgeVersionQuery.builder().systemCode(input.getSystemCode()).projectCode(input.getProjectCode())
                .componentId(components.get(0).getId()).build();
        KnowledgeComponentVersion current = support.currentVersion(query);
        if (current == null) {
            return null;
        }
        return load(query, current);
    }

    /** 返回完整清单及独立对象版本。 */
    public ObjectNode load(KnowledgeVersionQuery query, KnowledgeComponentVersion version) {
        ObjectNode result = json.object();
        ArrayNode objects = json.array();
        JsonNode manifest = version == null ? json.array() : json.parse(version.getManifest());
        Map<String, KnowledgeObjectVersion> found = Maps.newHashMap();
        java.util.List<String> ids = com.google.common.collect.Lists.newArrayList();
        manifest.forEach(entry -> ids.add(entry.path("versionId").asText()));
        if (!ids.isEmpty()) {
            query.setVersionIds(ids);
            support.objectVersions(query).forEach(value -> found.put(value.getId(), value));
        }
        for (JsonNode entry : manifest) {
            KnowledgeObjectVersion object = found.get(entry.path("versionId").asText());
            json.require(
                    object != null && object.getObjectId().equals(entry.path("objectId").asText())
                            && object.getObjectType().equals(entry.path("objectType").asText()) && object.getSchemaVersion() == 1,
                    ResponseEnum.KNOWLEDGE_REFERENCE_INVALID);
            objects.add(object(object));
        }
        result.put("currentVersionId", version == null ? null : version.getId());
        result.set("manifest", manifest);
        result.set("objects", objects);
        ObjectNode context = assemble(objects);
        lifecycleIssues(query, objects, context);
        result.set("context", context);
        return result;
    }

    private void lifecycleIssues(KnowledgeVersionQuery query, JsonNode objects, ObjectNode context) {
        java.util.List<String> ids = com.google.common.collect.Lists.newArrayList();
        objects.forEach(value -> ids.add(value.path("objectId").asText()));
        if (ids.isEmpty()) {
            return;
        }
        query.setObjectIds(ids);
        for (KnowledgeObjectVersion head : support.latestHeads(query)) {
            if ("EXPIRED".equals(head.getLifecycleState())) {
                ObjectNode issue = json.object();
                issue.put("type", "KNOWLEDGE_OBJECT_EXPIRED");
                issue.put("target", head.getObjectId());
                ((ArrayNode) context.path("issues")).add(issue);
            }
        }
    }

    /** 暴露完整对象正文，数据库text不直接泄漏为字符串。 */
    public ObjectNode object(KnowledgeObjectVersion value) {
        ObjectNode result = (ObjectNode) json.tree(value);
        result.set("content", json.parse(value.getContent()));
        result.set("evidence", json.parse(value.getEvidence()));
        return result;
    }

    /** 将对象版本投影为已有详情的内容分组。 */
    public ObjectNode assemble(JsonNode objects) {
        ObjectNode context = json.object();
        for (String key : new String[]{"businessKnowledge", "technicalKnowledge", "interfaceContexts", "interactions", "relatedComponents",
                "codeLocations", "issues"}) {
            context.set(key, json.array());
        }
        ObjectNode collaboration = json.object();
        collaboration.set("inputs", json.array());
        collaboration.set("outputs", json.array());
        context.set("collaboration", collaboration);
        Map<String, ObjectNode> knowledge = Maps.newLinkedHashMap();
        for (JsonNode object : objects) {
            append(context, knowledge, object);
        }
        for (JsonNode object : objects) {
            associate(context, knowledge, object);
        }
        ArrayNode ownInteractions = json.array();
        for (JsonNode interaction : context.path("interactions")) {
            if (context.path("component").path("id").asText().equals(interaction.path("componentId").asText())) {
                ownInteractions.add(interaction);
            }
        }
        context.set("interactions", ownInteractions);
        return context;
    }

    private void append(ObjectNode context, Map<String, ObjectNode> knowledge, JsonNode object) {
        ObjectNode content = object.path("content").deepCopy();
        content.put("id", object.path("objectId").asText());
        if ("COMPONENT".equals(object.path("objectType").asText())) {
            context.set("componentDetails", content.path("details"));
            content.remove("details");
            context.set("component", content);
            return;
        }
        if ("PAGE".equals(object.path("objectType").asText())) {
            context.set("page", content);
            return;
        }
        if ("KNOWLEDGE".equals(object.path("objectType").asText())) {
            content.put("status", object.path("confirmationStatus").asText("PENDING"));
            content.set("conditions", json.array());
            knowledge.put(object.path("objectId").asText(), content);
            String group = "BUSINESS".equals(content.path("type").asText()) ? "businessKnowledge" : "technicalKnowledge";
            ((ArrayNode) context.path(group)).add(content);
            return;
        }
        if ("INTERFACE".equals(object.path("objectType").asText())) {
            content.put("interfaceId", object.path("objectId").asText());
            content.set("rules", json.array());
            ((ArrayNode) context.path("interfaceContexts")).add(content);
            return;
        }
        if ("INTERACTION".equals(object.path("objectType").asText())) {
            ((ArrayNode) context.path("interactions")).add(content);
            return;
        }
    }

    private void associate(ObjectNode context, Map<String, ObjectNode> knowledge, JsonNode object) {
        JsonNode content = object.path("content");
        if ("APPLICABILITY".equals(object.path("objectType").asText())) {
            ObjectNode rule = knowledge.get(content.path("knowledgeId").asText());
            json.require(rule != null, ResponseEnum.KNOWLEDGE_REFERENCE_INVALID);
            ObjectNode condition = content.deepCopy();
            condition.put("id", object.path("objectId").asText());
            ((ArrayNode) rule.path("conditions")).add(condition);
        }
        if ("COMPONENT_RELATION".equals(object.path("objectType").asText())) {
            relation(context, object);
        }
        for (JsonNode api : context.path("interfaceContexts")) {
            if ("INTERFACE".equals(object.path("objectType").asText()) && object.path("objectId").asText().equals(api.path("interfaceId").asText())) {
                for (JsonNode id : content.path("ruleIds")) {
                    ObjectNode rule = knowledge.get(id.asText());
                    json.require(rule != null, ResponseEnum.KNOWLEDGE_REFERENCE_INVALID);
                    ObjectNode interfaceRule = rule.deepCopy();
                    interfaceRule.put("knowledgeId", rule.path("id").asText());
                    ((ArrayNode) api.path("rules")).add(interfaceRule);
                }
            }
        }
    }

    private void relation(ObjectNode context, JsonNode object) {
        ObjectNode relation = object.path("content").deepCopy();
        relation.put("id", object.path("objectId").asText());
        for (JsonNode interaction : context.path("interactions")) {
            if (interaction.path("id").asText().equals(relation.path("interactionId").asText())) {
                for (String field : new String[]{"action", "triggerCondition", "providedContent", "response"}) {
                    relation.set(field, interaction.path(field));
                }
            }
        }
        String currentId = context.path("component").path("id").asText();
        boolean input = currentId.equals(relation.path("targetComponentId").asText());
        JsonNode other = relation.path(input ? "source" : "target");
        json.require(other.isObject(), ResponseEnum.KNOWLEDGE_REFERENCE_INVALID);
        ArrayNode groups = (ArrayNode) context.path("collaboration").path(input ? "inputs" : "outputs");
        ObjectNode selected = null;
        for (JsonNode group : groups) {
            if (group.path("component").path("id").equals(other.path("id"))) {
                selected = (ObjectNode) group;
            }
        }
        if (selected == null) {
            selected = json.object();
            selected.set("component", other.deepCopy());
            selected.set("items", json.array());
            groups.add(selected);
        }
        ((ArrayNode) selected.path("items")).add(relation);
    }
}
