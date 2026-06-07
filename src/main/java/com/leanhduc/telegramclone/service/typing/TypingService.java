package com.leanhduc.telegramclone.service.typing;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TypingService implements ITypingService {

    private final RedisTemplate<String, String> redisTemplate;

    private static final String KEY_PREFIX = "conversation:typing:";
    private static final Duration TTL = Duration.ofSeconds(6);

    @Override
    public void setTyping(UUID conversationId, UUID userId, boolean isTyping) {
        String key = KEY_PREFIX + conversationId + ":" + userId;
        if (isTyping) {
            redisTemplate.opsForValue().set(key, "1", TTL);
        } else {
            redisTemplate.delete(key);
        }
    }

    @Override
    public Set<UUID> getTypingUsers(UUID conversationId) {
        String pattern = KEY_PREFIX + conversationId + ":*";
        Set<String> keys = redisTemplate.keys(pattern);
        if (keys == null || keys.isEmpty()) {
            return Set.of();
        }
        return keys.stream()
                .map(key -> {
                    String[] parts = key.split(":");
                    return UUID.fromString(parts[parts.length - 1]);
                })
                .collect(Collectors.toSet());
    }

    @Override
    public void clearTypingForUser(UUID userId) {
        String pattern = "conversation:typing:*:" + userId;
        Set<String> keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }
}
