-- 知识维护版本表。由部署人员核对目标库后执行，不自动连接或建表。
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
