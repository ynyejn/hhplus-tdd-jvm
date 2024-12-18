package io.hhplus.tdd.point;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.*;

class UserLockManagerTest {
    private final UserLockManager lockManager = new UserLockManager();

    @Test
    @DisplayName("동일한 사용자 ID로 항상 같은 락 객체를 반환한다")
    void returnSameLockForSameUser() {
        // given
        Long userId = 1L;

        // when
        ReentrantLock lock1 = lockManager.getLock(userId);
        ReentrantLock lock2 = lockManager.getLock(userId);

        // then
        assertSame(lock1, lock2);
    }

    @Test
    @DisplayName("서로 다른 사용자 ID는 서로 다른 락 객체를 반환한다")
    void returnDifferentLockForDifferentUsers() {
        // given
        Long userId1 = 1L;
        Long userId2 = 2L;

        // when
        ReentrantLock lock1 = lockManager.getLock(userId1);
        ReentrantLock lock2 = lockManager.getLock(userId2);

        // then
        assertNotSame(lock1, lock2);
    }

    @Test
    @DisplayName("동시에 여러 스레드에서 같은 사용자의 락을 요청해도 같은 락 객체를 반환한다")
    void returnSameLockForConcurrentRequests() throws InterruptedException {
        // given
        Long userId = 1L;
        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        ReentrantLock[] locks = new ReentrantLock[threadCount];

        // when
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    locks[index] = lockManager.getLock(userId);
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        endLatch.await();

        // then
        ReentrantLock firstLock = locks[0];
        for (int i = 1; i < threadCount; i++) {
            assertSame(firstLock, locks[i]);
        }

        executorService.shutdown();
    }

    @Test
    @DisplayName("락 획득과 해제가 정상적으로 동작한다")
    void lockAndUnlockWork() {
        // given
        Long userId = 1L;
        ReentrantLock lock = lockManager.getLock(userId);

        // when & then
        assertTrue(lock.tryLock());  // 락 획득 성공
        lock.unlock();
        assertTrue(lock.tryLock());  // 다시 락 획득 가능
        lock.unlock();
    }

}