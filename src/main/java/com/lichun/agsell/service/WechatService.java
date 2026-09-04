package com.lichun.agsell.service;

/**
 * 微信小程序服务
 * 封装微信接口调用，核心功能：code2session（code换openid）
 */
public interface WechatService {

    /**
     * 用登录 code 换取 openid 和 session_key
     *
     * @param code 小程序端 wx.login() 获取的临时登录凭证
     * @return openid 用户唯一标识
     */
    String code2Session(String code);
}
