package io.hhplus.tdd.point;

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
    class ChargePointTest {
        @Test
        void 포인트_충전시_DB에_정상적으로_반영되고_충전된_포인트_정보를_반환한다() {
            // given
            long userId = 1L;
            long chargeAmount = 1000L;

            // when
            UserPoint userPoint = pointService.charge(userId, chargeAmount);

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
        void 포인트_충전을_여러번_하면_포인트가_누적되어_반영된다() {
            // given
            long userId = 2L;
            long firstCharge = 1000L;
            long secondCharge = 2000L;

            // when
            pointService.charge(userId, firstCharge);
            UserPoint userPoint = pointService.charge(userId, secondCharge);

            // then
            assertEquals(firstCharge + secondCharge, userPoint.point());

            List<PointHistory> histories = pointService.selectHistoriesByUserId(userId);
            assertEquals(2, histories.size());
        }
    }

    @Nested
    class UsePointTest {
        @Test
        void 포인트_사용시_DB에_정상적으로_반영되고_차감된_포인트_정보를_반환한다() {
            // given
            long userId = 3L;
            long initialCharge = 5000L;
            long useAmount = 3000L;

            // when
            pointService.charge(userId, initialCharge);
            UserPoint userPoint = pointService.use(userId, useAmount);

            // then
            assertEquals(initialCharge - useAmount, userPoint.point());

            // DB에 반영되었는지 확인
            UserPoint savedPoint = pointService.selectById(userId);
            assertEquals(initialCharge - useAmount, savedPoint.point());

            // 이력이 저장되었는지 확인
            List<PointHistory> histories = pointService.selectHistoriesByUserId(userId);
            assertEquals(2, histories.size());
            assertEquals(useAmount, histories.get(1).amount());
            assertEquals(USE, histories.get(1).type());
        }

        @Test
        void 포인트_사용시_에러가_발생하면_포인트가_변경되지_않는다() {
            // given
            long userId = 4L;
            long initialCharge = 5000L;
            long useAmount = 6000L;  // 잔액보다 큰 금액 => validate에서 에러 발생

            // when
            pointService.charge(userId, initialCharge);

            // then
            assertThrows(IllegalArgumentException.class,
                    () -> pointService.use(userId, useAmount));

            UserPoint userPoint = pointService.selectById(userId);
            assertEquals(initialCharge, userPoint.point());  // 원래 금액이 유지되어야 함
        }
    }

    @Nested
    class ConcurrencyTest {
        @Test
        void 동일_사용자의_여러_포인트_요청이_동시에_들어와도_포인트가_정확하게_처리된다() throws InterruptedException {
            // given
            long userId = 5L;
            int threadCount = 10;
            long chargeAmount = 1000L;

            ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);

            // when
            for (int i = 0; i < threadCount; i++) {
                executorService.submit(() -> {
                    try {
                        pointService.charge(userId, chargeAmount);
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
        void 동일_사용자의_여러_포인트_충전과_사용_요청이_동시에_들어와도_포인트가_정확하게_처리된다() throws InterruptedException {
            // given
            long userId = 6L;
            long amount = 1000L;

            // 초기 잔액 5000원
            pointService.charge(userId, amount * 5);

            // 5번의 충전(+1000)과 5번의 사용(-1000) 요청 준비
            int numberOfRequests = 10;
            ExecutorService executorService = Executors.newFixedThreadPool(numberOfRequests);
            CountDownLatch latch = new CountDownLatch(numberOfRequests);

            // when
            for (int i = 0; i < numberOfRequests; i++) {
                boolean isCharge = i % 2 == 0;
                executorService.submit(() -> {
                    try {
                        if (isCharge) {
                            pointService.charge(userId, amount);
                        } else {
                            pointService.use(userId, amount);
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            // 모든 요청 완료 대기
            latch.await(10, TimeUnit.SECONDS);

            // then
            UserPoint finalPoint = pointService.selectById(userId);
            assertEquals(amount * 5, finalPoint.point());  // 최종 잔액은 초기 잔액과 동일

            List<PointHistory> histories = pointService.selectHistoriesByUserId(userId);
            assertEquals(11, histories.size());  // 초기 충전 1회 + (충전 5회 + 사용 5회)
        }

        @Test
        void 서로_다른_사용자의_포인트_요청은_동시에_처리된다() throws InterruptedException {
            // given
            long userId1 = 7L;
            long userId2 = 8L;
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
                        pointService.charge(userId1, chargeAmount);
                        return null;
                    } finally {
                        endLatch.countDown();
                    }
                }));

                // 두 번째 사용자의 요청들
                futures.add(executorService.submit(() -> {
                    try {
                        startLatch.await(); // 시작 신호 대기
                        pointService.charge(userId2, chargeAmount);
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
        void 동일_사용자의_포인트_요청은_도달_순서대로_처리된다() throws InterruptedException {
            // given
            long userId = 9L;
            List<Long> processOrder = Collections.synchronizedList(new ArrayList<>());

            // 첫 번째 요청이 처리중
            long amount1 = 1000L;
            pointService.charge(userId, amount1);

            // 두 번째, 세 번째 요청 준비
            Thread thread2 = new Thread(() -> {
                try {
                    long amount2 = 2000L;
                    pointService.charge(userId, amount2);
                    processOrder.add(amount2);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

            Thread thread3 = new Thread(() -> {
                try {
                    long amount3 = 3000L;
                    pointService.use(userId, amount3);
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
            assertEquals(0, finalPoint.point());  // 1000 + 2000 + 3000

            // 이력 확인
            List<PointHistory> histories = pointService.selectHistoriesByUserId(userId);
            assertEquals(3, histories.size());
            assertEquals(1000L, histories.get(0).amount());
            assertEquals(2000L, histories.get(1).amount());
            assertEquals(3000L, histories.get(2).amount());
        }
    }

}