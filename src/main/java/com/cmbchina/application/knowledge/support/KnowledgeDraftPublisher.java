package com.cmbchina.application.knowledge.support;

import com.cmbchina.application.knowledge.api.dto.maintenance.MaintenanceRequest;
import com.cmbchina.domain.knowledge.support.InteractionSyncSupport;
import com.cmbchina.domain.knowledge.support.KnowledgeVersionSupport;
import com.cmbchina.domain.knowledge.version.KnowledgeChangeDraft;
import com.cmbchina.domain.knowledge.version.KnowledgeComponentVersion;
import com.cmbchina.domain.knowledge.version.KnowledgeObjectVersion;
import com.cmbchina.domain.knowledge.version.KnowledgeVersionQuery;
import com.cmbchina.infrastructure.common.ResponseEnum;
import com.cmbchina.infrastructure.common.util.CUuid;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** 发布单一事务的执行器，调用方应用服务负责事务边界。 */
@Component
public class KnowledgeDraftPublisher {
    private static final Logger LOGGER = LoggerFactory.getLogger(KnowledgeDraftPublisher.class);
    @Autowired
    private KnowledgeVersionSupport support;
    @Autowired
    private KnowledgeMaintenanceCodec json;
    @Autowired
    private KnowledgeMaintenanceAccess access;
    @Autowired
    private KnowledgeDraftCoordinator drafts;
    @Autowired
    private KnowledgeDraftChanges changes;
    @Autowired
    private KnowledgeVersionWriter writer;
    @Autowired
    private InteractionSyncSupport interactionSync;

    /** 幂等确认，失败全部回滚。 */
    public JsonNode publish(MaintenanceRequest request) {
        KnowledgeVersionQuery query = access.scope(request);
        KnowledgeChangeDraft original = drafts.find(query, false);
        if (original.getComponentId() != null) {
            json.require(support.lockComponent(query) != null, ResponseEnum.KNOWLEDGE_NOT_FOUND);
        }
        KnowledgeChangeDraft draft = drafts.find(query, true);
        if ("PUBLISHED".equals(draft.getStatus())) {
            return json.parse(draft.getPublishedResult());
        }
        drafts.editable(draft, request);
        KnowledgeComponentVersion current = checkComponent(query, draft);
        ObjectNode payload = (ObjectNode) json.unpack(draft.getPayload());
        Set<String> selected = request.getSelectedChangeIds() == null ? Sets.newHashSet() : Sets.newHashSet(request.getSelectedChangeIds());
        JsonNode candidates = payload.path("changes");
        changes.selection(candidates, selected);
        ArrayNode after = changes.preview(query, payload.path("beforeObjects"), candidates, selected);
        ArrayNode lockTargets = json.array();
        payload.path("beforeObjects").forEach(lockTargets::add);
        candidates.forEach(lockTargets::add);
        writer.lockAll(query, lockTargets);
        ObjectNode result = json.object();
        result.put("draftId", draft.getId());
        result.putNull("baselineVersionId");
        result.set("objectVersionIds", json.object());
        ArrayNode originalManifest = baseline(query, draft, payload, result);
        assignBaselineIds(after, originalManifest);
        ArrayNode manifest = "MIGRATION".equals(draft.getOrigin()) && selected.isEmpty()
                ? originalManifest
                : saveSelected(query, draft, payload, after, selected);
        requireChanged(manifest, current, originalManifest, draft);
        KnowledgeComponentVersion next = publishComponent(query, draft, payload, manifest, originalManifest, selected);
        result.put("componentVersionId", next == null ? null : next.getId());
        if (next != null) {
            syncBack(query, manifest);
        }
        for (JsonNode entry : manifest) {
            ((ObjectNode) result.path("objectVersionIds")).put(entry.path("objectType").asText() + ":" + entry.path("objectId").asText(),
                    entry.path("versionId").asText());
        }
        close(draft, selected, result);
        audit(query, draft, request, next == null ? null : next.getId(), manifest);
        return result;
    }

    private void syncBack(KnowledgeVersionQuery query, JsonNode manifest) {
        java.util.List<String> ids = com.google.common.collect.Lists.newArrayList();
        manifest.forEach(entry -> ids.add(entry.path("versionId").asText()));
        java.util.List<KnowledgeObjectVersion> objects = java.util.Collections.emptyList();
        if (!ids.isEmpty()) {
            query.setVersionIds(ids);
            objects = support.objectVersions(query);
        }
        interactionSync.sync(query.getComponentId(), objects);
    }

