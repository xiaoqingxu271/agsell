package com.lichun.agsell.service;

/**
 * Redis Token 管理服务
 * 负责 token 的存取和删除，实现 JWT 的可控撤销
 */
public interface RedisTokenService {

    /**
     * 保存用户 token 到 Redis
     *
     * @param userId 用户ID
     * @param token  JWT token
     * @param jti    JWT ID（用于唯一标识）
     * @param expireSeconds 过期时间（秒）
     */
    void saveUserToken(Long userId, String token, String jti, long expireSeconds);

    /**
     * 验证用户 token 是否存在于 Redis（同时检查是否与 Redis 中存储的一致）
     *
     * @param userId 用户ID
     * @param token  JWT token
     * @param jti    JWT ID
     * @return 是否存在且匹配
     */
    boolean validateUserToken(Long userId, String token, String jti);

    /**
     * 删除用户 token（退出登录时调用）
     *
     * @param userId 用户ID
     * @param jti    JWT ID
     */
    void deleteUserToken(Long userId, String jti);

    /**
     * 保存管理员 token 到 Redis
     *
     * @param adminId     管理员ID
     * @param token       JWT token
     * @param jti         JWT ID
     * @param expireSeconds 过期时间（秒）
     */
    void saveAdminToken(Long adminId, String token, String jti, long expireSeconds);

    /**
     * 验证管理员 token
     *
     * @param adminId 管理员ID
     * @param token   JWT token
     * @param jti     JWT ID
     * @return 是否存在且匹配
     */
    boolean validateAdminToken(Long adminId, String token, String jti);

    /**
     * 删除管理员 token
     *
     * @param adminId 管理员ID
     * @param jti     JWT ID
     */
    void deleteAdminToken(Long adminId, String jti);
}
