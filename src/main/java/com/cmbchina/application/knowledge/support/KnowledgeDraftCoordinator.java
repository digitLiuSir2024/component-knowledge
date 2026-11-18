package com.cmbchina.application.knowledge.support;

import com.cmbchina.application.knowledge.api.dto.maintenance.MaintenanceRequest;
import com.cmbchina.domain.knowledge.support.KnowledgeVersionSupport;
import com.cmbchina.domain.knowledge.version.KnowledgeChangeDraft;
import com.cmbchina.domain.knowledge.version.KnowledgeComponentVersion;
import com.cmbchina.domain.knowledge.version.KnowledgeVersionQuery;
import com.cmbchina.infrastructure.common.ResponseEnum;
import com.cmbchina.infrastructure.common.util.CUuid;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Sets;
import java.time.Instant;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 草稿修订与审核预览，不执行发布。 */
@Component
public class KnowledgeDraftCoordinator {
    private static final java.util.Map<String, String> OPERATION_LABELS = com.google.common.collect.ImmutableMap.of("CREATE", "新增", "LINK", "关联",
            "UNLINK", "移除关联", "EXPIRE", "停用", "UPGRADE_REFERENCE", "更新引用");

    @Autowired
    private KnowledgeVersionSupport support;
    @Autowired
    private KnowledgeMaintenanceAccess access;
    @Autowired
    private KnowledgeMaintenanceCodec json;
    @Autowired
    private KnowledgeDraftChanges changes;
    @Autowired
    private VersionedKnowledgeLoader loader;
    @Autowired
    private KnowledgeBaselineAssembler baseline;
    @Value("${knowledge.maintenance.review-base-url:}")
    private String reviewBaseUrl;

    /** 创建幂等候选，未确认不保存正式版本。 */
    public ObjectNode create(MaintenanceRequest request, String origin) {
        json.require(json.text(request.getRequestId()), ResponseEnum.VALIDATION_ERROR);
        boolean migration = "MIGRATION".equals(origin);
        KnowledgeVersionQuery query = migration ? access.componentIdentity(request) : access.component(request);
        boolean registering = query.getComponentId() == null;
        if (registering) {
            json.require(json.digest(access.original(request)).equals(request.getSourceRevision()), ResponseEnum.KNOWLEDGE_BASE_CONFLICT);
            query = access.registerComponent(request);
        }
        String digest = json.digest(json.tree(request));
        KnowledgeChangeDraft existing = support.requestDraft(query);
        if (existing != null) {
            json.require(digest.equals(existing.getRequestDigest()), ResponseEnum.KNOWLEDGE_REQUEST_CONFLICT);
            return view(query, existing);
        }
        json.require(support.lockComponent(query) != null, ResponseEnum.KNOWLEDGE_NOT_FOUND);
        existing = support.requestDraft(query);
        if (existing != null) {
            json.require(digest.equals(existing.getRequestDigest()), ResponseEnum.KNOWLEDGE_REQUEST_CONFLICT);
            return view(query, existing);
        }
        ObjectNode payload = payload(request, query, origin, registering);
        JsonNode candidates = request.getChanges() == null ? json.array() : json.tree(request.getChanges());
        validateSubmission(candidates, query, origin, registering);
        if (Boolean.TRUE.equals(request.getObjectOnly())) {
            json.require("MANUAL".equals(origin), ResponseEnum.KNOWLEDGE_OBJECT_INVALID);
            for (JsonNode candidate : candidates) {
                json.require(!"COMPONENT".equals(candidate.path("objectType").asText()), ResponseEnum.KNOWLEDGE_OBJECT_INVALID);
            }
        }
        payload.set("changes", candidates);
        payload.set("identity", json.tree(request));
        String now = Instant.now().toString();
        KnowledgeChangeDraft draft = KnowledgeChangeDraft.builder().id(CUuid.nextId()).systemCode(query.getSystemCode())
                .projectCode(query.getProjectCode()).componentId(Boolean.TRUE.equals(request.getObjectOnly()) ? null : query.getComponentId())
                .origin(origin).revision(1).status("DRAFT").baseVersionId(payload.path("baseVersionId").asText(null)).objectBases("{}")
                .payload(json.pack(payload)).selectedChanges("[]").publishedResult(null).requestId(request.getRequestId()).requestDigest(digest)
                .createdAt(now).updatedAt(now).build();
        if ("RESTORE".equals(origin)) {
            return restore(query, request, draft);
        }
        replacePending(query, draft, now, request.getExpectedPendingDraft());
        support.insertDraft(draft);
        return view(query, draft);
    }

