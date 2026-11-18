package com.cmbchina.application.modelcontext.support;

import com.cmbchina.application.knowledge.support.KnowledgeMaintenanceCodec;
import com.cmbchina.application.knowledge.support.VersionedKnowledgeLoader;
import com.cmbchina.application.knowledge.support.KnowledgeSharedReferences;
import com.cmbchina.application.modelcontext.api.dto.ModelComponent;
import com.cmbchina.application.modelcontext.api.dto.ModelComponentRequest;
import com.cmbchina.application.modelcontext.api.dto.ModelComponentResponse;
import com.cmbchina.application.modelcontext.api.dto.ModelDetailsRequest;
import com.cmbchina.application.modelcontext.api.dto.ModelDetailsResponse;
import com.cmbchina.application.modelcontext.api.dto.ModelField;
import com.cmbchina.application.modelcontext.api.dto.ModelInputGroup;
import com.cmbchina.application.modelcontext.api.dto.ModelInterface;
import com.cmbchina.application.modelcontext.api.dto.ModelInterfaceParts;
import com.cmbchina.application.modelcontext.api.dto.ModelKnowledge;
import com.cmbchina.application.modelcontext.api.dto.ModelPage;
import com.cmbchina.domain.knowledge.model.KnowledgeQuery;
import com.cmbchina.domain.knowledge.model.KnowledgeItem;
import com.cmbchina.domain.knowledge.support.KnowledgeDataSupport;
import com.cmbchina.application.modelcontext.api.dto.ModelIssue;
import com.cmbchina.application.modelcontext.api.dto.ModelCodeLocation;
import com.cmbchina.application.modelcontext.api.dto.ModelRelatedGroup;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** 独立模型契约从版本源投影，不从旧正文或展示HTTP接口回读。 */
@Component
public class VersionedModelContext {
    @Autowired
    private VersionedKnowledgeLoader loader;
    @Autowired
    private KnowledgeMaintenanceCodec json;
    @Autowired
    private KnowledgeSharedReferences shared;
    @Autowired
    private KnowledgeDataSupport legacy;

    /** 纳管后首包保持自身契约，技术知识仍为目录。 */
    public ModelComponentResponse component(KnowledgeQuery query, ModelComponentRequest request) {
        JsonNode snapshot = loader.currentSnapshot(query);
        if (snapshot == null) {
            return null;
        }
        JsonNode context = snapshot.path("context");
        ModelComponentResponse result = ModelComponentResponse.builder().systemCode(request.getSystemCode()).projectCode(request.getProjectCode())
                .component(json.convert(context.path("component"), ModelComponent.class)).page(json.convert(context.path("page"), ModelPage.class))
                .componentDetails(context.path("componentDetails")).build();
        for (JsonNode item : context.path("businessKnowledge")) {
            result.getBusinessKnowledge().add(json.convert(item, ModelKnowledge.class));
        }
        for (JsonNode item : context.path("technicalKnowledge")) {
            ModelKnowledge knowledge = json.convert(item, ModelKnowledge.class);
            knowledge.setContent(null);
            result.getTechnicalKnowledge().add(knowledge);
        }
        result.setInterfaces(interfaces(context, null));
        for (JsonNode issue : context.path("issues")) {
            result.getIssues().add(json.convert(issue, ModelIssue.class));
        }
        return result;
    }

    /** 详情按请求选择固定清单中的明确对象，未命中不能读取旧表替代。 */
    public ModelDetailsResponse details(KnowledgeQuery query, ModelDetailsRequest request) {
        JsonNode snapshot = loader.currentSnapshot(query);
        if (snapshot == null) {
            return null;
        }
        JsonNode context = snapshot.path("context");
        ModelDetailsResponse result = ModelDetailsResponse.builder().build();
        if (request.getKnowledgeIds() != null) {
            List<ModelKnowledge> knowledge = Lists.newArrayList();
            for (String group : new String[]{"businessKnowledge", "technicalKnowledge"}) {
                for (JsonNode item : context.path(group)) {
                    if (request.getKnowledgeIds().contains(item.path("id").asText())) {
                        knowledge.add(json.convert(item, ModelKnowledge.class));
                    }
                }
            }
            result.setKnowledge(knowledge);
        }
        if (request.getInterfaces() != null) {
            result.setInterfaces(interfaces(context, request.getInterfaces()));
        }
        included(query, request, context, result);
        return result;
    }