    private void audit(KnowledgeVersionQuery query, KnowledgeChangeDraft draft, MaintenanceRequest request, String versionId, JsonNode manifest) {
        int interactions = 0;
        int relations = 0;
        for (JsonNode entry : manifest) {
            String type = entry.path("objectType").asText();
            if ("INTERACTION".equals(type)) {
                interactions++;
            } else if ("COMPONENT_RELATION".equals(type)) {
                relations++;
            }
        }
        LOGGER.info("knowledge publish component={} draft={} request={} version={} interactions={} relations={}", query.getComponentId(),
                draft.getId(), request.getRequestId(), versionId, interactions, relations);
    }

    private void requireChanged(JsonNode manifest, KnowledgeComponentVersion current, JsonNode original, KnowledgeChangeDraft draft) {
        JsonNode before = current == null ? original : json.parse(current.getManifest());
        json.require(!manifest.equals(before) || "MIGRATION".equals(draft.getOrigin()), ResponseEnum.KNOWLEDGE_NO_CHANGE);
    }

    /** 为事务内部预览副本补齐已有基线版本，未选择对象不重复写入。 */
    private void assignBaselineIds(ArrayNode objects, JsonNode manifest) {
        Map<String, String> ids = Maps.newHashMap();
        manifest.forEach(entry -> ids.put(key(entry), entry.path("versionId").asText()));
        for (JsonNode object : objects) {
            String id = ids.get(key(object));
            if (id != null) {
                ((ObjectNode) object).put("id", id);
            }
        }
    }

    private KnowledgeComponentVersion checkComponent(KnowledgeVersionQuery query, KnowledgeChangeDraft draft) {
        if (draft.getComponentId() == null) {
            return null;
        }
        if ("RESTORE".equals(draft.getOrigin())) {
            json.require(support.pendingDraftCount(query) == 1, ResponseEnum.KNOWLEDGE_PENDING_DRAFTS);
        }
        KnowledgeComponentVersion current = support.currentVersion(query);
        json.require(Objects.equals(current == null ? null : current.getId(), draft.getBaseVersionId()), ResponseEnum.KNOWLEDGE_BASE_CONFLICT);
        return current;
    }

    private KnowledgeComponentVersion publishComponent(KnowledgeVersionQuery query, KnowledgeChangeDraft draft, JsonNode payload, JsonNode manifest,
            JsonNode originalManifest, Set<String> selected) {
        if (draft.getComponentId() == null) {
            return null;
        }
        if (!"MIGRATION".equals(draft.getOrigin()) || !manifest.equals(originalManifest)) {
            return writer.publish(query, manifest, draft.getId(), payload.path("restoredFromVersionId").asText(null),
                    KnowledgeDraftCoordinator.describe(payload.path("changes"), selected));
        }
        return support.currentVersion(query);
    }

    private ArrayNode baseline(KnowledgeVersionQuery query, KnowledgeChangeDraft draft, ObjectNode payload, ObjectNode result) {
        if (!"MIGRATION".equals(draft.getOrigin())) {
            ArrayNode original = json.array();
            if (draft.getComponentId() == null) {
                payload.path("beforeObjects").forEach(object -> original.add(entryNode(object)));
            }
            return original;
        }
        MaintenanceRequest identity = json.convert(payload.path("identity"), MaintenanceRequest.class);
        json.require(json.digest(access.original(identity)).equals(json.digest(payload.path("originalSnapshot"))),
                ResponseEnum.KNOWLEDGE_BASE_CONFLICT);
        ArrayNode manifest = json.array();
        for (JsonNode object : payload.path("beforeObjects")) {
            KnowledgeObjectVersion saved = save(query, object, draft.getId(), "BASELINE", null);
            manifest.add(entry(saved));
        }
        KnowledgeComponentVersion baseline = writer.publish(query, manifest, draft.getId(), null, "初始化组件知识");
        result.put("baselineVersionId", baseline.getId());
        return manifest;
    }

    private ArrayNode saveSelected(KnowledgeVersionQuery query, KnowledgeChangeDraft draft, JsonNode payload, JsonNode after, Set<String> selected) {
        Map<String, JsonNode> accepted = Maps.newHashMap();
        Map<String, String> ids = Maps.newHashMap();
        collectSelected(payload, selected, accepted, ids);
        ArrayNode manifest = json.array();
        for (JsonNode original : after) {
            JsonNode candidate = accepted.get(key(original));
            manifest.add(selectedVersion(query, draft, original, candidate, ids));
        }
        return manifest;
    }

