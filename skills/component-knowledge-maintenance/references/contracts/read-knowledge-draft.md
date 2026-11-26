# read_knowledge_draft
通用字段、content 类型、Change、Draft 与错误码见 [common-schema.md](common-schema.md)。

后端 POST `/api/v1/knowledge/maintenance/drafts/read`。

## 请求

必填非空字符串：systemCode（系统编码）、projectCode（项目编码），另需非空字符串 draftId（草稿 ID）。

首次登记草稿回读时，核对 COMPONENT 的 CREATE 已转为 UPDATE，并核对适用关系与联动目标的临时键已替换为数据库 ID。交互的 `content.componentId` 不参与映射，应核对其归属为正式组件 ID。

## 响应

成功时 MCP 返回 `isError: false`，`content[0].text` 是后端 Result.data 的 JSON 序列化文本。失败时 `isError: true` 且 text 为错误文字，无成功数据。未声明的参数、缺失必填参数、配置和连接失败由桥接层报错。后端失败时 text 含业务码（参数校验 10000，未知异常 99999）。

返回 Draft 及当前修订，按默认选择生成预览。

## 请求示例

```json
{
  "systemCode": "S",
  "projectCode": "P",
  "draftId": "D1"
}
```

Draft：id:string、systemCode:string、projectCode:string、componentId:string|null（数据库 ID）、origin:SESSION/MANUAL/RESTORE/MIGRATION、status:DRAFT/PUBLISHED/DISCARDED、revision:integer、baseVersionId:string|null、changes:array<Change>、selectedChangeIds:array<string>、before:object、after:object、beforeObjects:array、afterObjects:array、independent:boolean、issues:array（当前返回 []）、reviewUrl:string、publishResult:object|null。before/after 为组装的知识上下文，beforeObjects/afterObjects 为用于预览的对象数组。另返回 identity（后端保存的请求对象）以及 requestId、createdAt、updatedAt；payload、objectBases、selectedChanges、publishedResult、requestDigest 等内部字段不在响应中返回。草稿仅保存不切换详情；有问题的操作不得发布，PENDING 内容不得自动变为 CONFIRMED。

PublishResult：draftId:string、baselineVersionId:string|null、componentVersionId:string|null、objectVersionIds:object<string,string>。
未登记组件首次提交的 COMPONENT CREATE 在持久化草稿中转为 UPDATE，objectId 绑定到 Draft.componentId。指向该组件的 APPLICABILITY.content.targetId（targetType 为 COMPONENT）和 COMPONENT_RELATION.content.targetComponentId 同样绑定到该 ID。使用 identity 与 beforeObjects 核对映射，再按 changeId 比较完整候选。

Change：changeId:string（草稿内唯一）、operation:CREATE/UPDATE/LINK/UNLINK/UPGRADE_REFERENCE/EXPIRE、objectType:string、objectId:string（CREATE 必须使用 `new:` 前缀草稿临时键）、baseObjectVersionId:string|null、targetVersionId:string|null、content:object|null、confirmationStatus:CONFIRMED/PENDING|null、evidence:array、atomicGroupId:string|null。字段均传入；LINK/UPGRADE_REFERENCE 用 targetVersionId，UPDATE/CREATE 用 content。UNLINK 只移除当前组件引用；EXPIRE 生成停用对象版本，不物理删除。

Evidence：kind:string（FACT/REQUIREMENT/INFERENCE）、source:string（文件/会话说明定位）、excerpt:string；三项必填非空。只提交必要依据，不上传整个会话。


## 响应数据示例（必要时节选）

```json
{"id": "D1", "origin": "SESSION", "status": "DRAFT", "revision": 1, "selectedChangeIds": ["c1"], "reviewUrl": "/component-detail?knowledgeDraft=D1&knowledgeComponent=MetricsStrip&knowledgeReview=1", "publishResult": null}
```

## 业务错误码

完整错误码表见 [common-schema.md](common-schema.md#业务错误码)。