    private ObjectNode restore(KnowledgeVersionQuery query, MaintenanceRequest request, KnowledgeChangeDraft draft) {
        long pending = support.pendingDraftCount(query);
        if (Boolean.TRUE.equals(request.getConfirmRestore())) {
            json.require(pending == 0, ResponseEnum.KNOWLEDGE_PENDING_DRAFTS);
            support.insertDraft(draft);
        }
        ObjectNode preview = view(query, draft);
        preview.put("pendingCount", pending);
        return preview;
    }

    private void replacePending(KnowledgeVersionQuery query, KnowledgeChangeDraft draft, String now, JsonNode expected) {
        if (draft.getComponentId() != null && !"RESTORE".equals(draft.getOrigin())) {
            query.setLimit(2);
            query.setOffset(0);
            java.util.List<KnowledgeChangeDraft> pending = support.pendingDrafts(query);
            json.require(pending.size() <= 1, ResponseEnum.KNOWLEDGE_PENDING_DRAFTS);
            validatePending(expected, pending);
            if (!pending.isEmpty()) {
                KnowledgeChangeDraft previous = pending.get(0);
                json.require(!"RESTORE".equals(previous.getOrigin()), ResponseEnum.KNOWLEDGE_PENDING_DRAFTS);
                previous.setStatus("DISCARDED");
                previous.setRevision(previous.getRevision() + 1);
                previous.setUpdatedAt(now);
                json.require(support.updateDraft(previous) == 1, ResponseEnum.KNOWLEDGE_REVISION_CONFLICT);
                draft.setRevision(previous.getRevision());
            }
        }
    }

    private void validatePending(JsonNode expected, java.util.List<KnowledgeChangeDraft> pending) {
        if (expected == null) {
            return;
        }
        json.require(expected.isObject(), ResponseEnum.VALIDATION_ERROR);
        if (expected.isEmpty()) {
            json.require(pending.isEmpty(), ResponseEnum.KNOWLEDGE_REVISION_CONFLICT);
            return;
        }
        JsonNode id = expected.path("id");
        JsonNode revision = expected.path("revision");
        boolean validRevision = revision.isIntegralNumber() && revision.canConvertToInt() && revision.asInt() > 0;
        json.require(expected.size() == 2 && id.isTextual() && json.text(id.asText()) && validRevision, ResponseEnum.VALIDATION_ERROR);
        json.require(pending.size() == 1 && id.asText().equals(pending.get(0).getId()) && revision.asInt() == pending.get(0).getRevision(),
                ResponseEnum.KNOWLEDGE_REVISION_CONFLICT);
    }

    private void validateSubmission(JsonNode candidates, KnowledgeVersionQuery query, String origin, boolean registering) {
        if (registering) {
            bindRegisteredComponent(candidates, query.getComponentId());
        }
        changes.validate(candidates);
        requireBases(candidates, origin);
    }

