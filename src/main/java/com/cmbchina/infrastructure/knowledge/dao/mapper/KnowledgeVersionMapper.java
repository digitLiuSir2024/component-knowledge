package com.cmbchina.infrastructure.knowledge.dao.mapper;
import com.cmbchina.infrastructure.knowledge.dao.po.KnowledgeChangeDraftPo;
import com.cmbchina.infrastructure.knowledge.dao.po.KnowledgeComponentVersionPo;
import com.cmbchina.infrastructure.knowledge.dao.po.KnowledgeCurrentReferencePo;
import com.cmbchina.infrastructure.knowledge.dao.po.KnowledgeObjectVersionPo;
import com.cmbchina.infrastructure.knowledge.dao.po.KnowledgeVersionQueryPo;
import org.apache.ibatis.annotations.Mapper;
/** 版本手写SQL入口。 */
@Mapper
public interface KnowledgeVersionMapper {
    com.cmbchina.infrastructure.knowledge.dao.po.KnowledgePo componentIdentity(KnowledgeVersionQueryPo value);

    KnowledgeObjectVersionPo objectVersion(KnowledgeVersionQueryPo value);

    java.util.List<KnowledgeObjectVersionPo> objectHistory(KnowledgeVersionQueryPo value);

    java.util.List<KnowledgeObjectVersionPo> searchObjects(KnowledgeVersionQueryPo value);

    java.util.List<KnowledgeObjectVersionPo> lockObject(KnowledgeVersionQueryPo value);

    KnowledgeComponentVersionPo currentVersion(KnowledgeVersionQueryPo value);

    KnowledgeComponentVersionPo componentVersion(KnowledgeVersionQueryPo value);

    java.util.List<KnowledgeComponentVersionPo> componentHistory(KnowledgeVersionQueryPo value);

    KnowledgeChangeDraftPo draft(KnowledgeVersionQueryPo value);

    java.util.List<KnowledgeChangeDraftPo> pendingDrafts(KnowledgeVersionQueryPo value);

    long pendingDraftCount(KnowledgeVersionQueryPo value);

    KnowledgeChangeDraftPo requestDraft(KnowledgeVersionQueryPo value);

    KnowledgeChangeDraftPo lockDraft(KnowledgeVersionQueryPo value);

    String lockComponent(KnowledgeVersionQueryPo value);

    void insertObject(KnowledgeObjectVersionPo value);

    void insertComponentVersion(KnowledgeComponentVersionPo value);

    void clearCurrent(KnowledgeVersionQueryPo value);

    void insertDraft(KnowledgeChangeDraftPo value);

    int updateDraft(KnowledgeChangeDraftPo value);

    void clearReferences(KnowledgeVersionQueryPo value);

    void insertReference(KnowledgeCurrentReferencePo value);

    java.util.List<String> referencingComponents(KnowledgeVersionQueryPo value);

    java.util.List<KnowledgeComponentVersionPo> currentVersions(KnowledgeVersionQueryPo value);

    java.util.List<KnowledgeObjectVersionPo> objectVersions(KnowledgeVersionQueryPo value);

    java.util.List<KnowledgeComponentVersionPo> pageCurrentVersions(KnowledgeVersionQueryPo value);

    /** 批量读取对象当前生命周期头。 */
    java.util.List<KnowledgeObjectVersionPo> latestHeads(KnowledgeVersionQueryPo value);

}
