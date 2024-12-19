package io.hhplus.tdd.point;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
        Long userId1 = 2L;
        Long userId2 = 3L;

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
        Long userId = 4L;
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
        Long userId = 5L;
        ReentrantLock lock = lockManager.getLock(userId);

        // when & then
        assertTrue(lock.tryLock());  // 락 획득 성공
        lock.unlock();
        assertTrue(lock.tryLock());  // 다시 락 획득 가능
        lock.unlock();
    }

    @Test
    @DisplayName("요청 순서대로(도달 순서) 락을 획득한다")
    void verifyFairLockOrder() throws InterruptedException {
        // given
        Long userId = 6L;
        ReentrantLock lock = lockManager.getLock(userId);
        assertTrue(lock.isFair(), "락 공정성 설정X");

        // 첫 번째 스레드가 락을 획득
        lock.lock();

        // 락 획득 순서를 기록할 리스트
        List<String> lockOrder = Collections.synchronizedList(new ArrayList<>());

        // 두 번째, 세 번째 스레드 생성
        Thread thread2 = new Thread(() -> {
            lock.lock();
            try {
                lockOrder.add("Thread2"); // 락을 획득한 순서를 기록
            } finally {
                lock.unlock();
            }
        });

        Thread thread3 = new Thread(() -> {
            lock.lock();
            try {
                lockOrder.add("Thread3");
            } finally {
                lock.unlock();
            }
        });

        // when
        thread2.start();
        Thread.sleep(100); // thread2가 먼저 대기열에 들어가도록
        thread3.start();
        Thread.sleep(100); // thread3가 대기열에 들어가도록

        // 락 해제
        lock.unlock();
        Thread.sleep(200); // thread2가 락을 획득하고 실행할 시간

        // then
        assertEquals(List.of("Thread2", "Thread3"), lockOrder,
                "락 획득 순서가 요청 순서와 다릅니다.");
    }

}