package io.hhplus.tdd.point;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.*;
import java.util.concurrent.*;

import static io.hhplus.tdd.point.TransactionType.CHARGE;
import static io.hhplus.tdd.point.TransactionType.USE;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class PointServiceItTest {
    @Autowired
    private PointService pointService;

    @Nested
    @DisplayName("포인트 충전 통합 테스트")
    class ChargePointTest {
        @Test
        @DisplayName("포인트 충전이 DB에 정상적으로 반영된다")
        void chargePoint() {
            // given
            long userId = 1L;
            long chargeAmount = 1000L;

            // when
            UserPoint userPoint = pointService.adjustUserPoint(CHARGE, userId, chargeAmount);

            // then
            assertEquals(chargeAmount, userPoint.point());

            // 정상 반영되었는지 확인
            UserPoint savedPoint = pointService.selectById(userId);
            assertEquals(chargeAmount, savedPoint.point());

            // 이력이 저장되었는지 확인
            List<PointHistory> histories = pointService.selectHistoriesByUserId(userId);
            assertFalse(histories.isEmpty());
            assertEquals(chargeAmount, histories.get(0).amount());
            assertEquals(CHARGE, histories.get(0).type());
        }

        @Test
        @DisplayName("여러번 충전시 포인트가 누적된다")
        void multipleCharges() {
            // given
            long userId = 2L;
            long firstCharge = 1000L;
            long secondCharge = 2000L;

            // when
            pointService.adjustUserPoint(CHARGE, userId, firstCharge);
            UserPoint userPoint = pointService.adjustUserPoint(CHARGE, userId, secondCharge);

            // then
            assertEquals(firstCharge + secondCharge, userPoint.point());

            List<PointHistory> histories = pointService.selectHistoriesByUserId(userId);
            assertEquals(2, histories.size());
        }
    }

    @Nested
    @DisplayName("포인트 사용 통합 테스트")
    class UsePointTest {
        @Test
        @DisplayName("포인트 사용이 DB에 정상적으로 반영된다")
        void usePoint() {
            // given
            long userId = 3L;
            long initialCharge = 5000L;
            long useAmount = 3000L;

            // when
            pointService.adjustUserPoint(CHARGE, userId, initialCharge);
            UserPoint userPoint = pointService.adjustUserPoint(USE, userId, useAmount);

            // then
            assertEquals(initialCharge - useAmount, userPoint.point());

            // DB에 반영되었는지 확인
            UserPoint savedPoint = pointService.selectById(userId);
            assertEquals(initialCharge - useAmount, savedPoint.point());

            // 이력이 저장되었는지 확인
            List<PointHistory> histories = pointService.selectHistoriesByUserId(userId);
            assertEquals(2, histories.size());
        }
    }

    @Nested
    @DisplayName("동시성 통합 테스트")
    class ConcurrencyTest {
        @Test
        @DisplayName("한 사용자의 여러 요청이 동시에 들어와도 포인트가 정확하게 처리된다")
        void concurrentRequestsForOneUser() throws InterruptedException {
            // given
            long userId = 4L;
            int threadCount = 10;
            long chargeAmount = 1000L;

            ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);

            // when
            for (int i = 0; i < threadCount; i++) {
                executorService.submit(() -> {
                    try {
                        pointService.adjustUserPoint(CHARGE, userId, chargeAmount);
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(10, TimeUnit.SECONDS);

            // then
            UserPoint finalPoint = pointService.selectById(userId);
            assertEquals(chargeAmount * threadCount, finalPoint.point());

            List<PointHistory> histories = pointService.selectHistoriesByUserId(userId);
            assertEquals(threadCount, histories.size());
        }

        @Test
        @DisplayName("서로 다른 사용자의 요청은 동시에 처리된다")
        void concurrentRequestsForDifferentUsers() throws InterruptedException {
            // given
            long userId1 = 5L;
            long userId2 = 6L;
            int threadCount = 5;
            long chargeAmount = 1000L;

            ExecutorService executorService = Executors.newFixedThreadPool(threadCount * 2);
            CountDownLatch startLatch = new CountDownLatch(1); // 모든 스레드가 동시에 시작하도록
            CountDownLatch endLatch = new CountDownLatch(threadCount * 2);

            List<Future<?>> futures = new ArrayList<>();

            // when
            // 모든 작업을 준비만 해두고
            for (int i = 0; i < threadCount; i++) {
                // 첫 번째 사용자의 요청들
                futures.add(executorService.submit(() -> {
                    try {
                        startLatch.await(); // 시작 신호 대기
                        pointService.adjustUserPoint(CHARGE, userId1, chargeAmount);
                        return null;
                    } finally {
                        endLatch.countDown();
                    }
                }));

                // 두 번째 사용자의 요청들
                futures.add(executorService.submit(() -> {
                    try {
                        startLatch.await(); // 시작 신호 대기
                        pointService.adjustUserPoint(CHARGE, userId2, chargeAmount);
                        return null;
                    } finally {
                        endLatch.countDown();
                    }
                }));
            }

            // 모든 스레드가 준비된 후 동시에 시작하도록 신호
            startLatch.countDown();

            // then
            // 모든 요청이 완료될 때까지 대기
            boolean completed = endLatch.await(15, TimeUnit.SECONDS);
            assertTrue(completed, "일부 요청이 시간 내에 완료되지 않았습니다");

            // 각 스레드의 예외 확인
            for (Future<?> future : futures) {
                assertDoesNotThrow(() -> future.get());
            }

            // 각 사용자의 최종 포인트 확인
            UserPoint user1Point = pointService.selectById(userId1);
            UserPoint user2Point = pointService.selectById(userId2);

            assertEquals(chargeAmount * threadCount, user1Point.point());
            assertEquals(chargeAmount * threadCount, user2Point.point());

            // 각 사용자의 이력 개수 확인
            List<PointHistory> historiesUser1 = pointService.selectHistoriesByUserId(userId1);
            List<PointHistory> historiesUser2 = pointService.selectHistoriesByUserId(userId2);

            assertEquals(threadCount, historiesUser1.size());
            assertEquals(threadCount, historiesUser2.size());

            executorService.shutdown();
        }

        @Test
        @DisplayName("동일 사용자의 포인트 요청이 순서대로 처리된다")
        void verifyPointRequestOrder() throws InterruptedException {
            // given
            long userId = 7L;
            List<Long> processOrder = Collections.synchronizedList(new ArrayList<>());

            // 첫 번째 요청이 처리중
            long amount1 = 1000L;
            pointService.adjustUserPoint(CHARGE, userId, amount1);

            // 두 번째, 세 번째 요청 준비
            Thread thread2 = new Thread(() -> {
                try {
                    long amount2 = 2000L;
                    pointService.adjustUserPoint(CHARGE, userId, amount2);
                    processOrder.add(amount2);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

            Thread thread3 = new Thread(() -> {
                try {
                    long amount3 = 3000L;
                    pointService.adjustUserPoint(CHARGE, userId, amount3);
                    processOrder.add(amount3);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

            // when
            thread2.start();
            Thread.sleep(100);  // thread2가 먼저 대기하도록 보장
            thread3.start();
            Thread.sleep(100);  // thread3가 대기하도록 보장

            // then
            Thread.sleep(1000);  // 모든 처리가 완료될 때까지 대기

            // 처리 순서 확인
            assertEquals(Arrays.asList(2000L, 3000L), processOrder);

            // 최종 포인트 확인
            UserPoint finalPoint = pointService.selectById(userId);
            assertEquals(6000L, finalPoint.point());  // 1000 + 2000 + 3000

            // 이력 확인
            List<PointHistory> histories = pointService.selectHistoriesByUserId(userId);
            assertEquals(3, histories.size());
            assertEquals(1000L, histories.get(0).amount());
            assertEquals(2000L, histories.get(1).amount());
            assertEquals(3000L, histories.get(2).amount());
        }
    }

    @Test
    @DisplayName("에러 발생 시 포인트가 변경되지 않는다.")
    void transactionRollback() {
        // given
        long userId = 8L;
        long initialCharge = 5000L;
        long useAmount = 6000L;  // 잔액보다 큰 금액 => validate에서 에러 발생

        // when
        pointService.adjustUserPoint(CHARGE, userId, initialCharge);

        // then
        assertThrows(IllegalArgumentException.class,
                () -> pointService.adjustUserPoint(USE, userId, useAmount));

        UserPoint userPoint = pointService.selectById(userId);
        assertEquals(initialCharge, userPoint.point());  // 원래 금액이 유지되어야 함
    }
}