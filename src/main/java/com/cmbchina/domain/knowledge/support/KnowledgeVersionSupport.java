package com.cmbchina.domain.knowledge.support;
import com.cmbchina.domain.knowledge.version.KnowledgeChangeDraft;
import com.cmbchina.domain.knowledge.version.KnowledgeComponentVersion;
import com.cmbchina.domain.knowledge.version.KnowledgeCurrentReference;
import com.cmbchina.domain.knowledge.version.KnowledgeObjectVersion;
import com.cmbchina.domain.knowledge.version.KnowledgeVersionQuery;
/** 知识版本存取及锁边界。 */
public interface KnowledgeVersionSupport {
    /** 在明确系统项目内读取组件定位身份。 */
    com.cmbchina.domain.knowledge.model.KnowledgeItem componentIdentity(KnowledgeVersionQuery value);

    /** objectVersion版本操作。 */
    KnowledgeObjectVersion objectVersion(KnowledgeVersionQuery value);

    /** objectHistory版本操作。 */
    java.util.List<KnowledgeObjectVersion> objectHistory(KnowledgeVersionQuery value);

    /** searchObjects版本操作。 */
    java.util.List<KnowledgeObjectVersion> searchObjects(KnowledgeVersionQuery value);

    /** lockObject版本操作。 */
    java.util.List<KnowledgeObjectVersion> lockObject(KnowledgeVersionQuery value);

    /** currentVersion版本操作。 */
    KnowledgeComponentVersion currentVersion(KnowledgeVersionQuery value);

    /** componentVersion版本操作。 */
    KnowledgeComponentVersion componentVersion(KnowledgeVersionQuery value);

    /** componentHistory版本操作。 */
    java.util.List<KnowledgeComponentVersion> componentHistory(KnowledgeVersionQuery value);

    /** draft版本操作。 */
    KnowledgeChangeDraft draft(KnowledgeVersionQuery value);

    /** 分页读取本组件待确认草稿。 */
    java.util.List<KnowledgeChangeDraft> pendingDrafts(KnowledgeVersionQuery value);

    /** 本组件待确认草稿总数。 */
    long pendingDraftCount(KnowledgeVersionQuery value);

    /** requestDraft版本操作。 */
    KnowledgeChangeDraft requestDraft(KnowledgeVersionQuery value);

    /** lockDraft版本操作。 */
    KnowledgeChangeDraft lockDraft(KnowledgeVersionQuery value);

    /** lockComponent版本操作。 */
    String lockComponent(KnowledgeVersionQuery value);

    /** insertObject版本操作。 */
    void insertObject(KnowledgeObjectVersion value);

    /** insertComponentVersion版本操作。 */
    void insertComponentVersion(KnowledgeComponentVersion value);

    /** clearCurrent版本操作。 */
    void clearCurrent(KnowledgeVersionQuery value);

    /** insertDraft版本操作。 */
    void insertDraft(KnowledgeChangeDraft value);

    /** updateDraft版本操作。 */
    int updateDraft(KnowledgeChangeDraft value);

    /** clearReferences版本操作。 */
    void clearReferences(KnowledgeVersionQuery value);

    /** insertReference版本操作。 */
    void insertReference(KnowledgeCurrentReference value);

    /** referencingComponents版本操作。 */
    java.util.List<String> referencingComponents(KnowledgeVersionQuery value);

    java.util.List<KnowledgeComponentVersion> currentVersions(KnowledgeVersionQuery value);

    java.util.List<KnowledgeObjectVersion> objectVersions(KnowledgeVersionQuery value);

    java.util.List<KnowledgeComponentVersion> pageCurrentVersions(KnowledgeVersionQuery value);

    /** 批量读取对象当前生命周期头。 */
    java.util.List<KnowledgeObjectVersion> latestHeads(KnowledgeVersionQuery value);

}
