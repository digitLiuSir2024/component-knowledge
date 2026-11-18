package com.cmbchina.application.knowledge.service;

import com.cmbchina.application.knowledge.api.KnowledgeMaintenanceApplicationService;
import com.cmbchina.application.knowledge.api.dto.maintenance.KnowledgeCandidate;
import com.cmbchina.application.knowledge.api.dto.maintenance.MaintenanceRequest;
import com.cmbchina.application.knowledge.support.KnowledgeDraftCoordinator;
import com.cmbchina.application.knowledge.support.KnowledgeDraftPublisher;
import com.cmbchina.application.knowledge.support.KnowledgeMaintenanceAccess;
import com.cmbchina.application.knowledge.support.KnowledgeMaintenanceCodec;
import com.cmbchina.application.knowledge.support.VersionedKnowledgeLoader;
import com.cmbchina.domain.knowledge.support.KnowledgeVersionSupport;
import com.cmbchina.domain.knowledge.version.KnowledgeComponentVersion;
import com.cmbchina.domain.knowledge.version.KnowledgeVersionQuery;
import com.cmbchina.infrastructure.common.ResponseEnum;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 知识维护用例与事务边界。 */
@Service
public class KnowledgeMaintenanceApplicationServiceImpl implements KnowledgeMaintenanceApplicationService {
    @Autowired
    private KnowledgeMaintenanceAccess access;
    @Autowired
    private KnowledgeMaintenanceCodec json;
    @Autowired
    private KnowledgeVersionSupport support;
    @Autowired
    private VersionedKnowledgeLoader loader;
    @Autowired
    private KnowledgeDraftCoordinator drafts;
    @Autowired
    private KnowledgeDraftPublisher publisher;

    /** 读取组件当前基准。 */
    public JsonNode readContext(MaintenanceRequest request) {
        ObjectNode result = access.context(request);
        KnowledgeVersionQuery query = access.componentIdentity(request);
        query.setLimit(2);
        ArrayNode ids = json.array();
        if (query.getComponentId() == null) {
            result.put("registrationObjectId", "new:component");
        } else {
            support.pendingDrafts(query).forEach(draft -> ids.add(draft.getId()));
        }
        result.set("pendingDraftIds", ids);
        return result;
    }

    /** 在当前系统项目内分页检索对象。 */
    public JsonNode searchObjects(MaintenanceRequest request) {
        KnowledgeVersionQuery query = access.scope(request);
        query.setLimit(query.getLimit() + 1);
        return access.page(support.searchObjects(query), query);
    }

    /** 按明确版本读取完整正文。 */
    public JsonNode readObjectVersion(MaintenanceRequest request) {
        return loader.object(access.object(request));
    }

    /** 读取对象版本历史，不把列表摘要当正文。 */
    public JsonNode readObjectHistory(MaintenanceRequest request) {
        KnowledgeVersionQuery query = access.scope(request);
        json.require(json.text(query.getObjectId()) && json.text(query.getObjectType()), ResponseEnum.VALIDATION_ERROR);
        query.setLimit(query.getLimit() + 1);
        return access.page(support.objectHistory(query), query);
    }

    /** 草稿摘要不返回内部压缩快照。 */
    public JsonNode listDrafts(MaintenanceRequest request) {
        KnowledgeVersionQuery query = access.component(request);
        query.setLimit(query.getLimit() + 1);
        ObjectNode result = access.page(support.pendingDrafts(query), query);
        result.set("items", drafts.summaries(result.path("items")));
        result.put("total", support.pendingDraftCount(query));
        return result;
    }

    /** 创建会话或手工候选，尚不发布。 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public JsonNode createDraft(MaintenanceRequest request) {
        json.require("SESSION".equals(request.getOrigin()) || "MANUAL".equals(request.getOrigin()), ResponseEnum.KNOWLEDGE_OBJECT_INVALID);
        return drafts.create(request, request.getOrigin());
    }

    /** 页内编辑有修订保护。 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public JsonNode updateDraft(MaintenanceRequest request) {
        return drafts.update(request, false);
    }

    /** 读取持久化草稿及完整预览。 */
    public JsonNode readDraft(MaintenanceRequest request) {
        KnowledgeVersionQuery query = access.scope(request);
        return drafts.view(query, drafts.find(query, false), request.getSelectedChangeIds());
    }

