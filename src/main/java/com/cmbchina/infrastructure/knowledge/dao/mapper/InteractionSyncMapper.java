package com.cmbchina.infrastructure.knowledge.dao.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 交互对象回写基础表的显式 SQL；不使用动态表名。 */
@Mapper
public interface InteractionSyncMapper {
    int upsertInteraction(@Param("row") Map<String, Object> row);

    int upsertRelation(@Param("row") Map<String, Object> row);

    int deleteRelations(@Param("componentId") String componentId, @Param("retained") List<String> retained);

    int deleteInteractions(@Param("componentId") String componentId, @Param("retained") List<String> retained);
}
