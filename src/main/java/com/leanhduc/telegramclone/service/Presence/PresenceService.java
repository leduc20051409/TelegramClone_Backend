package com.leanhduc.telegramclone.service.Presence;

import com.leanhduc.telegramclone.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PresenceService implements IPresenceService {
    private final RedisTemplate<String, String> redisTemplate;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    private static final String KEY_PREFIX = "user:connections:";
    private static final Duration TTL = Duration.ofSeconds(60);


    @Override
    public void connect(UUID userId, String sessionId) {
        String key = KEY_PREFIX + userId;
        Long sizeBefore = redisTemplate.opsForSet().size(key);
        redisTemplate.opsForSet().add(key, sessionId);
        redisTemplate.expire(key, TTL);

        if (sizeBefore == null || sizeBefore == 0) {
            eventPublisher.publishEvent(new UserPresenceChangedEvent(this, userId, true, Instant.now()));
        }
    }

    @Override
    public void heartbeat(UUID userId) {
        redisTemplate.expire(KEY_PREFIX + userId, TTL);
    }

    @Override
    public void disconnect(UUID userId, String sessionId) {
        String key = KEY_PREFIX + userId;
        redisTemplate.opsForSet().remove(key, sessionId);

        Long size = redisTemplate.opsForSet().size(key);
        if (size == null || size == 0) {
            Instant lastSeen = Instant.now();
            userRepository.updateLastSeen(userId, lastSeen, Instant.now().minusSeconds(30));
            eventPublisher.publishEvent(new UserPresenceChangedEvent(this, userId, false, lastSeen));
        }

    }

    @Override
    public boolean isUserOnline(UUID userId) {
        String key = KEY_PREFIX + userId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }
}
