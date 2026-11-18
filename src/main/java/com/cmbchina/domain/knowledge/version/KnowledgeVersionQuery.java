package com.cmbchina.domain.knowledge.version;
import lombok.Getter;
import lombok.Setter;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
/** 知识版本持久化边界数据。 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeVersionQuery {
    private String systemCode;
    private String projectCode;
    private String componentId;
    private String pagePath;
    private String objectType;
    private String objectId;
    private String versionId;
    private String draftId;
    private String requestId;
    private String keyword;
    private java.util.List<String> componentIds;
    private java.util.List<String> versionIds;
    private java.util.List<String> objectIds;
    private int offset;
    private int limit;
}
