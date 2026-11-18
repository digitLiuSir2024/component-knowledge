package com.cmbchina.infrastructure.knowledge.supportimpl;
import com.cmbchina.domain.knowledge.model.KnowledgeQuery;
import com.cmbchina.domain.knowledge.model.KnowledgeItem;
import com.cmbchina.domain.knowledge.support.KnowledgeDataSupport;
import com.cmbchina.infrastructure.knowledge.dao.mapper.KnowledgeMapper;
import com.cmbchina.infrastructure.knowledge.dao.converter.KnowledgeDataConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
/** 数据库知识读取。 */
@Component
public class KnowledgeDataSupportImpl implements KnowledgeDataSupport {
    @Autowired
    private KnowledgeMapper mapper;
    /** 删除指定组件登记及专属知识。 */
    public void purgeComponent(String componentId) {
        mapper.deleteComponentObjectVersions(componentId);
        mapper.deleteComponentReferences(componentId);
        mapper.deleteComponentVersions(componentId);
        mapper.deleteComponentDrafts(componentId);
        mapper.deleteComponentRelations(componentId);
        mapper.deleteComponentInteractions(componentId);
        mapper.deleteComponentEntries(componentId);
        mapper.deleteComponentApplicability(componentId);
        mapper.deleteComponent(componentId);
    }

    /** 在已登记页面内建立组件身份。 */
    public void registerComponent(KnowledgeQuery query, String id) {
        mapper.registerComponent(query, id);
    }

    /** 读取知识关联数据。 */
    public java.util.List<KnowledgeItem> projects(KnowledgeQuery query) {
        return mapper.projects(query).stream().map(KnowledgeDataConverter::convert).collect(java.util.stream.Collectors.toList());
    }

    /** 读取知识关联数据。 */
    public java.util.List<KnowledgeItem> components(KnowledgeQuery query) {
        return mapper.components(query).stream().map(KnowledgeDataConverter::convert).collect(java.util.stream.Collectors.toList());
    }

    /** 读取页面内登记的组件名称。 */
    public java.util.List<KnowledgeItem> pageComponents(KnowledgeQuery query) {
        return mapper.pageComponents(query).stream().map(KnowledgeDataConverter::convert).collect(java.util.stream.Collectors.toList());
    }

    /** 读取知识关联数据。 */
    public java.util.List<KnowledgeItem> scopes(KnowledgeQuery query) {
        return mapper.scopes(query).stream().map(KnowledgeDataConverter::convert).collect(java.util.stream.Collectors.toList());
    }

    /** 读取知识关联数据。 */
    public java.util.List<KnowledgeItem> services(KnowledgeQuery query) {
        return mapper.services(query).stream().map(KnowledgeDataConverter::convert).collect(java.util.stream.Collectors.toList());
    }

    /** 读取知识关联数据。 */
    public java.util.List<KnowledgeItem> interfaces(KnowledgeQuery query) {
        return mapper.interfaces(query).stream().map(KnowledgeDataConverter::convert).collect(java.util.stream.Collectors.toList());
    }

    /** 读取知识关联数据。 */
    public java.util.List<KnowledgeItem> knowledge(KnowledgeQuery query) {
        return mapper.knowledge(query).stream().map(KnowledgeDataConverter::convert).collect(java.util.stream.Collectors.toList());
    }

    /** 批量读取结构化上下文。 */
    public java.util.List<com.cmbchina.domain.knowledge.model.ContractField> fields(KnowledgeQuery query) {
        return mapper.fields(query).stream().map(KnowledgeDataConverter::convert).collect(java.util.stream.Collectors.toList());
    }

    /** 批量读取结构化上下文。 */
    public java.util.List<com.cmbchina.domain.knowledge.model.ContractExample> examples(KnowledgeQuery query) {
        return mapper.examples(query).stream().map(KnowledgeDataConverter::convert).collect(java.util.stream.Collectors.toList());
    }

    /** 批量读取结构化上下文。 */
    public java.util.List<com.cmbchina.domain.knowledge.model.ContractError> errors(KnowledgeQuery query) {
        return mapper.errors(query).stream().map(KnowledgeDataConverter::convert).collect(java.util.stream.Collectors.toList());
    }

    /** 读取当前页面基本信息。 */
    public java.util.List<KnowledgeItem> pages(KnowledgeQuery query) {
        return mapper.pages(query).stream().map(KnowledgeDataConverter::convert).collect(java.util.stream.Collectors.toList());
    }

    /** 查询其他组件的知识关系。 */
    public java.util.List<KnowledgeItem> relatedComponents(KnowledgeQuery query) {
        return mapper.relatedComponents(query).stream().map(KnowledgeDataConverter::convert).collect(java.util.stream.Collectors.toList());
    }

    /** 读取模型补充资料。 */
    public java.util.List<KnowledgeItem> knowledgeByIds(KnowledgeQuery query) {
        return mapper.knowledgeByIds(query).stream().map(KnowledgeDataConverter::convert).collect(java.util.stream.Collectors.toList());
    }

    /** 读取模型补充资料。 */
    public java.util.List<KnowledgeItem> interfacesByIds(KnowledgeQuery query) {
        return mapper.interfacesByIds(query).stream().map(KnowledgeDataConverter::convert).collect(java.util.stream.Collectors.toList());
    }

    /** 读取组件交互。 */
    public java.util.List<com.cmbchina.domain.knowledge.model.ComponentInteraction> interactions(KnowledgeQuery query) {
        return mapper.interactions(query).stream().map(KnowledgeDataConverter::convert).collect(java.util.stream.Collectors.toList());
    }

    /** 读取组件有向联动。 */
    public java.util.List<com.cmbchina.domain.knowledge.model.ComponentRelation> componentRelations(KnowledgeQuery query) {
        return mapper.componentRelations(query).stream().map(KnowledgeDataConverter::convert).collect(java.util.stream.Collectors.toList());
    }

    /** 读取页面关联流程的原始说明。 */
    public java.util.List<KnowledgeItem> pageFlows(KnowledgeQuery query) {
        return mapper.pageFlows(query).stream().map(KnowledgeDataConverter::convert).collect(java.util.stream.Collectors.toList());
    }

}
