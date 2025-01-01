package io.hhplus.tdd.point;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static io.hhplus.tdd.point.TransactionType.CHARGE;
import static io.hhplus.tdd.point.TransactionType.USE;
import static org.junit.jupiter.api.Assertions.*;

class UserPointTest {
    @Nested
    class ChargeValidationTest {
        @Test
        void 포인트_충전시_정상금액을_입력하면_검증을_통과한다() {
            // given
            UserPoint userPoint = new UserPoint(1L, 1000, System.currentTimeMillis());
            long chargeAmount = 5000;

            // when & then
            assertDoesNotThrow(() ->
                    userPoint.validate(CHARGE, chargeAmount)
            );
        }

        @Test
        void 포인트_충전시_음수값을_입력하면_IllegalArgumentException을_반환한다() {
            // given
            UserPoint userPoint = new UserPoint(1L, 1000, System.currentTimeMillis());
            long chargeAmount = -5000;

            // when & then
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> userPoint.validate(CHARGE, chargeAmount)
            );
            assertEquals("충전/사용 포인트는 0보다 커야합니다.", exception.getMessage());
        }

        @Test
        void 포인트_충전시_최대치_100000원을_초과하면_IllegalArgumentException을_반환한다() {
            // given
            UserPoint userPoint = new UserPoint(1L, 90000, System.currentTimeMillis());
            long chargeAmount = 20000;

            // when & then
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> userPoint.validate(CHARGE, chargeAmount)
            );
            assertEquals("포인트가 최대치를 초과했습니다.", exception.getMessage());
        }
    }

    @Nested
    class UseValidationTest {
        @Test
        void 포인트_사용시_잔액내에서_사용하면_검증을_통과한다() {
            // given
            UserPoint userPoint = new UserPoint(1L, 5000, System.currentTimeMillis());
            long useAmount = 3000;

            // when & then
            assertDoesNotThrow(() ->
                    userPoint.validate(USE, useAmount)
            );
        }

        @Test
        void 포인트_사용시_음수값을_입력하면_IllegalArgumentException을_반환한다() {
            // given
            UserPoint userPoint = new UserPoint(1L, 5000, System.currentTimeMillis());
            long useAmount = -1000;

            // when & then
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> userPoint.validate(USE, useAmount)
            );
            assertEquals("충전/사용 포인트는 0보다 커야합니다.", exception.getMessage());
        }

        @Test
        void 포인트_사용시_잔액을_초과하면_IllegalArgumentException을_반환한다() {
            // given
            UserPoint userPoint = new UserPoint(1L, 1000, System.currentTimeMillis());
            long useAmount = 2000;

            // when & then
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> userPoint.validate(USE, useAmount)
            );
            assertEquals("포인트가 부족합니다.", exception.getMessage());
        }
    }

    @Nested
    class BoundaryTest {
        @Test
        void 포인트_충전과_사용시_0포인트를_입력하면_IllegalArgumentException을_반환한다() {
            // given
            UserPoint userPoint = new UserPoint(1L, 1000, System.currentTimeMillis());
            long amount = 0;

            // when & then
            assertAll(
                    () -> {
                        IllegalArgumentException exception = assertThrows(
                                IllegalArgumentException.class,
                                () -> userPoint.validate(CHARGE, amount)
                        );
                        assertEquals("충전/사용 포인트는 0보다 커야합니다.", exception.getMessage());
                    },
                    () -> {
                        IllegalArgumentException exception = assertThrows(
                                IllegalArgumentException.class,
                                () -> userPoint.validate(USE, amount)
                        );
                        assertEquals("충전/사용 포인트는 0보다 커야합니다.", exception.getMessage());
                    }
            );
        }

        @Test
        void 포인트_충전과_사용시_1포인트를_입력하면_검증을_통과한다() {
            // given
            UserPoint userPoint = new UserPoint(1L, 1000, System.currentTimeMillis());
            long amount = 1;

            // when & then
            assertAll(
                    () -> assertDoesNotThrow(() ->
                            userPoint.validate(CHARGE, amount)
                    ),
                    () -> assertDoesNotThrow(() ->
                            userPoint.validate(USE, amount)
                    )
            );
        }

        @Test
        void 포인트_충전시_정확히_최대치_100000원이_되도록_충전하면_검증을_통과한다() {
            // given
            UserPoint userPoint = new UserPoint(1L, 90000, System.currentTimeMillis());
            long chargeAmount = 10000;

            // when & then
            assertDoesNotThrow(() ->
                    userPoint.validate(CHARGE, chargeAmount)
            );
        }
    }
}