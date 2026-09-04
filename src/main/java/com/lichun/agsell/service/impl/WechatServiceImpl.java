package com.lichun.agsell.service.impl;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONUtil;
import com.lichun.agsell.exception.BusinessException;
import com.lichun.agsell.exception.ErrorCode;
import com.lichun.agsell.service.WechatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 微信小程序服务实现
 * 调用微信 jscode2session 接口换取 openid
 */
@Slf4j
@Service
public class WechatServiceImpl implements WechatService {

    @Value("${wechat.miniapp.appid}")
    private String appid;

    @Value("${wechat.miniapp.secret}")
    private String secret;

    @Value("${wechat.miniapp.code2session_url}")
    private String code2SessionUrl;

    /**
     * 用登录 code 换取 openid
     *
     * @param code 小程序端 wx.login() 获取的临时登录凭证
     * @return openid 用户唯一标识
     * @throws BusinessException 如果 code 无效或微信接口返回错误
     */
    @Override
    public String code2Session(String code) {
        String url = code2SessionUrl
                + "?appid=" + appid
                + "&secret=" + secret
                + "&js_code=" + code
                + "&grant_type=authorization_code";

        try (HttpResponse response = HttpRequest.get(url)
                .timeout(5000)
                .execute()) {
            String body = response.body();
            log.info("微信 jscode2session 响应: {}", body);

            if (!JSONUtil.isJson(body)) {
                log.error("微信接口返回非JSON响应: {}", body);
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "微信接口响应异常");
            }

            // 微信接口成功时不返回 errcode 字段，仅错误时才返回
            // 使用 getInt("errcode") 而非带默认值，成功时为 null
            Integer errcode = JSONUtil.parseObj(body).getInt("errcode");
            String errmsg = JSONUtil.parseObj(body).getStr("errmsg", "");

            if (errcode != null && errcode != 0) {
                log.error("微信接口错误: errcode={}, errmsg={}", errcode, errmsg);
                switch (errcode) {
                    case 40029:
                        throw new BusinessException(ErrorCode.PARAMS_ERROR, "无效的code，请重新登录");
                    case 45011:
                        throw new BusinessException(ErrorCode.SYSTEM_ERROR, "API请求频率限制，请稍后重试");
                    default:
                        throw new BusinessException(ErrorCode.SYSTEM_ERROR, "微信登录失败: " + errmsg);
                }
            }

            return JSONUtil.parseObj(body).getStr("openid");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用微信接口异常", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "微信登录服务异常，请稍后重试");
        }
    }
}
