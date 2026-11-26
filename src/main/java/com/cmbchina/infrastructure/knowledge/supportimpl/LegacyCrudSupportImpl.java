package com.cmbchina.infrastructure.knowledge.supportimpl;

import com.cmbchina.domain.knowledge.model.LegacyEntity;
import com.cmbchina.domain.knowledge.support.LegacyCrudSupport;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.util.List;
import java.util.Map;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** 通用读写实现；所有动态表名和列名在调用前经 LegacyEntity 白名单过滤。 */
@Component
public class LegacyCrudSupportImpl implements LegacyCrudSupport {
    private static final String NS = "com.cmbchina.infrastructure.knowledge.dao.mapper.LegacyCrudMapper.";
    @Autowired
    private SqlSessionTemplate sessions;

    public Map<String, Object> get(LegacyEntity entity, Map<String, Object> keys) {
        return sessions.selectOne(NS + "get", param(entity, keys, Maps.newHashMap(), 0, 1));
    }

    public List<Map<String, Object>> list(LegacyEntity entity, Map<String, Object> filters, int offset, int limit) {
        return sessions.selectList(NS + "list", param(entity, filters, Maps.newHashMap(), offset, limit));
    }

    public int upsert(LegacyEntity entity, Map<String, Object> row) {
        return sessions.insert(NS + "upsert", param(entity, Maps.newHashMap(), row, 0, 1));
    }

    public int delete(LegacyEntity entity, Map<String, Object> keys) {
        Map<String, Object> values = Maps.newHashMap();
        values.put("primaryKey", keys.get(entity.primaryKey()));
        Map<String, Object> parameters = param(entity, Maps.newHashMap(), Maps.newHashMap(), 0, 1);
        parameters.put("primaryKey", values.get("primaryKey"));
        return sessions.delete(NS + "delete", parameters);
    }

    private Map<String, Object> param(LegacyEntity entity, Map<String, Object> filters, Map<String, Object> row, int offset, int limit) {
        Map<String, Object> result = Maps.newHashMap();
        result.put("table", entity.table());
        result.put("primaryKey", entity.primaryKey());
        result.put("columns", Lists.newArrayList(entity.columns()));
        result.put("setColumns", setColumns(entity));
        result.put("filters", filters);
        result.put("row", row);
        result.put("offset", offset);
        result.put("limit", limit);
        return result;
    }

    private List<String> setColumns(LegacyEntity entity) {
        List<String> result = Lists.newArrayList(entity.columns());
        result.remove(entity.primaryKey());
        return result;
    }
}
