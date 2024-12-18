package io.hhplus.tdd.point;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Component
public class UserLockManager {
    private final ConcurrentHashMap<Long, ReentrantLock> userLocks = new ConcurrentHashMap<>();

    public ReentrantLock getLock(Long userId) {
        // computeIfAbsent는 원자적 연산으로, 키가 없을 경우에만 새 lock을 생성
        return userLocks.computeIfAbsent(userId, k -> new ReentrantLock());
    }
}