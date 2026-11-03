package com.cidas.edudoc.common.api;

import org.springframework.http.HttpStatus;

/** 全局错误码：与《详细设计说明书》§5.1 对齐，业务 code 与 HTTP 状态一一对应。 */
public enum ErrorCode {

    OK(200, HttpStatus.OK, "success"),

    BAD_REQUEST(40001, HttpStatus.BAD_REQUEST, "参数校验失败"),
    UNAUTHORIZED(40101, HttpStatus.UNAUTHORIZED, "未登录或令牌过期"),
    TOKEN_REVOKED(40102, HttpStatus.UNAUTHORIZED, "令牌已撤销"),
    FORBIDDEN(40301, HttpStatus.FORBIDDEN, "角色权限不足"),
    ACL_DENIED(40302, HttpStatus.FORBIDDEN, "文档 ACL 拒绝"),
    SHARE_LINK_EXPIRED(40303, HttpStatus.FORBIDDEN, "共享链接过期或撤销"),
    NOT_FOUND(40401, HttpStatus.NOT_FOUND, "资源不存在或不可见"),
    // 1103 审查 S1 整改新增：§5.1 字典未含 405 码位，框架异常塌缩为 500 不可接受，按同构规则补位
    METHOD_NOT_ALLOWED(40501, HttpStatus.METHOD_NOT_ALLOWED, "请求方法不允许"),
    CONFLICT(40901, HttpStatus.CONFLICT, "版本或并发冲突"),
    DUPLICATE(40902, HttpStatus.CONFLICT, "重复提交或幂等键冲突"),
    FILE_TOO_LARGE(41301, HttpStatus.PAYLOAD_TOO_LARGE, "文件超过 20MB"),
    UNSUPPORTED_MEDIA_TYPE(41501, HttpStatus.UNSUPPORTED_MEDIA_TYPE, "文件类型不支持"),
    PARSE_FAILED(42201, HttpStatus.UNPROCESSABLE_ENTITY, "文档结构无法解析"),
    FORMAT_BLOCKER(42202, HttpStatus.UNPROCESSABLE_ENTITY, "格式校验存在阻断问题"),
    RATE_LIMITED(42901, HttpStatus.TOO_MANY_REQUESTS, "请求或任务并发超限"),
    INTERNAL_ERROR(50001, HttpStatus.INTERNAL_SERVER_ERROR, "服务器内部错误"),
    MODEL_FAILED(50201, HttpStatus.BAD_GATEWAY, "模型服务失败"),
    SERVICE_UNAVAILABLE(50301, HttpStatus.SERVICE_UNAVAILABLE, "OSS 或数据库暂时不可用"),
    TIMEOUT(50401, HttpStatus.GATEWAY_TIMEOUT, "处理超时");

    private final int code;
    private final HttpStatus httpStatus;
    private final String defaultMessage;

    ErrorCode(int code, HttpStatus httpStatus, String defaultMessage) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public int code() {
        return code;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
