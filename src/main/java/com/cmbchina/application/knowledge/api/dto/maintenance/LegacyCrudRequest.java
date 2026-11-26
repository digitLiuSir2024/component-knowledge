package com.cmbchina.application.knowledge.api.dto.maintenance;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.Setter;

/** legacy 基础表读写请求；entity 必须是白名单枚举名或表名。 */
@Getter
@Setter
public class LegacyCrudRequest {
    private String entity;
    private JsonNode keys;
    private JsonNode filters;
    private JsonNode row;
    private Integer offset;
    private Integer limit;
}
