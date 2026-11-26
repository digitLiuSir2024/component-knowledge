package com.cmbchina.adapter.knowledge.web;

import com.cmbchina.application.api.dto.Result;
import com.cmbchina.application.knowledge.api.LegacyCrudApplicationService;
import com.cmbchina.application.knowledge.api.dto.maintenance.LegacyCrudRequest;
import com.fasterxml.jackson.databind.JsonNode;
import javax.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** legacy 基础表运行时读写入口，表名由服务端白名单约束。 */
@RestController
@RequestMapping("/api/v1/knowledge/maintenance/legacy")
public class LegacyCrudController {
    @Autowired
    private LegacyCrudApplicationService service;

    @PostMapping("/get")
    public Result<JsonNode> get(@Valid @RequestBody LegacyCrudRequest request) {
        return Result.success(service.get(request));
    }

    @PostMapping("/list")
    public Result<JsonNode> list(@Valid @RequestBody LegacyCrudRequest request) {
        return Result.success(service.list(request));
    }

    @PostMapping("/upsert")
    public Result<JsonNode> upsert(@Valid @RequestBody LegacyCrudRequest request) {
        return Result.success(service.upsert(request));
    }

    @PostMapping("/delete")
    public Result<JsonNode> delete(@Valid @RequestBody LegacyCrudRequest request) {
        return Result.success(service.delete(request));
    }
}
