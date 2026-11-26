-- MySQL 8.0 组件知识库初始化 DDL，共 18 张表。
-- 执行前选定全新数据库。本脚本仅创建表结构。
SET NAMES utf8mb4;

CREATE TABLE knowledge_project (
    id varchar(64) NOT NULL COMMENT '主键由CUuid生成',
    system_code varchar(64) NOT NULL COMMENT '系统编号',
    project_code varchar(64) NOT NULL COMMENT '项目号',
    PRIMARY KEY (id), UNIQUE KEY uk_project (system_code, project_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识查询project';

CREATE TABLE knowledge_service (
    id varchar(64) NOT NULL COMMENT '主键由CUuid生成',
    system_code varchar(64) NOT NULL COMMENT '系统编号',
    project_code varchar(64) NOT NULL COMMENT '项目号',
    service_code varchar(64) NOT NULL COMMENT '服务标识',
    service_type varchar(16) NOT NULL COMMENT '代码类型FRONTEND前端BACKEND后端',
    local_path varchar(1024) NULL COMMENT '本地代码目录',
    PRIMARY KEY (id), UNIQUE KEY uk_service (system_code, project_code, service_type, service_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识查询service';

CREATE TABLE knowledge_page (
    id varchar(64) NOT NULL COMMENT '主键由CUuid生成',
    system_code varchar(64) NOT NULL COMMENT '系统编号',
    project_code varchar(64) NOT NULL COMMENT '项目号',
    page_path varchar(255) NOT NULL COMMENT '页面路径',
    name varchar(255) NULL COMMENT '页面名称',
    description text NULL COMMENT '业务功能描述',
    PRIMARY KEY (id), UNIQUE KEY uk_page (system_code, project_code, page_path)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识查询page';

CREATE TABLE knowledge_component (
    id varchar(64) NOT NULL COMMENT '主键由CUuid生成',
    page_id varchar(64) NOT NULL COMMENT '页面编号',
    component_id varchar(128) NOT NULL COMMENT '页面内组件标识',
    name varchar(255) NULL COMMENT '组件名称',
    description text NULL COMMENT '业务功能描述',
    PRIMARY KEY (id), UNIQUE KEY uk_component (page_id, component_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识查询component';

CREATE TABLE knowledge_flow (
    id varchar(64) NOT NULL COMMENT '主键由CUuid生成',
    system_code varchar(64) NOT NULL COMMENT '系统编号',
    project_code varchar(64) NOT NULL COMMENT '项目号',
    name varchar(255) NOT NULL COMMENT '流程名称',
    PRIMARY KEY (id), KEY idx_flow_project (system_code, project_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识查询flow';

CREATE TABLE knowledge_page_flow (
    id varchar(64) NOT NULL COMMENT '主键由CUuid生成',
    page_id varchar(64) NOT NULL COMMENT '页面编号',
    flow_id varchar(64) NOT NULL COMMENT '流程编号',
    PRIMARY KEY (id), UNIQUE KEY uk_page_flow (page_id, flow_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识查询page_flow';

CREATE TABLE knowledge_interface (
    id varchar(64) NOT NULL COMMENT '主键由CUuid生成',
    service_id varchar(64) NOT NULL COMMENT '服务编号',
    method varchar(16) NOT NULL COMMENT 'HTTP方法大写',
    path varchar(255) NOT NULL COMMENT '实际接口路径或整段参数模板',
    name varchar(255) NULL COMMENT '接口名称',
    description text NULL COMMENT '接口用途',
    request_content_type varchar(128) NULL COMMENT '请求媒体类型',
    response_content_type varchar(128) NULL COMMENT '响应媒体类型',
    PRIMARY KEY (id), UNIQUE KEY uk_interface (service_id, method, path)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识查询interface';

CREATE TABLE knowledge_entry (
    id varchar(64) NOT NULL COMMENT '主键由CUuid生成',
    system_code varchar(64) NOT NULL COMMENT '系统编号',
    project_code varchar(64) NOT NULL COMMENT '项目号',
    kind varchar(32) NOT NULL COMMENT '知识类别',
    content text NOT NULL COMMENT '知识正文',
    source text NULL COMMENT '知识来源',
    status varchar(16) NOT NULL COMMENT 'CONFIRMED已确认PENDING待确认EXPIRED失效',
    title varchar(255) NULL COMMENT '知识标题',
    knowledge_type varchar(16) NULL COMMENT 'BUSINESS业务 TECHNICAL技术',
    PRIMARY KEY (id), KEY idx_entry_project (system_code, project_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识查询entry';

CREATE TABLE knowledge_applicability (
    id varchar(64) NOT NULL COMMENT '主键由CUuid生成',
    knowledge_id varchar(64) NOT NULL COMMENT '知识编号',
    target_type varchar(16) NOT NULL COMMENT 'COMPONENT PAGE FLOW INTERFACE',
    target_id varchar(64) NOT NULL COMMENT '关联对象主键',
    condition_text text NULL COMMENT '适用条件未知时为空',
    unconditional tinyint NOT NULL COMMENT '是否明确无条件适用',
    exception_text text NULL COMMENT '明确例外及已知冲突依据',
    replaces_knowledge_id varchar(64) NULL COMMENT '明确替代的知识编号',
    PRIMARY KEY (id), KEY idx_applicability_target (target_type, target_id), KEY idx_applicability_knowledge (knowledge_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识查询applicability';

CREATE TABLE knowledge_interface_field (
    id varchar(64) NOT NULL COMMENT 'CUuid主键',
    interface_id varchar(64) NOT NULL COMMENT '接口编号',
    direction varchar(16) NOT NULL COMMENT 'REQUEST入参 RESPONSE出参',
    location varchar(16) NOT NULL COMMENT '参数位置',
    parent_id varchar(64) NULL COMMENT '父字段编号',
    field_name varchar(128) NOT NULL COMMENT '字段名称',
    field_path varchar(255) NOT NULL COMMENT '完整字段路径',
    value_type varchar(64) NOT NULL COMMENT '数据类型',
    item_type varchar(64) NULL COMMENT '数组元素类型',
    required_flag tinyint NOT NULL COMMENT '是否必须存在',
    nullable_flag tinyint NOT NULL COMMENT '是否允许空值',
    business_meaning text NULL COMMENT '业务含义',
    technical_description text NULL COMMENT '技术说明',
    validation_text text NULL COMMENT '校验要求',
    sort_order int NOT NULL COMMENT '排序序号',
    PRIMARY KEY(id), UNIQUE KEY uk_interface_field(interface_id,direction,location,field_path)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='结构化接口field';

CREATE TABLE knowledge_interface_example (
    id varchar(64) NOT NULL COMMENT 'CUuid主键',
    interface_id varchar(64) NOT NULL COMMENT '接口编号',
    name varchar(255) NOT NULL COMMENT '示例名称',
    description text NOT NULL COMMENT '示例来源及适用说明',
    request_body text NOT NULL COMMENT '合法JSON请求值',
    response_body text NOT NULL COMMENT '合法JSON响应值',
    sort_order int NOT NULL COMMENT '排序序号',
    PRIMARY KEY(id), KEY idx_example_interface(interface_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='结构化接口example';

CREATE TABLE knowledge_interface_error (
    id varchar(64) NOT NULL COMMENT 'CUuid主键',
    interface_id varchar(64) NOT NULL COMMENT '接口编号',
    code varchar(32) NOT NULL COMMENT '业务错误码',
    http_status int NOT NULL COMMENT 'HTTP状态',
    description text NOT NULL COMMENT '错误含义',
    trigger_condition text NOT NULL COMMENT '触发条件',
    sort_order int NOT NULL COMMENT '排序序号',
    PRIMARY KEY(id), UNIQUE KEY uk_interface_error(interface_id,code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='结构化接口error';

CREATE TABLE knowledge_component_interaction (
    id varchar(64) NOT NULL COMMENT '交互主键由CUuid生成',
    component_id varchar(64) NOT NULL COMMENT '所属组件knowledge_component数据库主键',
    action_name varchar(255) NOT NULL COMMENT '用户操作或组件触发行为名称',
    trigger_condition text NULL COMMENT '操作触发条件，空表示未登记条件而非无条件',
    provided_content text NULL COMMENT '传递给关联组件的业务内容',
    response_text text NULL COMMENT '当前组件自身的响应或界面变化',
    sort_order int NOT NULL DEFAULT 0 COMMENT '所属组件内交互展示顺序',
    PRIMARY KEY (id),
    KEY idx_interaction_component (component_id,sort_order,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='组件交互逻辑';

CREATE TABLE knowledge_component_relation (
    id varchar(64) NOT NULL COMMENT '联动关系主键由CUuid生成',
    interaction_id varchar(64) NOT NULL COMMENT '来源交互knowledge_component_interaction主键',
    target_component_id varchar(64) NOT NULL COMMENT '目标组件knowledge_component数据库主键',
    effect_text text NOT NULL COMMENT '操作对目标组件产生的具体影响',
    condition_text text NULL COMMENT '本条联动的生效条件，空表示未登记条件',
    sort_order int NOT NULL DEFAULT 0 COMMENT '同一交互下目标联动展示顺序',
    PRIMARY KEY (id),
    KEY idx_relation_interaction (interaction_id,sort_order,id),
    KEY idx_relation_target (target_component_id,interaction_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='组件有向联动关系';

CREATE TABLE knowledge_object_version (
 id varchar(64) NOT NULL COMMENT '对象版本主键由CUuid生成',
 system_code varchar(64) NOT NULL COMMENT '系统编号',
 project_code varchar(64) NOT NULL COMMENT '项目编号',
 object_type varchar(64) NOT NULL COMMENT '知识对象类型',
 object_id varchar(64) NOT NULL COMMENT '知识对象稳定主键',
 version_no int NOT NULL COMMENT '对象内递增版本号',
 previous_version_id varchar(64) NULL COMMENT '前一对象版本主键，首次为空',
 schema_version int NOT NULL COMMENT '载荷结构规范版本',
 version_purpose varchar(64) NOT NULL COMMENT 'NORMAL正式内容或BASELINE历史基线',
 lifecycle_state varchar(64) NOT NULL COMMENT 'ACTIVE可用或EXPIRED已停用',
 confirmation_status varchar(64) NULL COMMENT 'CONFIRMED已确认或PENDING待确认',
 content text NOT NULL COMMENT '当前对象版本完整结构化正文',
 evidence text NOT NULL COMMENT '依据分类、来源定位及必要摘录',
 draft_id varchar(64) NOT NULL COMMENT '发布所属草稿主键',
 created_at varchar(64) NOT NULL COMMENT '创建时间，UTC ISO8601',
 PRIMARY KEY (id),
 UNIQUE KEY uk_object_version (system_code,project_code,object_type,object_id,version_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='不可变知识对象版本';

CREATE TABLE knowledge_component_version (
 id varchar(64) NOT NULL COMMENT '组件版本主键由CUuid生成',
 component_id varchar(64) NOT NULL COMMENT '组件数据库主键',
 version_no int NOT NULL COMMENT '组件内递增版本号',
 previous_version_id varchar(64) NULL COMMENT '前一组件版本主键，首次为空',
 manifest text NOT NULL COMMENT '完整有序对象版本引用清单',
 draft_id varchar(64) NOT NULL COMMENT '本次发布所属草稿主键',
 summary varchar(255) NULL COMMENT '发布或恢复摘要',
 restored_from_version_id varchar(64) NULL COMMENT '恢复目标历史组件版本主键',
 is_current tinyint NOT NULL COMMENT '是否为当前已发布版本',
 created_at varchar(64) NOT NULL COMMENT '创建时间，UTC ISO8601',
 PRIMARY KEY (id),
 UNIQUE KEY uk_component_version (component_id,version_no),
 KEY idx_component_current (component_id,is_current)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='组件完整知识版本清单';

CREATE TABLE knowledge_change_draft (
 id varchar(64) NOT NULL COMMENT '草稿主键由CUuid生成',
 system_code varchar(64) NOT NULL COMMENT '系统编号',
 project_code varchar(64) NOT NULL COMMENT '项目编号',
 component_id varchar(64) NULL COMMENT '当前维护组件的数据库主键',
 origin varchar(64) NOT NULL COMMENT 'SESSION会话MANUAL页面RESTORE恢复MIGRATION迁移',
 revision int NOT NULL COMMENT '草稿修订号，每次修改递增',
 status varchar(64) NOT NULL COMMENT 'DRAFT待确认PUBLISHED已发布DISCARDED已丢弃',
 base_version_id varchar(64) NULL COMMENT '创建草稿时组件基准版本主键',
 object_bases text NULL COMMENT '待编辑对象的原版本信息',
 payload text NOT NULL COMMENT '候选变化、身份及独立原始快照',
 selected_changes text NOT NULL COMMENT '最终接受的变更编号清单',
 published_result text NULL COMMENT '幂等发布结果及对象版本映射',
 request_id varchar(255) NOT NULL COMMENT '调用方幂等请求编号',
 request_digest varchar(64) NOT NULL COMMENT '请求内容摘要，检测同键不同内容',
 created_at varchar(64) NOT NULL COMMENT '创建时间，UTC ISO8601',
 updated_at varchar(64) NOT NULL COMMENT '最后修改时间，UTC ISO8601',
 PRIMARY KEY (id),
 UNIQUE KEY uk_draft_request (system_code,project_code,request_id),
 KEY idx_draft_component (component_id,status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='可修订知识候选及发布结果';

CREATE TABLE knowledge_component_reference (
 id varchar(64) NOT NULL COMMENT 'CUuid引用索引主键',
 system_code varchar(64) NOT NULL COMMENT '系统编号',
 project_code varchar(64) NOT NULL COMMENT '项目编号',
 component_id varchar(64) NOT NULL COMMENT '组件数据库主键',
 component_version_id varchar(64) NOT NULL COMMENT '当前组件版本主键',
 object_type varchar(64) NOT NULL COMMENT '对象类型',
 object_id varchar(64) NOT NULL COMMENT '对象稳定主键',
 object_version_id varchar(64) NOT NULL COMMENT '对象版本主键',
 PRIMARY KEY (id),
 UNIQUE KEY uk_component_reference (component_id,object_type,object_id),
 KEY idx_object_reference (system_code,project_code,object_type,object_id,component_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='当前组件引用可重建索引';
