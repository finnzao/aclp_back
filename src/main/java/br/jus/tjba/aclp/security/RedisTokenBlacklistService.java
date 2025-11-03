package br.jus.tjba.aclp.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class RedisTokenBlacklistService implements TokenBlacklistService {

    private static final Logger logger = LoggerFactory.getLogger(RedisTokenBlacklistService.class);
    private static final String BLACKLIST_PREFIX = "jwt:blacklist:";

    private final RedisTemplate<String, String> redisTemplate;

    public RedisTokenBlacklistService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void blacklist(String token, long expirationMillis) {
        try {
            String key = BLACKLIST_PREFIX + token;
            long ttl = Math.max(expirationMillis - System.currentTimeMillis(), 0);
            if (ttl > 0) {
                redisTemplate.opsForValue().set(key, "revoked", ttl, TimeUnit.MILLISECONDS);
            }
        } catch (Exception e) {
            logger.error("Falha ao adicionar token à blacklist no Redis", e);
        }
    }

    @Override
    public boolean isBlacklisted(String token) {
        try {
            String key = BLACKLIST_PREFIX + token;
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (Exception e) {
            logger.error("Falha ao consultar blacklist no Redis", e);
            return false;
        }
    }
}