    private void bindRegisteredComponent(JsonNode candidates, String componentId) {
        for (JsonNode candidate : candidates) {
            if ("COMPONENT".equals(candidate.path("objectType").asText()) && "CREATE".equals(candidate.path("operation").asText())
                    && "new:component".equals(candidate.path("objectId").asText())) {
                ((ObjectNode) candidate).put("operation", "UPDATE").put("objectId", componentId);
            }
            JsonNode content = candidate.path("content");
            if ("APPLICABILITY".equals(candidate.path("objectType").asText()) && "COMPONENT".equals(content.path("targetType").asText())
                    && "new:component".equals(content.path("targetId").asText())) {
                ((ObjectNode) content).put("targetId", componentId);
            }
            if ("COMPONENT_RELATION".equals(candidate.path("objectType").asText())
                    && "new:component".equals(content.path("targetComponentId").asText())) {
                ((ObjectNode) content).put("targetComponentId", componentId);
            }
        }
    }

    private ObjectNode payload(MaintenanceRequest request, KnowledgeVersionQuery query, String origin, boolean registering) {
        KnowledgeComponentVersion current = support.currentVersion(query);
        ObjectNode payload = json.object();
        payload.put("baseVersionId", current == null ? null : current.getId());
        if ("MIGRATION".equals(origin)) {
            json.require(current == null, ResponseEnum.KNOWLEDGE_BASE_CONFLICT);
            ObjectNode original = access.original(request);
            json.require(registering || json.digest(original).equals(request.getSourceRevision()), ResponseEnum.KNOWLEDGE_BASE_CONFLICT);
            payload.set("originalSnapshot", original);
            payload.set("beforeObjects", baseline.collect(original));
        } else {
            json.require(current != null && current.getId().equals(request.getBaseVersionId()), ResponseEnum.KNOWLEDGE_BASE_CONFLICT);
            payload.set("beforeObjects", loader.load(query, current).path("objects"));
        }
        if ("RESTORE".equals(origin)) {
            json.require(current != null && current.getId().equals(request.getExpectedCurrentVersionId()), ResponseEnum.KNOWLEDGE_BASE_CONFLICT);
            query.setVersionId(request.getTargetVersionId());
            KnowledgeComponentVersion target = support.componentVersion(query);
            json.require(target != null, ResponseEnum.KNOWLEDGE_NOT_FOUND);
            payload.put("restoredFromVersionId", target.getId());
        }
        return payload;
    }

    /** 按系统项目读取草稿，可选择加行锁。 */
    public KnowledgeChangeDraft find(KnowledgeVersionQuery query, boolean locked) {
        json.require(json.text(query.getDraftId()), ResponseEnum.VALIDATION_ERROR);
        KnowledgeChangeDraft draft = locked ? support.lockDraft(query) : support.draft(query);
        json.require(draft != null, ResponseEnum.KNOWLEDGE_NOT_FOUND);
        query.setComponentId(draft.getComponentId());
        return draft;
    }

    /** 基准与修订必须同时有效。 */
    public void editable(KnowledgeChangeDraft draft, MaintenanceRequest request) {
        json.require("DRAFT".equals(draft.getStatus()), ResponseEnum.KNOWLEDGE_DRAFT_CLOSED);
        json.require(request.getRevision() != null && request.getRevision() == draft.getRevision(), ResponseEnum.KNOWLEDGE_REVISION_CONFLICT);
    }

    /** 页内编辑只改变候选，不改变原始快照。 */
    public ObjectNode update(MaintenanceRequest request, boolean discard) {
        KnowledgeVersionQuery query = access.scope(request);
        KnowledgeChangeDraft original = find(query, true);
        editable(original, request);
        KnowledgeChangeDraft draft = json.convert(json.tree(original), KnowledgeChangeDraft.class);
        if (discard) {
            draft.setStatus("DISCARDED");
        } else {
            JsonNode candidates = json.tree(request.getChanges());
            changes.validate(candidates);
            requireBases(candidates, draft.getOrigin());
            ObjectNode payload = (ObjectNode) json.unpack(draft.getPayload());
            payload.set("changes", candidates);
            draft.setPayload(json.pack(payload));
        }
        draft.setRevision(draft.getRevision() + 1);
        draft.setUpdatedAt(Instant.now().toString());
        json.require(support.updateDraft(draft) == 1, ResponseEnum.KNOWLEDGE_REVISION_CONFLICT);
        return view(query, draft);
    }

