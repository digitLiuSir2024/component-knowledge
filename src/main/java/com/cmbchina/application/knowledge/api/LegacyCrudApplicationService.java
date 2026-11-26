package com.cmbchina.application.knowledge.api;

import com.cmbchina.application.knowledge.api.dto.maintenance.LegacyCrudRequest;
import com.fasterxml.jackson.databind.JsonNode;

/** legacy 基础表运行时读写应用边界。 */
public interface LegacyCrudApplicationService {
    JsonNode get(LegacyCrudRequest request);

    JsonNode list(LegacyCrudRequest request);

    JsonNode upsert(LegacyCrudRequest request);

    JsonNode delete(LegacyCrudRequest request);
}