    private void included(KnowledgeQuery query, ModelDetailsRequest request, JsonNode context, ModelDetailsResponse result) {
        for (JsonNode issue : context.path("issues")) {
            result.getIssues().add(json.convert(issue, ModelIssue.class));
        }
        if (request.getKnowledgeIds() != null) {
            Set<String> found = Sets.newHashSet();
            result.getKnowledge().forEach(value -> found.add(value.getId()));
            for (String id : request.getKnowledgeIds()) {
                if (!found.contains(id)) {
                    result.getIssues().add(ModelIssue.builder().type("KNOWLEDGE_NOT_FOUND").target(id).build());
                }
            }
        }
        if (request.getInclude() == null) {
            return;
        }
        if (request.getInclude().contains("relatedComponents")) {
            List<String> ids = request.getKnowledgeIds() == null ? knowledgeIds(context) : request.getKnowledgeIds();
            List<ModelRelatedGroup> related = Lists.newArrayList();
            shared.read(query, context.path("component").path("id").asText(), ids)
                    .forEach(group -> related.add(json.convert(json.tree(group), ModelRelatedGroup.class)));
            result.setRelatedComponents(related);
        }
        if (request.getInclude().contains("codeLocations")) {
            result.setCodeLocations(locations(query, context));
        }
    }

    private List<String> knowledgeIds(JsonNode context) {
        List<String> ids = Lists.newArrayList();
        for (String group : new String[]{"businessKnowledge", "technicalKnowledge"}) {
            context.path(group).forEach(item -> ids.add(item.path("id").asText()));
        }
        return ids;
    }

    private List<ModelCodeLocation> locations(KnowledgeQuery query, JsonNode context) {
        Set<String> services = Sets.newHashSet();
        context.path("interfaceContexts").forEach(item -> services.add(item.path("serviceCode").asText()));
        List<ModelCodeLocation> result = Lists.newArrayList();
        for (KnowledgeItem item : legacy.services(query)) {
            if ("FRONTEND".equals(item.getServiceType()) || services.contains(item.getServiceCode())) {
                result.add(ModelCodeLocation.builder().id(item.getId()).serviceCode(item.getServiceCode()).serviceType(item.getServiceType())
                        .localPath(item.getLocalPath()).build());
            }
        }
        return result;
    }

    private List<ModelInterface> interfaces(JsonNode context, List<ModelInterfaceParts> requested) {
        Map<String, Set<String>> parts = Maps.newHashMap();
        if (requested != null) {
            for (ModelInterfaceParts item : requested) {
                parts.put(item.getId(), Sets.newHashSet(item.getParts()));
            }
        }
        List<ModelInterface> result = Lists.newArrayList();
        for (JsonNode api : context.path("interfaceContexts")) {
            String id = api.path("interfaceId").asText();
            Set<String> selected = requested == null ? Sets.newHashSet("inputs", "outputs", "rules") : parts.get(id);
            if (selected != null) {
                result.add(api(api, selected));
            }
        }
        return result;
    }

    private ModelInterface api(JsonNode source, Set<String> parts) {
        com.fasterxml.jackson.databind.node.ObjectNode value = source.deepCopy();
        value.put("id", source.path("interfaceId").asText());
        for (JsonNode rule : value.path("rules")) {
            ((com.fasterxml.jackson.databind.node.ObjectNode) rule).put("knowledgeId", rule.path("id").asText());
        }
        ModelInterface result = json.convert(value, ModelInterface.class);
        result.setInputs(null);
        result.setOutputs(null);
        result.setRules(null);
        result.setErrors(null);
        result.setExamples(null);
        if (parts.contains("inputs")) {
            List<ModelInputGroup> inputs = Lists.newArrayList();
            for (JsonNode group : source.path("inputs")) {
                inputs.add(ModelInputGroup.builder().location(group.path("location").asText()).fields(fields(group.path("fields"))).build());
            }
            result.setInputs(inputs);
        }
        if (parts.contains("outputs")) {
            result.setOutputs(fields(source.path("outputs")));
        }
        if (parts.contains("rules")) {
            result.setRules(json.convert(value, ModelInterface.class).getRules());
        }
        if (parts.contains("errors")) {
            result.setErrors(json.convert(value, ModelInterface.class).getErrors());
        }
        if (parts.contains("examples")) {
            result.setExamples(json.convert(value, ModelInterface.class).getExamples());
        }
        return result;
    }

    private List<ModelField> fields(JsonNode rows) {
        List<ModelField> result = Lists.newArrayList();
        for (JsonNode field : rows) {
            result.add(
                    ModelField.builder().name(field.path("name").asText()).path(field.path("path").asText()).location(field.path("location").asText())
                            .required(field.path("required").asBoolean()).nullable(field.path("nullable").asBoolean())
                            .validation(field.path("validation").asText(null)).businessRule(field.path("meaning").asText(null))
                            .technicalRule(field.path("detail").asText(null)).children(fields(field.path("children"))).build());
        }
        return result;
    }
}
