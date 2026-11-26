package com.cmbchina.adapter.knowledge.web;
import com.cmbchina.application.api.dto.Result;
import com.cmbchina.application.knowledge.api.KnowledgeMaintenanceApplicationService;
import com.cmbchina.application.knowledge.api.dto.maintenance.MaintenanceRequest;
import com.fasterxml.jackson.databind.JsonNode;
import javax.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
/** 页面维护和会话草稿入口，发布能力只由页面流程调用。 */
@RestController
@RequestMapping("/api/v1/knowledge/maintenance")
public class KnowledgeMaintenanceController {
    @Autowired
    private KnowledgeMaintenanceApplicationService service;
    /** readContext知识维护操作。 */
    @PostMapping("/context")
    public Result<JsonNode> readContext(@Valid @RequestBody MaintenanceRequest request) {
        return Result.success(service.readContext(request));
    }

    /** searchObjects知识维护操作。 */
    @PostMapping("/objects/search")
    public Result<JsonNode> searchObjects(@Valid @RequestBody MaintenanceRequest request) {
        return Result.success(service.searchObjects(request));
    }

    /** readObjectVersion知识维护操作。 */
    @PostMapping("/objects/version")
    public Result<JsonNode> readObjectVersion(@Valid @RequestBody MaintenanceRequest request) {
        return Result.success(service.readObjectVersion(request));
    }

    /** readObjectHistory知识维护操作。 */
    @PostMapping("/objects/history")
    public Result<JsonNode> readObjectHistory(@Valid @RequestBody MaintenanceRequest request) {
        return Result.success(service.readObjectHistory(request));
    }

    /** 读取当前或指定版本的交互图。 */
    @PostMapping("/interactions/graph")
    public Result<JsonNode> interactionGraph(@Valid @RequestBody MaintenanceRequest request) {
        return Result.success(service.readInteractionGraph(request));
    }

    /** 分页发现组件待确认更新。 */
    @PostMapping("/drafts/list")
    public Result<JsonNode> listDrafts(@Valid @RequestBody MaintenanceRequest request) {
        return Result.success(service.listDrafts(request));
    }

    /** createDraft知识维护操作。 */
    @PostMapping("/drafts/create")
    public Result<JsonNode> createDraft(@Valid @RequestBody MaintenanceRequest request) {
        return Result.success(service.createDraft(request));
    }

    /** updateDraft知识维护操作。 */
    @PostMapping("/drafts/update")
    public Result<JsonNode> updateDraft(@Valid @RequestBody MaintenanceRequest request) {
        return Result.success(service.updateDraft(request));
    }

    /** readDraft知识维护操作。 */
    @PostMapping("/drafts/read")
    public Result<JsonNode> readDraft(@Valid @RequestBody MaintenanceRequest request) {
        return Result.success(service.readDraft(request));
    }

    /** discardDraft知识维护操作。 */
    @PostMapping("/drafts/discard")
    public Result<JsonNode> discardDraft(@Valid @RequestBody MaintenanceRequest request) {
        return Result.success(service.discardDraft(request));
    }

    /** publishDraft知识维护操作。 */
    @PostMapping("/drafts/publish")
    public Result<JsonNode> publishDraft(@Valid @RequestBody MaintenanceRequest request) {
        return Result.success(service.publishDraft(request));
    }

    /** listComponentVersions知识维护操作。 */
    @PostMapping("/versions")
    public Result<JsonNode> listComponentVersions(@Valid @RequestBody MaintenanceRequest request) {
        return Result.success(service.listComponentVersions(request));
    }

    /** createRestoreDraft知识维护操作。 */
    @PostMapping("/restore")
    public Result<JsonNode> createRestoreDraft(@Valid @RequestBody MaintenanceRequest request) {
        return Result.success(service.createRestoreDraft(request));
    }

    /** 永久清理单个组件的知识。 */
    @PostMapping("/components/purge")
    public Result<Boolean> purgeComponent(@Valid @RequestBody MaintenanceRequest request) {
        service.purgeComponent(request);
        return Result.success(true);
    }

    /** createBaselineDraft知识维护操作。 */
    @PostMapping("/baseline")
    public Result<JsonNode> createBaselineDraft(@Valid @RequestBody MaintenanceRequest request) {
        return Result.success(service.createBaselineDraft(request));
    }
}
