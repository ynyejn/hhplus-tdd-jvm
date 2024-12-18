package io.hhplus.tdd.point;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.*;
import static io.hhplus.tdd.point.TransactionType.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class PointServiceTest {

    @Mock
    private UserPointTable userPointTable;

    @Mock
    private PointHistoryTable pointHistoryTable;

    @Mock
    private UserLockManager lockManager;

    @InjectMocks
    private PointService pointService;

    @Nested
    @DisplayName("포인트 충전 검증 테스트")
    class ChargeValidationTest {
        @Test
        @DisplayName("정상적인 충전 요청은 검증을 통과한다")
        void validateSuccessfulCharge() {
            // given
            long currentPoint = 1000;
            long chargeAmount = 5000;

            // when & then
            assertDoesNotThrow(() ->
                    pointService.validate(CHARGE, currentPoint, chargeAmount)
            );
        }

        @Test
        @DisplayName("음수 포인트 충전 시 예외가 발생한다")
        void validateNegativeChargeAmount() {
            // given
            long currentPoint = 1000;
            long chargeAmount = -5000;

            // when & then
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> pointService.validate(CHARGE, currentPoint, chargeAmount)
            );
            assertEquals("충전/사용 포인트는 0보다 커야합니다.", exception.getMessage());
        }

        @Test
        @DisplayName("최대 포인트 초과 충전 시 예외가 발생한다")
        void validateExceedMaxPointCharge() {
            // given
            long currentPoint = 90000;
            long chargeAmount = 20000;

            // when & then
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> pointService.validate(CHARGE, currentPoint, chargeAmount)
            );
            assertEquals("포인트가 최대치를 초과했습니다.", exception.getMessage());
        }
    }

    @Nested
    @DisplayName("포인트 사용 검증 테스트")
    class UseValidationTest {
        @Test
        @DisplayName("정상적인 포인트 사용은 검증을 통과한다")
        void validateSuccessfulUse() {
            // given
            long currentPoint = 5000;
            long useAmount = 3000;

            // when & then
            assertDoesNotThrow(() ->
                    pointService.validate(USE, currentPoint, useAmount)
            );
        }

        @Test
        @DisplayName("음수 포인트 사용 시 예외가 발생한다")
        void validateNegativeUseAmount() {
            // given
            long currentPoint = 5000;
            long useAmount = -1000;

            // when & then
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> pointService.validate(USE, currentPoint, useAmount)
            );
            assertEquals("충전/사용 포인트는 0보다 커야합니다.", exception.getMessage());
        }

        @Test
        @DisplayName("잔액 초과 사용 시 예외가 발생한다")
        void validateInsufficientBalance() {
            // given
            long currentPoint = 1000;
            long useAmount = 2000;

            // when & then
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> pointService.validate(USE, currentPoint, useAmount)
            );
            assertEquals("포인트가 부족합니다.", exception.getMessage());
        }
    }

    @Nested
    @DisplayName("경계값 테스트")
    class BoundaryTest {
        @Test
        @DisplayName("0 포인트는 충전/사용할 수 없다")
        void validateZeroAmount() {
            // given
            long currentPoint = 1000;
            long amount = 0;

            // when & then
            assertAll(
                    () -> {
                        IllegalArgumentException exception = assertThrows(
                                IllegalArgumentException.class,
                                () -> pointService.validate(CHARGE, currentPoint, amount)
                        );
                        assertEquals("충전/사용 포인트는 0보다 커야합니다.", exception.getMessage());
                    },
                    () -> {
                        IllegalArgumentException exception = assertThrows(
                                IllegalArgumentException.class,
                                () -> pointService.validate(USE, currentPoint, amount)
                        );
                        assertEquals("충전/사용 포인트는 0보다 커야합니다.", exception.getMessage());
                    }
            );
        }

        @Test
        @DisplayName("1 포인트는 충전/사용이 가능하다")
        void validateOnePointAmount() {
            // given
            long currentPoint = 1000;
            long amount = 1;

            // when & then
            assertAll(
                    () -> assertDoesNotThrow(() ->
                            pointService.validate(CHARGE, currentPoint, amount)
                    ),
                    () -> assertDoesNotThrow(() ->
                            pointService.validate(USE, currentPoint, amount)
                    )
            );
        }

        @Test
        @DisplayName("정확히 최대 포인트까지 충전 가능하다")
        void validateExactMaxPoint() {
            // given
            long currentPoint = 90000;
            long chargeAmount = 10000; // 최대값인 100000까지 정확히 충전

            // when & then
            assertDoesNotThrow(() ->
                    pointService.validate(CHARGE, currentPoint, chargeAmount)
            );
        }
    }


    @Nested
    @DisplayName("포인트 충전 테스트")
    class ChargePointTest {
        @Test
        @DisplayName("포인트 충전이 성공적으로 처리된다")
        void chargeSuccess() {
            // given
            long userId = 1L;
            long currentPoint = 1000L;
            long chargeAmount = 500L;
            UserPoint initialPoint = new UserPoint(userId, currentPoint, System.currentTimeMillis());
            UserPoint updatedPoint = new UserPoint(userId, currentPoint + chargeAmount, System.currentTimeMillis());

            when(lockManager.getLock(userId)).thenReturn(new ReentrantLock());
            when(userPointTable.selectById(userId)).thenReturn(initialPoint);
            when(userPointTable.insertOrUpdate(userId, currentPoint + chargeAmount)).thenReturn(updatedPoint);

            // when
            UserPoint result = pointService.adjustUserPoint(CHARGE, userId, chargeAmount);

            // then
            assertEquals(currentPoint + chargeAmount, result.point());
            verify(pointHistoryTable).insert(eq(userId), eq(chargeAmount), eq(CHARGE), anyLong());
        }

        @Test
        @DisplayName("최대 포인트 초과 충전시 예외가 발생한다")
        void exceedMaxPointCharge() {
            // given
            long userId = 1L;
            long currentPoint = 90000L;
            long chargeAmount = 20000L;
            UserPoint userPoint = new UserPoint(userId, currentPoint, System.currentTimeMillis());

            when(lockManager.getLock(userId)).thenReturn(new ReentrantLock());
            when(userPointTable.selectById(userId)).thenReturn(userPoint);

            // when & then
            assertThrows(IllegalArgumentException.class,
                    () -> pointService.adjustUserPoint(CHARGE, userId, chargeAmount));
            verify(userPointTable, never()).insertOrUpdate(anyLong(), anyLong());
            verify(pointHistoryTable, never()).insert(anyLong(), anyLong(), any(), anyLong());
        }
    }

    @Nested
    @DisplayName("포인트 사용 테스트")
    class UsePointTest {
        @Test
        @DisplayName("포인트 사용이 성공적으로 처리된다")
        void useSuccess() {
            // given
            long userId = 1L;
            long currentPoint = 1000L;
            long useAmount = 500L;
            UserPoint initialPoint = new UserPoint(userId, currentPoint, System.currentTimeMillis());
            UserPoint updatedPoint = new UserPoint(userId, currentPoint - useAmount, System.currentTimeMillis());

            when(lockManager.getLock(userId)).thenReturn(new ReentrantLock());
            when(userPointTable.selectById(userId)).thenReturn(initialPoint);
            when(userPointTable.insertOrUpdate(userId, currentPoint - useAmount)).thenReturn(updatedPoint);

            // when
            UserPoint result = pointService.adjustUserPoint(USE, userId, useAmount);

            // then
            assertEquals(currentPoint - useAmount, result.point());
            verify(pointHistoryTable).insert(eq(userId), eq(useAmount), eq(USE), anyLong());
        }

        @Test
        @DisplayName("잔액 부족시 예외가 발생한다")
        void insufficientBalance() {
            // given
            long userId = 1L;
            long currentPoint = 500L;
            long useAmount = 1000L;
            UserPoint userPoint = new UserPoint(userId, currentPoint, System.currentTimeMillis());

            when(lockManager.getLock(userId)).thenReturn(new ReentrantLock());
            when(userPointTable.selectById(userId)).thenReturn(userPoint);

            // when & then
            assertThrows(IllegalArgumentException.class,
                    () -> pointService.adjustUserPoint(USE, userId, useAmount));
            verify(userPointTable, never()).insertOrUpdate(anyLong(), anyLong());
            verify(pointHistoryTable, never()).insert(anyLong(), anyLong(), any(), anyLong());
        }
    }

    @Test
    @DisplayName("음수 금액 입력시 예외가 발생한다")
    void negativeAmountFails() {
        // given
        long userId = 1L;
        long currentPoint = 1000L;
        long negativeAmount = -500L;
        UserPoint userPoint = new UserPoint(userId, currentPoint, System.currentTimeMillis());

        when(lockManager.getLock(userId)).thenReturn(new ReentrantLock());
        when(userPointTable.selectById(userId)).thenReturn(userPoint);

        // when & then
        assertThrows(IllegalArgumentException.class,
                () -> pointService.adjustUserPoint(CHARGE, userId, negativeAmount));
        verify(userPointTable, never()).insertOrUpdate(anyLong(), anyLong());
        verify(pointHistoryTable, never()).insert(anyLong(), anyLong(), any(), anyLong());
    }


}