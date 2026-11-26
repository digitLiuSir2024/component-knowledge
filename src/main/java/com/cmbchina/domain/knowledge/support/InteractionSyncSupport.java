package com.cmbchina.domain.knowledge.support;

import com.cmbchina.domain.knowledge.version.KnowledgeObjectVersion;
import java.util.List;

/** 版本对象发布后回写 legacy 交互基础表的边界。 */
public interface InteractionSyncSupport {
    void sync(String componentId, List<KnowledgeObjectVersion> objects);
}
