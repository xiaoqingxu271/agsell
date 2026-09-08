package com.lichun.agsell.exception;

import lombok.Getter;

/**
 * 全局异常统一响应工具类
 */
@Getter
public enum ErrorCode {

    SUCCESS(0, "ok"),
    PARAMS_ERROR(40000, "请求参数错误"),
    NOT_LOGIN_ERROR(40100, "未登录"),
    NO_AUTH_ERROR(40101, "无权限"),
    NOT_FOUND_ERROR(40400, "请求数据不存在"),
    FORBIDDEN_ERROR(40300, "禁止访问"),
    SYSTEM_ERROR(50000, "系统内部异常"),
    OPERATION_ERROR(50001, "操作失败"),
    // 管理员相关
    ADMIN_NOT_LOGIN_ERROR(40200, "管理员未登录"),
    ADMIN_NO_AUTH_ERROR(40201, "管理员无权限"),
    // 用户相关
    PASSWORD_ERROR(40301, "密码错误"),
    USER_ALREADY_EXISTS(40401, "用户名已存在"),
    PHONE_ALREADY_EXISTS(40402, "手机号已注册"),
    // 验证码相关
    SMS_CODE_INVALID(40001, "验证码错误"),
    SMS_CODE_EXPIRED(40002, "验证码已过期"),
    SMS_CODE_SEND_TOO_FAST(40003, "发送过于频繁，请稍后再试"),
    // 购物车/订单相关
    STOCK_INSUFFICIENT(50002, "库存不足"),
    ORDER_STATUS_ERROR(50003, "订单状态异常"),
    // 售后相关
    AFTER_SALES_STATUS_ERROR(50004, "售后单状态异常"),
    // 文件上传相关
    FILE_UPLOAD_FAILED(50100, "文件上传失败"),
    FILE_TYPE_NOT_ALLOWED(50101, "不支持的文件类型"),
    FILE_SIZE_EXCEEDED(50102, "文件大小超出限制"),
    FILE_NAME_INVALID(50103, "文件名不合法"),
    OSS_CLIENT_ERROR(50104, "OSS 服务异常");

    /**
     * 状态码
     */
    private final int code;

    /**
     * 信息
     */
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

}
