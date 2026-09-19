package com.lichun.agsell.annotation;

import java.lang.annotation.*;

/**
 * 操作日志注解
 * 标注在管理端写操作接口上，由 OperationLogAspect 在方法成功后异步记录审计日志
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OperationLog {

    /** 操作模块，如：管理员管理 / 系统配置 / 商品管理 / 订单管理 */
    String module();

    /** 操作动作，如：新增 / 编辑 / 删除 / 重置密码 / 发货 */
    String action();

    /**
     * 操作内容模板（中文可读），支持 SpEL 引用方法参数，如：
     * "新增了「#request.couponName」优惠券"
     * 留空则降级为拼接方法参数（兼容旧接口）。
     */
    String content() default "";
}
