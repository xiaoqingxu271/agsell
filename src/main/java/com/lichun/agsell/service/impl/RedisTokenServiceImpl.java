package com.lichun.agsell.service.impl;

import com.lichun.agsell.service.RedisTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisTokenServiceImpl implements RedisTokenService {

    private final StringRedisTemplate redisTemplate;

    /** Redis key 前缀：用户 token */
    private static final String USER_TOKEN_PREFIX = "token:user:";

    /** Redis key 前缀：管理员 token */
    private static final String ADMIN_TOKEN_PREFIX = "token:admin:";

    @Override
    public void saveUserToken(Long userId, String token, String jti, long expireSeconds) {
        String key = USER_TOKEN_PREFIX + userId + ":" + jti;
        redisTemplate.opsForValue().set(key, token, expireSeconds, TimeUnit.SECONDS);
        log.info("[RedisToken] 保存用户token, userId={}, jti={}, expire={}s", userId, jti, expireSeconds);
    }

    @Override
    public boolean validateUserToken(Long userId, String token, String jti) {
        String key = USER_TOKEN_PREFIX + userId + ":" + jti;
        String storedToken = redisTemplate.opsForValue().get(key);
        boolean valid = token.equals(storedToken);
        if (!valid) {
            log.warn("[RedisToken] 用户token验证失败, userId={}, jti={}", userId, jti);
        }
        return valid;
    }

    @Override
    public void deleteUserToken(Long userId, String jti) {
        String key = USER_TOKEN_PREFIX + userId + ":" + jti;
        Boolean deleted = redisTemplate.delete(key);
        log.info("[RedisToken] 删除用户token, userId={}, jti={}, deleted={}", userId, jti, deleted);
    }

    @Override
    public void deleteUserTokens(Long userId) {
        String pattern = USER_TOKEN_PREFIX + userId + ":*";
        Set<String> keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
            log.info("[RedisToken] 删除用户全部token, userId={}, count={}", userId, keys.size());
        } else {
            log.info("[RedisToken] 删除用户全部token, userId={}, 无有效token", userId);
        }
    }

    @Override
    public void saveAdminToken(Long adminId, String token, String jti, long expireSeconds) {
        String key = ADMIN_TOKEN_PREFIX + adminId + ":" + jti;
        redisTemplate.opsForValue().set(key, token, expireSeconds, TimeUnit.SECONDS);
        log.info("[RedisToken] 保存管理员token, adminId={}, jti={}, expire={}s", adminId, jti, expireSeconds);
    }

    @Override
    public boolean validateAdminToken(Long adminId, String token, String jti) {
        String key = ADMIN_TOKEN_PREFIX + adminId + ":" + jti;
        String storedToken = redisTemplate.opsForValue().get(key);
        boolean valid = token.equals(storedToken);
        if (!valid) {
            log.warn("[RedisToken] 管理员token验证失败, adminId={}, jti={}", adminId, jti);
        }
        return valid;
    }

    @Override
    public void deleteAdminToken(Long adminId, String jti) {
        String key = ADMIN_TOKEN_PREFIX + adminId + ":" + jti;
        Boolean deleted = redisTemplate.delete(key);
        log.info("[RedisToken] 删除管理员token, adminId={}, jti={}, deleted={}", adminId, jti, deleted);
    }
}
