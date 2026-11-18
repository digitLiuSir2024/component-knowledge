package com.cmbchina.application.knowledge.support;

import com.cmbchina.application.knowledge.api.ComponentKnowledgeApplicationService;
import com.cmbchina.application.knowledge.api.dto.ComponentKnowledgeRequest;
import com.cmbchina.application.knowledge.api.dto.maintenance.MaintenanceRequest;
import com.cmbchina.domain.knowledge.model.KnowledgeItem;
import com.cmbchina.domain.knowledge.model.KnowledgeQuery;
import com.cmbchina.domain.knowledge.support.KnowledgeDataSupport;
import com.cmbchina.domain.knowledge.support.KnowledgeVersionSupport;
import com.cmbchina.domain.knowledge.version.KnowledgeComponentVersion;
import com.cmbchina.domain.knowledge.version.KnowledgeObjectVersion;
import com.cmbchina.domain.knowledge.version.KnowledgeVersionQuery;
import com.cmbchina.infrastructure.common.ResponseEnum;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

/** 维护查询的身份范围与旧数据读取。 */
@Component
public class KnowledgeMaintenanceAccess {
    @Value("${knowledge.maintenance.enabled:false}")
    private boolean enabled;
    @Autowired
    private KnowledgeDataSupport data;
    @Autowired
    private KnowledgeVersionSupport versions;
    @Autowired
    private ComponentKnowledgeApplicationService legacy;
    @Autowired
    private VersionedKnowledgeLoader loader;
    @Autowired
    private KnowledgeMaintenanceCodec json;

    /** 构造有限分页与系统项目条件。 */
    public KnowledgeVersionQuery scope(MaintenanceRequest request) {
        json.require(enabled, ResponseEnum.KNOWLEDGE_DISABLED);
        json.require(json.text(request.getSystemCode()) && json.text(request.getProjectCode()), ResponseEnum.VALIDATION_ERROR);
        int offset = request.getOffset() == null ? 0 : request.getOffset();
        int limit = request.getLimit() == null ? 20 : request.getLimit();
        json.require(offset >= 0 && limit > 0 && limit <= 100, ResponseEnum.VALIDATION_ERROR);
        return KnowledgeVersionQuery.builder().systemCode(request.getSystemCode()).projectCode(request.getProjectCode())
                .objectType(request.getObjectType()).objectId(request.getObjectId()).versionId(request.getVersionId()).draftId(request.getDraftId())
                .requestId(request.getRequestId()).keyword(request.getKeyword()).offset(offset).limit(limit).build();
    }

    /** 页面内身份必须能解析为当前项目组件数据库主键。 */
    public KnowledgeVersionQuery component(MaintenanceRequest request) {
        KnowledgeVersionQuery query = componentIdentity(request);
        json.require(query.getComponentId() != null, ResponseEnum.KNOWLEDGE_NOT_FOUND);
        return query;
    }

    private KnowledgeQuery identity(MaintenanceRequest request) {
        return KnowledgeQuery.builder().systemCode(request.getSystemCode()).projectCode(request.getProjectCode()).pagePath(request.getPagePath())
                .componentId(request.getComponentId()).build();
    }

    /** 解析组件登记，首次整理允许页面内尚无组件记录。 */
    public KnowledgeVersionQuery componentIdentity(MaintenanceRequest request) {
        KnowledgeVersionQuery query = scope(request);
        json.require(json.text(request.getPagePath()) && json.text(request.getComponentId()), ResponseEnum.VALIDATION_ERROR);
        KnowledgeQuery identity = identity(request);
        List<KnowledgeItem> found = data.components(identity);
        if (found.isEmpty()) {
            json.require(data.pages(identity).size() == 1, ResponseEnum.KNOWLEDGE_NOT_FOUND);
            query.setComponentId(null);
        } else {
            json.require(found.size() == 1, ResponseEnum.KNOWLEDGE_NOT_FOUND);
            query.setComponentId(found.get(0).getId());
        }
        return query;
    }

    /** 清理组件及其专属记录。 */
    public void purgeComponent(MaintenanceRequest request) {
        KnowledgeVersionQuery query = componentIdentity(request);
        if (query.getComponentId() != null && versions.lockComponent(query) != null) {
            data.purgeComponent(query.getComponentId());
        }
    }

    /** 写入首次草稿的组件身份，失败随草稿事务回滚。 */
    public KnowledgeVersionQuery registerComponent(MaintenanceRequest request) {
        data.registerComponent(identity(request), com.cmbchina.infrastructure.common.util.CUuid.nextId());
        return component(request);
    }

