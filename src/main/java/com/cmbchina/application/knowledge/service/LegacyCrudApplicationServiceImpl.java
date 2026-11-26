package com.cmbchina.application.knowledge.service;

import com.cmbchina.application.knowledge.api.LegacyCrudApplicationService;
import com.cmbchina.application.knowledge.api.dto.maintenance.LegacyCrudRequest;
import com.cmbchina.application.knowledge.support.KnowledgeMaintenanceCodec;
import com.cmbchina.domain.knowledge.model.LegacyEntity;
import com.cmbchina.domain.knowledge.support.LegacyCrudSupport;
import com.cmbchina.infrastructure.common.ResponseEnum;
import com.cmbchina.infrastructure.common.util.CUuid;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Maps;
import java.util.Iterator;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 列名白名单、主键生成与分页边界在应用边界统一校验。 */
@Service
public class LegacyCrudApplicationServiceImpl implements LegacyCrudApplicationService {
    @Autowired
    private LegacyCrudSupport support;
    @Autowired
    private KnowledgeMaintenanceCodec json;

    public JsonNode get(LegacyCrudRequest request) {
        LegacyEntity entity = entity(request);
        Map<String, Object> keys = keys(entity, request.getKeys());
        Map<String, Object> row = support.get(entity, keys);
        json.require(row != null, ResponseEnum.KNOWLEDGE_NOT_FOUND);
        return json.tree(row);
    }

    public JsonNode list(LegacyCrudRequest request) {
        LegacyEntity entity = entity(request);
        int offset = request.getOffset() == null ? 0 : request.getOffset();
        int limit = request.getLimit() == null ? 20 : request.getLimit();
        json.require(offset >= 0 && limit > 0 && limit <= 100, ResponseEnum.VALIDATION_ERROR);
        ArrayNode result = json.array();
        for (Map<String, Object> row : support.list(entity, map(entity, request.getFilters()), offset, limit)) {
            result.add(json.tree(row));
        }
        return result;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public JsonNode upsert(LegacyCrudRequest request) {
        LegacyEntity entity = entity(request);
        Map<String, Object> row = map(entity, request.getRow());
        Object id = row.get(entity.primaryKey());
        if (id == null || id.toString().trim().isEmpty()) {
            row.put(entity.primaryKey(), CUuid.nextId());
        }
        support.upsert(entity, row);
        return json.tree(row);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public JsonNode delete(LegacyCrudRequest request) {
        LegacyEntity entity = entity(request);
        Map<String, Object> keys = keys(entity, request.getKeys());
        int affected = support.delete(entity, keys);
        ObjectNode result = json.object();
        result.put("affected", affected);
        return result;
    }

    private LegacyEntity entity(LegacyCrudRequest request) {
        json.require(request != null, ResponseEnum.VALIDATION_ERROR);
        LegacyEntity entity = LegacyEntity.parse(request.getEntity());
        json.require(entity != null, ResponseEnum.VALIDATION_ERROR);
        return entity;
    }

    private Map<String, Object> keys(LegacyEntity entity, JsonNode value) {
        Map<String, Object> keys = map(entity, value);
        Object id = keys.get(entity.primaryKey());
        json.require(id != null && !id.toString().trim().isEmpty(), ResponseEnum.VALIDATION_ERROR);
        Map<String, Object> onlyKey = Maps.newHashMap();
        onlyKey.put(entity.primaryKey(), id);
        return onlyKey;
    }

    private Map<String, Object> map(LegacyEntity entity, JsonNode value) {
        json.require(value != null && value.isObject(), ResponseEnum.VALIDATION_ERROR);
        Map<String, Object> result = Maps.newLinkedHashMap();
        Iterator<Map.Entry<String, JsonNode>> fields = value.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            json.require(entity.has(field.getKey()), ResponseEnum.VALIDATION_ERROR);
            if (!field.getValue().isNull()) {
                result.put(field.getKey(), scalar(field.getValue()));
            }
        }
        return result;
    }

    private Object scalar(JsonNode value) {
        if (value.isBoolean()) {
            return value.booleanValue() ? 1 : 0;
        }
        if (value.isInt()) {
            return value.intValue();
        }
        if (value.isLong() || value.isBigInteger()) {
            return value.longValue();
        }
        if (value.isNumber()) {
            return value.doubleValue();
        }
        return value.asText();
    }
}
