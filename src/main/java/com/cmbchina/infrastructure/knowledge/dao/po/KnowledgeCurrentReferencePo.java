package com.cmbchina.infrastructure.knowledge.dao.po;
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
public class KnowledgeCurrentReferencePo {
    private String id;
    private String systemCode;
    private String projectCode;
    private String componentId;
    private String componentVersionId;
    private String objectType;
    private String objectId;
    private String objectVersionId;
}
