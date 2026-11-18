package com.cmbchina.infrastructure.knowledge.supportimpl;
import com.cmbchina.domain.knowledge.version.KnowledgeChangeDraft;
import com.cmbchina.domain.knowledge.version.KnowledgeComponentVersion;
import com.cmbchina.domain.knowledge.version.KnowledgeCurrentReference;
import com.cmbchina.domain.knowledge.version.KnowledgeObjectVersion;
import com.cmbchina.domain.knowledge.version.KnowledgeVersionQuery;
import com.cmbchina.domain.knowledge.support.KnowledgeVersionSupport;
import com.cmbchina.infrastructure.knowledge.dao.po.KnowledgeChangeDraftPo;
import com.cmbchina.infrastructure.knowledge.dao.po.KnowledgeComponentVersionPo;
import com.cmbchina.infrastructure.knowledge.dao.po.KnowledgeCurrentReferencePo;
import com.cmbchina.infrastructure.knowledge.dao.po.KnowledgeObjectVersionPo;
import com.cmbchina.infrastructure.knowledge.dao.po.KnowledgeVersionQueryPo;
import com.cmbchina.infrastructure.knowledge.dao.mapper.KnowledgeVersionMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.stream.Collectors;
/** 版本领域与持久化转换。 */
@Component
public class KnowledgeVersionSupportImpl implements KnowledgeVersionSupport {
    @Autowired
    private KnowledgeVersionMapper mapper;
    /** 在当前范围读取联动两端身份，不跨项目解析。 */
    public com.cmbchina.domain.knowledge.model.KnowledgeItem componentIdentity(KnowledgeVersionQuery value) {
        com.cmbchina.infrastructure.knowledge.dao.po.KnowledgePo row = mapper.componentIdentity(toPo(value));
        return row == null ? null : com.cmbchina.infrastructure.knowledge.dao.converter.KnowledgeDataConverter.convert(row);
    }

    /** objectVersion版本操作。 */
    public KnowledgeObjectVersion objectVersion(KnowledgeVersionQuery value) {
        return fromPo(mapper.objectVersion(toPo(value)));
    }

    /** objectHistory版本操作。 */
    public java.util.List<KnowledgeObjectVersion> objectHistory(KnowledgeVersionQuery value) {
        return mapper.objectHistory(toPo(value)).stream().map(this::fromPo).collect(Collectors.toList());
    }

    /** searchObjects版本操作。 */
    public java.util.List<KnowledgeObjectVersion> searchObjects(KnowledgeVersionQuery value) {
        return mapper.searchObjects(toPo(value)).stream().map(this::fromPo).collect(Collectors.toList());
    }

    /** lockObject版本操作。 */
    public java.util.List<KnowledgeObjectVersion> lockObject(KnowledgeVersionQuery value) {
        return mapper.lockObject(toPo(value)).stream().map(this::fromPo).collect(Collectors.toList());
    }

    /** currentVersion版本操作。 */
    public KnowledgeComponentVersion currentVersion(KnowledgeVersionQuery value) {
        return fromPo(mapper.currentVersion(toPo(value)));
    }

    /** componentVersion版本操作。 */
    public KnowledgeComponentVersion componentVersion(KnowledgeVersionQuery value) {
        return fromPo(mapper.componentVersion(toPo(value)));
    }

    /** componentHistory版本操作。 */
    public java.util.List<KnowledgeComponentVersion> componentHistory(KnowledgeVersionQuery value) {
        return mapper.componentHistory(toPo(value)).stream().map(this::fromPo).collect(Collectors.toList());
    }

    /** draft版本操作。 */
    public KnowledgeChangeDraft draft(KnowledgeVersionQuery value) {
        return fromPo(mapper.draft(toPo(value)));
    }

    /** 分页读取当前组件待确认草稿。 */
    public java.util.List<KnowledgeChangeDraft> pendingDrafts(KnowledgeVersionQuery value) {
        return mapper.pendingDrafts(toPo(value)).stream().map(this::fromPo).collect(Collectors.toList());
    }

    /** 由数据库统计同范围草稿总数。 */
    public long pendingDraftCount(KnowledgeVersionQuery value) {
        return mapper.pendingDraftCount(toPo(value));
    }

    /** requestDraft版本操作。 */
    public KnowledgeChangeDraft requestDraft(KnowledgeVersionQuery value) {
        return fromPo(mapper.requestDraft(toPo(value)));
    }

    /** lockDraft版本操作。 */
    public KnowledgeChangeDraft lockDraft(KnowledgeVersionQuery value) {
        return fromPo(mapper.lockDraft(toPo(value)));
    }