    /** 完整原内容和候选内容供原详情组件复用。 */
    public ObjectNode view(KnowledgeVersionQuery query, KnowledgeChangeDraft draft) {
        return view(query, draft, null);
    }

    /** 按页面选择返回确定性的最终发布预览。 */
    public ObjectNode view(KnowledgeVersionQuery query, KnowledgeChangeDraft draft, java.util.List<String> choices) {
        ObjectNode result = (ObjectNode) json.tree(draft);
        result.put("independent", draft.getComponentId() == null);
        ObjectNode payload = (ObjectNode) json.unpack(draft.getPayload());
        JsonNode candidates = payload.path("changes");
        Set<String> selected = Sets.newHashSet();
        for (JsonNode change : candidates) {
            selected.add(change.path("changeId").asText());
        }
        if (choices != null) {
            selected = Sets.newHashSet(choices);
        }
        changes.selection(candidates, selected);
        ArrayNode afterObjects = changes.preview(query, payload.path("beforeObjects"), candidates, selected);
        result.set("changes", candidates);
        result.set("before", loader.assemble(payload.path("beforeObjects")));
        result.set("after", loader.assemble(afterObjects));
        result.set("beforeObjects", payload.path("beforeObjects"));
        result.set("afterObjects", afterObjects);
        result.set("selectedChangeIds", json.tree(selected));
        result.set("issues", json.array());
        result.set("publishResult", draft.getPublishedResult() == null ? null : json.parse(draft.getPublishedResult()));
        result.set("identity", payload.path("identity"));
        result.put("reviewUrl",
                org.springframework.web.util.UriComponentsBuilder.fromUriString(reviewBaseUrl).queryParam("knowledgeDraft", draft.getId())
                        .queryParam("knowledgeComponent", payload.path("identity").path("componentId").asText()).queryParam("knowledgeReview", "1")
                        .build().encode().toUriString());
        result.remove(java.util.Arrays.asList("payload", "requestDigest", "objectBases", "publishedResult", "selectedChanges"));
        return result;
    }

    /** 列表仅返回审核所需摘要，不泄露内部存储字段。 */
    public ArrayNode summaries(JsonNode rows) {
        ArrayNode result = json.array();
        for (JsonNode row : rows) {
            ObjectNode summary = json.object();
            for (String field : new String[]{"id", "origin", "revision", "baseVersionId", "createdAt", "updatedAt"}) {
                summary.set(field, row.path(field));
            }
            JsonNode candidates = json.unpack(row.path("payload").asText()).path("changes");
            summary.put("changeCount", candidates.size());
            summary.put("summary", describe(candidates, null));
            result.add(summary);
        }
        return result;
    }

    static String describe(JsonNode candidates, java.util.Set<String> selected) {
        StringBuilder description = new StringBuilder();
        for (JsonNode candidate : candidates) {
            if (selected != null && !selected.contains(candidate.path("changeId").asText())) {
                continue;
            }
            String operation = OPERATION_LABELS.getOrDefault(candidate.path("operation").asText(), "修改");
            if (description.length() > 0) {
                description.append("；");
            }
            description.append(operation).append(candidate.path("content").path("title").asText(candidate.path("objectId").asText()));
        }
        String value = description.toString();
        return value.codePointCount(0, value.length()) > 100 ? value.substring(0, value.offsetByCodePoints(0, 99)) + "…" : value;
    }

    private void requireBases(JsonNode candidates, String origin) {
        if ("MIGRATION".equals(origin)) {
            return;
        }
        for (JsonNode change : candidates) {
            String operation = change.path("operation").asText();
            if ("UPDATE".equals(operation) || "EXPIRE".equals(operation)) {
                json.require(json.text(change.path("baseObjectVersionId").asText(null)), ResponseEnum.KNOWLEDGE_BASE_CONFLICT);
            }
        }
    }
}
