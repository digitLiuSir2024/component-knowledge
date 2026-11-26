package com.cmbchina.domain.knowledge.support;

import com.cmbchina.domain.knowledge.model.LegacyEntity;
import java.util.List;
import java.util.Map;

/** legacy 基础表的通用读写边界，列名由实体白名单约束。 */
public interface LegacyCrudSupport {
    Map<String, Object> get(LegacyEntity entity, Map<String, Object> keys);

    List<Map<String, Object>> list(LegacyEntity entity, Map<String, Object> filters, int offset, int limit);

    int upsert(LegacyEntity entity, Map<String, Object> row);

    int delete(LegacyEntity entity, Map<String, Object> keys);
}