    /** 当前清单或明确标记的未纳管旧资料。 */
    public ObjectNode context(MaintenanceRequest request) {
        KnowledgeVersionQuery query = componentIdentity(request);
        KnowledgeComponentVersion current = query.getComponentId() == null ? null : versions.currentVersion(query);
        KnowledgeComponentVersion viewed = current;
        if (json.text(request.getVersionId())) {
            viewed = versions.componentVersion(query);
            json.require(viewed != null, ResponseEnum.KNOWLEDGE_NOT_FOUND);
        }
        ObjectNode result = loader.load(query, viewed);
        result.put("currentVersionId", current == null ? null : current.getId());
        result.set("currentVersion", versionInfo(query, current));
        result.set("viewedVersion", versionInfo(query, viewed));
        if (current == null) {
            JsonNode original = original(request);
            result.set("legacyContext", original);
            result.put("sourceRevision", json.digest(original));
        } else {
            result.putNull("legacyContext");
        }
        return result;
    }

    /** 版本标识不包含内部清单，恢复来源解析为可读版本号。 */
    private JsonNode versionInfo(KnowledgeVersionQuery query, KnowledgeComponentVersion version) {
        if (version == null) {
            return json.tree(null);
        }
        ObjectNode info = (ObjectNode) json.tree(version);
        info.remove("manifest");
        if (version.getRestoredFromVersionId() != null) {
            query.setVersionId(version.getRestoredFromVersionId());
            KnowledgeComponentVersion source = versions.componentVersion(query);
            info.put("restoredFromVersionNo", source == null ? null : source.getVersionNo());
        }
        return info;
    }

    /** 获取当前真实使用的旧知识及前端说明，不使用候选覆盖。 */
    public ObjectNode original(MaintenanceRequest request) {
        ObjectNode original = (ObjectNode) json
                .tree(legacy.query(ComponentKnowledgeRequest.builder().systemCode(request.getSystemCode()).projectCode(request.getProjectCode())
                        .pagePath(request.getPagePath()).componentId(request.getComponentId()).interfaces(request.getInterfaces()).build()));
        java.util.List<String> knowledgeIds = com.google.common.collect.Lists.newArrayList();
        for (String group : new String[]{"businessKnowledge", "technicalKnowledge"}) {
            original.path(group).forEach(item -> knowledgeIds.add(item.path("id").asText()));
        }
        ObjectNode sources = json.object();
        if (!knowledgeIds.isEmpty()) {
            for (KnowledgeItem row : data.knowledgeByIds(KnowledgeQuery.builder().systemCode(request.getSystemCode())
                    .projectCode(request.getProjectCode()).knowledgeIds(knowledgeIds).build())) {
                sources.put(row.getKnowledgeId(), row.getSource());
            }
        }
        original.set("knowledgeSources", sources);
        original.set("flowContexts", json.tree(data.pageFlows(KnowledgeQuery.builder().systemCode(request.getSystemCode())
                .projectCode(request.getProjectCode()).pagePath(request.getPagePath()).build())));
        if (request.getMeta() != null) {
            original.set("componentDetails", request.getMeta().deepCopy());
        }
        return original;
    }

    /** 对象版本必须与请求身份完全相符。 */
    public KnowledgeObjectVersion object(MaintenanceRequest request) {
        KnowledgeVersionQuery query = scope(request);
        json.require(json.text(query.getVersionId()) && json.text(query.getObjectId()) && json.text(query.getObjectType()),
                ResponseEnum.VALIDATION_ERROR);
        KnowledgeObjectVersion value = versions.objectVersion(query);
        json.require(value != null, ResponseEnum.KNOWLEDGE_NOT_FOUND);
        json.require(query.getObjectType().equals(value.getObjectType()) && query.getObjectId().equals(value.getObjectId()),
                ResponseEnum.KNOWLEDGE_SCOPE_MISMATCH);
        return value;
    }

    /** 封装有限列表，额外读取一条判断是否有下一页。 */
    public ObjectNode page(List<?> rows, KnowledgeVersionQuery query) {
        ObjectNode result = json.object();
        ArrayNode items = json.array();
        for (int index = 0; index < Math.min(rows.size(), query.getLimit() - 1); index++) {
            Object value = rows.get(index);
            items.add(value instanceof KnowledgeObjectVersion ? loader.object((KnowledgeObjectVersion) value) : json.tree(value));
        }
        result.set("items", items);
        result.put("offset", query.getOffset());
        result.put("limit", query.getLimit() - 1);
        result.put("hasMore", rows.size() == query.getLimit());
        return result;
    }
}
