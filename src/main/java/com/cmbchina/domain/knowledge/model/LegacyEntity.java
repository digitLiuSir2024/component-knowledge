package com.cmbchina.domain.knowledge.model;

import com.google.common.collect.ImmutableSet;
import java.util.Set;

/** 允许运行时读写的 legacy 基础表白名单，列名与 init.sql 一致。 */
public enum LegacyEntity {
    PROJECT("knowledge_project", "id", "id", "system_code", "project_code"),
    SERVICE("knowledge_service", "id", "id", "system_code", "project_code", "service_code", "service_type", "local_path"),
    PAGE("knowledge_page", "id", "id", "system_code", "project_code", "page_path", "name", "description"),
    COMPONENT("knowledge_component", "id", "id", "page_id", "component_id", "name", "description"),
    FLOW("knowledge_flow", "id", "id", "system_code", "project_code", "name"),
    PAGE_FLOW("knowledge_page_flow", "id", "id", "page_id", "flow_id"),
    INTERFACE("knowledge_interface", "id", "id", "service_id", "method", "path", "name", "description", "request_content_type",
            "response_content_type"),
    ENTRY("knowledge_entry", "id", "id", "system_code", "project_code", "kind", "content", "source", "status", "title", "knowledge_type"),
    APPLICABILITY("knowledge_applicability", "id", "id", "knowledge_id", "target_type", "target_id", "condition_text", "unconditional",
            "exception_text", "replaces_knowledge_id"),
    INTERFACE_FIELD("knowledge_interface_field", "id", "id", "interface_id", "direction", "location", "parent_id", "field_name", "field_path",
            "value_type", "item_type", "required_flag", "nullable_flag", "business_meaning", "technical_description", "validation_text",
            "sort_order"),
    INTERFACE_EXAMPLE("knowledge_interface_example", "id", "id", "interface_id", "name", "description", "request_body", "response_body",
            "sort_order"),
    INTERFACE_ERROR("knowledge_interface_error", "id", "id", "interface_id", "code", "http_status", "description", "trigger_condition", "sort_order"),
    COMPONENT_INTERACTION("knowledge_component_interaction", "id", "id", "component_id", "action_name", "trigger_condition", "provided_content",
            "response_text", "sort_order"),
    COMPONENT_RELATION("knowledge_component_relation", "id", "id", "interaction_id", "target_component_id", "effect_text", "condition_text",
            "sort_order");

    private final String table;
    private final String primaryKey;
    private final Set<String> columns;

    LegacyEntity(String table, String primaryKey, String... columns) {
        this.table = table;
        this.primaryKey = primaryKey;
        this.columns = ImmutableSet.copyOf(columns);
    }

    public String table() {
        return table;
    }

    public String primaryKey() {
        return primaryKey;
    }

    public Set<String> columns() {
        return columns;
    }

    public boolean has(String column) {
        return columns.contains(column);
    }

    /** 接受枚举名或表名，均大小写不敏感。 */
    public static LegacyEntity parse(String value) {
        if (value == null) {
            return null;
        }
        for (LegacyEntity entity : values()) {
            if (entity.name().equalsIgnoreCase(value) || entity.table.equalsIgnoreCase(value)) {
                return entity;
            }
        }
        return null;
    }
}
