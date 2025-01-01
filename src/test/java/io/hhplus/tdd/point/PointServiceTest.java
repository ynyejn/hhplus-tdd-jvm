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
    class ChargePointTest {
        @Test
        void 포인트_충전시_정상_금액을_충전하면_충전된_포인트_정보를_반환한다() {
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
            UserPoint result = pointService.charge(userId, chargeAmount);

            // then
            assertEquals(currentPoint + chargeAmount, result.point());
            verify(pointHistoryTable).insert(eq(userId), eq(chargeAmount), eq(CHARGE), anyLong());
        }

        @Test
        void 포인트_충전시_최대치_100000원을_초과하면_IllegalArgumentException을_반환한다() {
            // given
            long userId = 1L;
            long currentPoint = 90000L;
            long chargeAmount = 20000L;
            UserPoint userPoint = new UserPoint(userId, currentPoint, System.currentTimeMillis());

            when(lockManager.getLock(userId)).thenReturn(new ReentrantLock());
            when(userPointTable.selectById(userId)).thenReturn(userPoint);

            // when & then
            assertThrows(IllegalArgumentException.class,
                    () -> pointService.charge(userId, chargeAmount));
            verify(userPointTable, never()).insertOrUpdate(anyLong(), anyLong());
            verify(pointHistoryTable, never()).insert(anyLong(), anyLong(), any(), anyLong());
        }
    }

    @Nested
    class UsePointTest {
        @Test
        void 포인트_사용시_잔액_내에서_사용하면_차감된_포인트_정보를_반환한다() {
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
            UserPoint result = pointService.use(userId, useAmount);

            // then
            assertEquals(currentPoint - useAmount, result.point());
            verify(pointHistoryTable).insert(eq(userId), eq(useAmount), eq(USE), anyLong());
        }

        @Test
        void 포인트_사용시_잔액이_부족하면_IllegalArgumentException을_반환한다() {
            // given
            long userId = 1L;
            long currentPoint = 500L;
            long useAmount = 1000L;
            UserPoint userPoint = new UserPoint(userId, currentPoint, System.currentTimeMillis());

            when(lockManager.getLock(userId)).thenReturn(new ReentrantLock());
            when(userPointTable.selectById(userId)).thenReturn(userPoint);

            // when & then
            assertThrows(IllegalArgumentException.class,
                    () -> pointService.use(userId, useAmount));
            verify(userPointTable, never()).insertOrUpdate(anyLong(), anyLong());
            verify(pointHistoryTable, never()).insert(anyLong(), anyLong(), any(), anyLong());
        }
    }

    @Test
    void 포인트_충전시_음수_금액을_충전하면_IllegalArgumentException을_반환한다() {
        // given
        long userId = 1L;
        long currentPoint = 1000L;
        long negativeAmount = -500L;
        UserPoint userPoint = new UserPoint(userId, currentPoint, System.currentTimeMillis());

        when(lockManager.getLock(userId)).thenReturn(new ReentrantLock());
        when(userPointTable.selectById(userId)).thenReturn(userPoint);

        // when & then
        assertThrows(IllegalArgumentException.class,
                () -> pointService.charge(userId, negativeAmount));
        verify(userPointTable, never()).insertOrUpdate(anyLong(), anyLong());
        verify(pointHistoryTable, never()).insert(anyLong(), anyLong(), any(), anyLong());
    }

    @Test
    void 포인트_사용시_음수_금액을_사용하면_IllegalArgumentException을_반환한다() {
        // given
        long userId = 1L;
        long currentPoint = 1000L;
        long negativeAmount = -500L;
        UserPoint userPoint = new UserPoint(userId, currentPoint, System.currentTimeMillis());

        when(lockManager.getLock(userId)).thenReturn(new ReentrantLock());
        when(userPointTable.selectById(userId)).thenReturn(userPoint);

        // when & then
        assertThrows(IllegalArgumentException.class,
                () -> pointService.use(userId, negativeAmount));
        verify(userPointTable, never()).insertOrUpdate(anyLong(), anyLong());
        verify(pointHistoryTable, never()).insert(anyLong(), anyLong(), any(), anyLong());
    }


}