    /** lockComponent版本操作。 */
    public String lockComponent(KnowledgeVersionQuery value) {
        return mapper.lockComponent(toPo(value));
    }

    /** insertObject版本操作。 */
    public void insertObject(KnowledgeObjectVersion value) {
        mapper.insertObject(toPo(value));
    }

    /** insertComponentVersion版本操作。 */
    public void insertComponentVersion(KnowledgeComponentVersion value) {
        mapper.insertComponentVersion(toPo(value));
    }

    /** clearCurrent版本操作。 */
    public void clearCurrent(KnowledgeVersionQuery value) {
        mapper.clearCurrent(toPo(value));
    }

    /** insertDraft版本操作。 */
    public void insertDraft(KnowledgeChangeDraft value) {
        mapper.insertDraft(toPo(value));
    }

    /** updateDraft版本操作。 */
    public int updateDraft(KnowledgeChangeDraft value) {
        return mapper.updateDraft(toPo(value));
    }

    /** clearReferences版本操作。 */
    public void clearReferences(KnowledgeVersionQuery value) {
        mapper.clearReferences(toPo(value));
    }

    /** insertReference版本操作。 */
    public void insertReference(KnowledgeCurrentReference value) {
        mapper.insertReference(toPo(value));
    }

    /** referencingComponents版本操作。 */
    public java.util.List<String> referencingComponents(KnowledgeVersionQuery value) {
        return mapper.referencingComponents(toPo(value));
    }

    private KnowledgeObjectVersionPo toPo(KnowledgeObjectVersion value) {
        if (value == null) {
            return null;
        }
        return KnowledgeObjectVersionPo.builder().id(value.getId()).systemCode(value.getSystemCode()).projectCode(value.getProjectCode())
                .objectType(value.getObjectType()).objectId(value.getObjectId()).versionNo(value.getVersionNo())
                .previousVersionId(value.getPreviousVersionId()).schemaVersion(value.getSchemaVersion()).versionPurpose(value.getVersionPurpose())
                .lifecycleState(value.getLifecycleState()).confirmationStatus(value.getConfirmationStatus()).content(value.getContent())
                .evidence(value.getEvidence()).draftId(value.getDraftId()).createdAt(value.getCreatedAt()).build();
    }

    private KnowledgeObjectVersion fromPo(KnowledgeObjectVersionPo value) {
        if (value == null) {
            return null;
        }
        return KnowledgeObjectVersion.builder().id(value.getId()).systemCode(value.getSystemCode()).projectCode(value.getProjectCode())
                .objectType(value.getObjectType()).objectId(value.getObjectId()).versionNo(value.getVersionNo())
                .previousVersionId(value.getPreviousVersionId()).schemaVersion(value.getSchemaVersion()).versionPurpose(value.getVersionPurpose())
                .lifecycleState(value.getLifecycleState()).confirmationStatus(value.getConfirmationStatus()).content(value.getContent())
                .evidence(value.getEvidence()).draftId(value.getDraftId()).createdAt(value.getCreatedAt()).build();
    }

    private KnowledgeComponentVersionPo toPo(KnowledgeComponentVersion value) {
        if (value == null) {
            return null;
        }
        return KnowledgeComponentVersionPo.builder().id(value.getId()).componentId(value.getComponentId()).versionNo(value.getVersionNo())
                .previousVersionId(value.getPreviousVersionId()).manifest(value.getManifest()).draftId(value.getDraftId()).summary(value.getSummary())
                .restoredFromVersionId(value.getRestoredFromVersionId()).current(value.isCurrent()).createdAt(value.getCreatedAt()).build();
    }

    private KnowledgeComponentVersion fromPo(KnowledgeComponentVersionPo value) {
        if (value == null) {
            return null;
        }
        return KnowledgeComponentVersion.builder().id(value.getId()).componentId(value.getComponentId()).versionNo(value.getVersionNo())
                .previousVersionId(value.getPreviousVersionId()).manifest(value.getManifest()).draftId(value.getDraftId()).summary(value.getSummary())
                .restoredFromVersionId(value.getRestoredFromVersionId()).current(value.isCurrent()).createdAt(value.getCreatedAt()).build();
    }

    private KnowledgeChangeDraftPo toPo(KnowledgeChangeDraft value) {
        if (value == null) {
            return null;
        }
        return KnowledgeChangeDraftPo.builder().id(value.getId()).systemCode(value.getSystemCode()).projectCode(value.getProjectCode())
                .componentId(value.getComponentId()).origin(value.getOrigin()).revision(value.getRevision()).status(value.getStatus())
                .baseVersionId(value.getBaseVersionId()).objectBases(value.getObjectBases()).payload(value.getPayload())
                .selectedChanges(value.getSelectedChanges()).publishedResult(value.getPublishedResult()).requestId(value.getRequestId())
                .requestDigest(value.getRequestDigest()).createdAt(value.getCreatedAt()).updatedAt(value.getUpdatedAt()).build();
    }

