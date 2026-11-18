package com.cmbchina.infrastructure.common;

/** 统一错误码与默认文案。 */
public enum ResponseEnum {
    SUCCESS(200, "成功"),
    VALIDATION_ERROR(10000, "参数校验失败"),
    BUSINESS_ERROR(10001, "业务处理失败"),
    NOT_FOUND(10002, "请求资源不存在"),
    METHOD_NOT_ALLOWED(10003, "不支持的请求方法"),
    MEDIA_TYPE_NOT_SUPPORTED(10004, "不支持的请求内容类型"),
    UPLOAD_TOO_LARGE(10005, "上传文件超过大小限制"),
    KNOWLEDGE_NOT_FOUND(10100, "知识对象、草稿或版本不存在"),
    KNOWLEDGE_SCOPE_MISMATCH(10101, "知识对象不属于当前范围"),
    KNOWLEDGE_REVISION_CONFLICT(10102, "知识草稿已变化，请重新读取"),
    KNOWLEDGE_BASE_CONFLICT(10103, "知识基准版本已变化，请重新读取后确认"),
    KNOWLEDGE_REFERENCE_INVALID(10104, "知识引用版本不合法"),
    KNOWLEDGE_DEPENDENCY_INVALID(10105, "知识变更的关联选择不完整"),
    KNOWLEDGE_NO_CHANGE(10106, "没有可发布的实质变更"),
    KNOWLEDGE_DRAFT_CLOSED(10107, "知识草稿已关闭"),
    KNOWLEDGE_PENDING_DRAFTS(10111, "请先处理现有待确认更新"),
    KNOWLEDGE_REQUEST_CONFLICT(10108, "请求编号已用于其他知识内容"),
    KNOWLEDGE_OBJECT_INVALID(10109, "知识对象结构或状态不合法"),
    KNOWLEDGE_DISABLED(10110, "知识维护尚未启用，请联系项目维护人员"),
    UNKNOWN_ERROR(99999, "处理失败，请稍后重试");

    private final int code;
    private final String message;

    /** 保存错误码与可对外展示的默认文案。 */
    ResponseEnum(int code, String message) {
        this.code = code;
        this.message = message;
    }

    /** 返回错误码。 */
    public int getCode() {
        return code;
    }

    /** 返回默认文案。 */
    public String getMessage() {
        return message;
    }
}