    /** 丢弃不影响正式版本。 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public JsonNode discardDraft(MaintenanceRequest request) {
        return drafts.update(request, true);
    }

    /** 确认发布与引用投影同一事务完成。 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public JsonNode publishDraft(MaintenanceRequest request) {
        return publisher.publish(request);
    }

    /** 读取组件可恢复版本。 */
    public JsonNode listComponentVersions(MaintenanceRequest request) {
        KnowledgeVersionQuery query = access.component(request);
        query.setLimit(query.getLimit() + 1);
        return access.page(support.componentHistory(query), query);
    }

    /** 恢复也先生成候选，不能绕过页面确认。 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public JsonNode createRestoreDraft(MaintenanceRequest request) {
        KnowledgeVersionQuery query = access.component(request);
        if (Boolean.TRUE.equals(request.getConfirmRestore())) {
            json.require(support.lockComponent(query) != null, ResponseEnum.KNOWLEDGE_NOT_FOUND);
        }
        query.setVersionId(request.getExpectedCurrentVersionId());
        KnowledgeComponentVersion current = support.componentVersion(query);
        json.require(current != null, ResponseEnum.KNOWLEDGE_BASE_CONFLICT);
        query.setVersionId(request.getTargetVersionId());
        KnowledgeComponentVersion target = support.componentVersion(query);
        json.require(target != null, ResponseEnum.KNOWLEDGE_NOT_FOUND);
        Map<String, JsonNode> original = index(loader.load(query, current).path("objects"));
        Map<String, JsonNode> restored = index(loader.load(query, target).path("objects"));
        List<KnowledgeCandidate> candidates = restoreCandidates(original, restored);
        MaintenanceRequest copy = json.convert(json.tree(request), MaintenanceRequest.class);
        copy.setChanges(candidates);
        copy.setBaseVersionId(current.getId());
        ObjectNode draft = drafts.create(copy, "RESTORE");
        if (!Boolean.TRUE.equals(request.getConfirmRestore())) {
            return draft;
        }
        copy.setDraftId(draft.path("id").asText());
        copy.setRevision(draft.path("revision").asInt());
        copy.setSelectedChangeIds(candidates.stream().map(KnowledgeCandidate::getChangeId).collect(java.util.stream.Collectors.toList()));
        return publisher.publish(copy);
    }

    /** 原子清理组件登记、知识、版本和草稿。 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void purgeComponent(MaintenanceRequest request) {
        access.purgeComponent(request);
    }

    /** 首次纳管保留原始快照，确认时再创建基线。 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public JsonNode createBaselineDraft(MaintenanceRequest request) {
        return drafts.create(request, "MIGRATION");
    }

    private Map<String, JsonNode> index(JsonNode values) {
        Map<String, JsonNode> result = Maps.newLinkedHashMap();
        for (JsonNode value : values) {
            result.put(value.path("objectType").asText() + ":" + value.path("objectId").asText(), value);
        }
        return result;
    }

    private List<KnowledgeCandidate> restoreCandidates(Map<String, JsonNode> original, Map<String, JsonNode> restored) {
        List<KnowledgeCandidate> result = Lists.newArrayList();
        for (Map.Entry<String, JsonNode> entry : restored.entrySet()) {
            JsonNode target = entry.getValue();
            JsonNode old = original.get(entry.getKey());
            if (old == null || !old.path("id").equals(target.path("id"))) {
                result.add(restoreCandidate(target, "LINK", target.path("id").asText()));
            }
        }
        for (Map.Entry<String, JsonNode> entry : original.entrySet()) {
            if (!restored.containsKey(entry.getKey())) {
                result.add(restoreCandidate(entry.getValue(), "UNLINK", null));
            }
        }
        return result;
    }

    private KnowledgeCandidate restoreCandidate(JsonNode target, String operation, String versionId) {
        ObjectNode evidence = json.object();
        evidence.put("kind", "FACT");
        evidence.put("source", "已保存的组件历史版本");
        evidence.put("excerpt", "恢复历史对象引用，保留其他组件当前引用");
        return KnowledgeCandidate.builder().changeId(operation + ":" + target.path("objectType").asText() + ":" + target.path("objectId").asText())
                .operation(operation).objectType(target.path("objectType").asText()).objectId(target.path("objectId").asText())
                .targetVersionId(versionId).evidence(json.array().add(evidence)).build();
    }
}
