package com.julius.clipper.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;

@Service
public class DistributedLockManager {

    private static final Logger log = LoggerFactory.getLogger(DistributedLockManager.class);

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<Long> unlockScript;

    public DistributedLockManager(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        String script = "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                        "return redis.call('del', KEYS[1]) " +
                        "else " +
                        "return 0 " +
                        "end";
        this.unlockScript = new DefaultRedisScript<>(script, Long.class);
    }

    public boolean acquireLock(String lockKey, String ownerId, long ttlSeconds) {
        try {
            Boolean success = redisTemplate.opsForValue().setIfAbsent(lockKey, ownerId, Duration.ofSeconds(ttlSeconds));
            boolean acquired = Boolean.TRUE.equals(success);
            if (acquired) {
                log.debug("Acquired distributed lock: Key={}, Owner={}", lockKey, ownerId);
            }
            return acquired;
        } catch (Exception e) {
            log.error("Failed to acquire distributed lock for key={}: {}", lockKey, e.getMessage(), e);
            return false;
        }
    }

    public boolean releaseLock(String lockKey, String ownerId) {
        try {
            Long result = redisTemplate.execute(unlockScript, Collections.singletonList(lockKey), ownerId);
            boolean released = result != null && result > 0;
            if (released) {
                log.debug("Released distributed lock: Key={}, Owner={}", lockKey, ownerId);
            } else {
                log.debug("Failed to release distributed lock (owner mismatch or expired): Key={}, Owner={}", lockKey, ownerId);
            }
            return released;
        } catch (Exception e) {
            log.error("Error executing distributed unlock for key={}: {}", lockKey, e.getMessage(), e);
            return false;
        }
    }
}