    private KnowledgeChangeDraft fromPo(KnowledgeChangeDraftPo value) {
        if (value == null) {
            return null;
        }
        return KnowledgeChangeDraft.builder().id(value.getId()).systemCode(value.getSystemCode()).projectCode(value.getProjectCode())
                .componentId(value.getComponentId()).origin(value.getOrigin()).revision(value.getRevision()).status(value.getStatus())
                .baseVersionId(value.getBaseVersionId()).objectBases(value.getObjectBases()).payload(value.getPayload())
                .selectedChanges(value.getSelectedChanges()).publishedResult(value.getPublishedResult()).requestId(value.getRequestId())
                .requestDigest(value.getRequestDigest()).createdAt(value.getCreatedAt()).updatedAt(value.getUpdatedAt()).build();
    }

    private KnowledgeVersionQueryPo toPo(KnowledgeVersionQuery value) {
        if (value == null) {
            return null;
        }
        return KnowledgeVersionQueryPo.builder().systemCode(value.getSystemCode()).projectCode(value.getProjectCode())
                .componentId(value.getComponentId()).pagePath(value.getPagePath()).objectType(value.getObjectType()).objectId(value.getObjectId())
                .versionId(value.getVersionId()).draftId(value.getDraftId()).requestId(value.getRequestId()).keyword(value.getKeyword())
                .componentIds(value.getComponentIds()).versionIds(value.getVersionIds()).objectIds(value.getObjectIds()).offset(value.getOffset())
                .limit(value.getLimit()).build();
    }

    private KnowledgeVersionQuery fromPo(KnowledgeVersionQueryPo value) {
        if (value == null) {
            return null;
        }
        return KnowledgeVersionQuery.builder().systemCode(value.getSystemCode()).projectCode(value.getProjectCode())
                .componentId(value.getComponentId()).pagePath(value.getPagePath()).objectType(value.getObjectType()).objectId(value.getObjectId())
                .versionId(value.getVersionId()).draftId(value.getDraftId()).requestId(value.getRequestId()).keyword(value.getKeyword())
                .componentIds(value.getComponentIds()).versionIds(value.getVersionIds()).objectIds(value.getObjectIds()).offset(value.getOffset())
                .limit(value.getLimit()).build();
    }

    private KnowledgeCurrentReferencePo toPo(KnowledgeCurrentReference value) {
        if (value == null) {
            return null;
        }
        return KnowledgeCurrentReferencePo.builder().id(value.getId()).systemCode(value.getSystemCode()).projectCode(value.getProjectCode())
                .componentId(value.getComponentId()).componentVersionId(value.getComponentVersionId()).objectType(value.getObjectType())
                .objectId(value.getObjectId()).objectVersionId(value.getObjectVersionId()).build();
    }

    private KnowledgeCurrentReference fromPo(KnowledgeCurrentReferencePo value) {
        if (value == null) {
            return null;
        }
        return KnowledgeCurrentReference.builder().id(value.getId()).systemCode(value.getSystemCode()).projectCode(value.getProjectCode())
                .componentId(value.getComponentId()).componentVersionId(value.getComponentVersionId()).objectType(value.getObjectType())
                .objectId(value.getObjectId()).objectVersionId(value.getObjectVersionId()).build();
    }

    /** 批量读取同项目相关组件的当前版本。 */
    public java.util.List<KnowledgeComponentVersion> currentVersions(KnowledgeVersionQuery value) {
        return mapper.currentVersions(toPo(value)).stream().map(this::fromPo).collect(Collectors.toList());
    }

    /** 批量读取同项目明确指定的对象版本。 */
    public java.util.List<KnowledgeObjectVersion> objectVersions(KnowledgeVersionQuery value) {
        return mapper.objectVersions(toPo(value)).stream().map(this::fromPo).collect(Collectors.toList());
    }

    /** 批量读取页面已纳管的当前版本。 */
    public java.util.List<KnowledgeComponentVersion> pageCurrentVersions(KnowledgeVersionQuery value) {
        return mapper.pageCurrentVersions(toPo(value)).stream().map(this::fromPo).collect(Collectors.toList());
    }

    /** 批量读取对象当前状态，保留历史内容独立性。 */
    public java.util.List<KnowledgeObjectVersion> latestHeads(KnowledgeVersionQuery value) {
        return mapper.latestHeads(toPo(value)).stream().map(this::fromPo).collect(Collectors.toList());
    }

}
