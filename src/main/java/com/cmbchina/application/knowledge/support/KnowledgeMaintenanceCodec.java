package com.cmbchina.application.knowledge.support;

import com.cmbchina.infrastructure.common.ResponseEnum;
import com.cmbchina.infrastructure.common.exception.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** 维护载荷的JSON边界，序列化统一使用托管Mapper。 */
@Component
public class KnowledgeMaintenanceCodec {
    private static final int PAYLOAD_TEXT_LIMIT = 60000;
    @Autowired
    private ObjectMapper mapper;

    /** 读取已保存的结构载荷，不回退模拟内容。 */
    public JsonNode parse(String value) {
        try {
            return mapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ResponseEnum.KNOWLEDGE_OBJECT_INVALID, exception);
        }
    }

    /** 转换独立边界数据。 */
    public JsonNode tree(Object value) {
        return mapper.valueToTree(value);
    }

    /** 创建对象载荷。 */
    public ObjectNode object() {
        return mapper.createObjectNode();
    }

    /** 创建数组载荷。 */
    public ArrayNode array() {
        return mapper.createArrayNode();
    }

    /** 大草稿压缩后仍以合法JSON文本保存，避免TEXT截断。 */
    public String pack(JsonNode value) {
        byte[] bytes = value.toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= PAYLOAD_TEXT_LIMIT) {
            return value.toString();
        }
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(output)) {
                gzip.write(bytes);
            }
            ObjectNode envelope = object();
            envelope.put("_knowledgePayloadEncoding", "gzip-base64-v1");
            envelope.put("data", Base64.getEncoder().encodeToString(output.toByteArray()));
            String result = envelope.toString();
            require(result.length() <= PAYLOAD_TEXT_LIMIT, ResponseEnum.KNOWLEDGE_OBJECT_INVALID);
            return result;
        } catch (IOException exception) {
            throw new BusinessException(ResponseEnum.KNOWLEDGE_OBJECT_INVALID, exception);
        }
    }

    /** 只解码内部草稿，不改变公开接口的对象载荷。 */
    public JsonNode unpack(String value) {
        JsonNode envelope = parse(value);
        if (!"gzip-base64-v1".equals(envelope.path("_knowledgePayloadEncoding").asText())) {
            return envelope;
        }
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(envelope.path("data").asText())))) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = gzip.read(buffer)) >= 0) {
                output.write(buffer, 0, count);
            }
            return parse(new String(output.toByteArray(), StandardCharsets.UTF_8));
        } catch (IOException | IllegalArgumentException exception) {
            throw new BusinessException(ResponseEnum.KNOWLEDGE_OBJECT_INVALID, exception);
        }
    }

    /** 按明确类型解析请求内容。 */
    public <T> T convert(JsonNode value, Class<T> type) {
        return mapper.convertValue(value, type);
    }

    /** 对原始依据生成可比较摘要。 */
    public String digest(JsonNode value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte item : bytes) {
                result.append(String.format("%02x", item & 255));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new BusinessException(ResponseEnum.UNKNOWN_ERROR, exception);
        }
    }

    /** 缺少明确身份或字段时拒绝。 */
    public void require(boolean valid, ResponseEnum error) {
        if (!valid) {
            throw new BusinessException(error);
        }
    }

    /** 非空白文本校验。 */
    public boolean text(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