    private void collectSelected(JsonNode payload, Set<String> selected, Map<String, JsonNode> accepted, Map<String, String> ids) {
        for (JsonNode candidate : payload.path("changes")) {
            if (selected.contains(candidate.path("changeId").asText())) {
                accepted.put(key(candidate), candidate);
                if (candidate.path("objectId").asText().startsWith("new:")) {
                    ids.put(candidate.path("objectId").asText(), CUuid.nextId());
                }
            }
        }
    }

    private ObjectNode selectedVersion(KnowledgeVersionQuery query, KnowledgeChangeDraft draft, JsonNode original, JsonNode candidate,
            Map<String, String> ids) {
        if (candidate == null && original.path("id").isTextual()) {
            return entryNode(original);
        }
        if (candidate != null
                && ("LINK".equals(candidate.path("operation").asText()) || "UPGRADE_REFERENCE".equals(candidate.path("operation").asText()))) {
            query.setObjectType(original.path("objectType").asText());
            query.setObjectId(original.path("objectId").asText());
            KnowledgeObjectVersion head = writer.head(query);
            json.require("RESTORE".equals(draft.getOrigin()) || head != null && "ACTIVE".equals(head.getLifecycleState()),
                    ResponseEnum.KNOWLEDGE_OBJECT_INVALID);
            return entryNode(original);
        }
        String expected = candidate == null ? null : candidate.path("baseObjectVersionId").asText(null);
        return entry(save(query, replace(original, ids), draft.getId(), candidate == null ? "BASELINE" : "NORMAL", expected));
    }

    private KnowledgeObjectVersion save(KnowledgeVersionQuery query, JsonNode object, String draftId, String purpose, String expected) {
        query.setObjectType(object.path("objectType").asText());
        query.setObjectId(object.path("objectId").asText());
        KnowledgeObjectVersion head = writer.head(query);
        String lifecycle = object.path("lifecycleState").asText("ACTIVE");
        if (head != null && "EXPIRED".equals(head.getLifecycleState())) {
            lifecycle = "EXPIRED";
        }
        KnowledgeObjectVersion proposed = KnowledgeObjectVersion.builder().versionPurpose(purpose).lifecycleState(lifecycle)
                .confirmationStatus(object.path("confirmationStatus").asText(null)).content(object.path("content").toString())
                .evidence(object.path("evidence").isArray() ? object.path("evidence").toString() : "[]").draftId(draftId).build();
        return writer.save(query, proposed, expected);
    }

    private ObjectNode entry(KnowledgeObjectVersion object) {
        ObjectNode result = json.object();
        result.put("objectType", object.getObjectType());
        result.put("objectId", object.getObjectId());
        result.put("versionId", object.getId());
        return result;
    }

    private ObjectNode entryNode(JsonNode object) {
        ObjectNode result = json.object();
        result.set("objectType", object.path("objectType"));
        result.set("objectId", object.path("objectId"));
        result.put("versionId", object.path("id").asText());
        return result;
    }

    private ObjectNode replace(JsonNode object, Map<String, String> ids) {
        ObjectNode result = object.deepCopy();
        replaceValues(result, ids);
        return result;
    }

    private void replaceValues(JsonNode value, Map<String, String> ids) {
        if (value.isObject()) {
            ObjectNode object = (ObjectNode) value;
            object.fieldNames().forEachRemaining(field -> {
                JsonNode child = object.path(field);
                if (child.isTextual() && ids.containsKey(child.asText())) {
                    object.put(field, ids.get(child.asText()));
                } else {
                    replaceValues(child, ids);
                }
            });
        } else if (value.isArray()) {
            ArrayNode array = (ArrayNode) value;
            for (int index = 0; index < array.size(); index++) {
                JsonNode child = array.get(index);
                if (child.isTextual() && ids.containsKey(child.asText())) {
                    array.set(index, json.tree(ids.get(child.asText())));
                } else {
                    replaceValues(child, ids);
                }
            }
        }
    }

    private void close(KnowledgeChangeDraft original, Set<String> selected, ObjectNode result) {
        KnowledgeChangeDraft draft = json.convert(json.tree(original), KnowledgeChangeDraft.class);
        draft.setRevision(draft.getRevision() + 1);
        draft.setStatus("PUBLISHED");
        draft.setSelectedChanges(json.tree(selected).toString());
        draft.setPublishedResult(result.toString());
        draft.setUpdatedAt(Instant.now().toString());
        json.require(support.updateDraft(draft) == 1, ResponseEnum.KNOWLEDGE_REVISION_CONFLICT);
    }

    private String key(JsonNode value) {
        return value.path("objectType").asText() + ":" + value.path("objectId").asText();
    }
}
