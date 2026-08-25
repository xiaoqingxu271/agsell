package com.lichun.agsell.service;

/**
 * 短信验证码服务
 */
public interface SmsCodeService {

    /**
     * 发送验证码到手机号（生成6位随机码，存入Redis，5分钟过期）
     *
     * @param phone 手机号
     */
    void sendCode(String phone);

    /**
     * 校验验证码，校验成功后删除 Redis 中的验证码（一次性使用）
     *
     * @param phone 手机号
     * @param code  用户输入的验证码
     * @return 是否校验成功
     */
    boolean verifyCode(String phone, String code);
}
