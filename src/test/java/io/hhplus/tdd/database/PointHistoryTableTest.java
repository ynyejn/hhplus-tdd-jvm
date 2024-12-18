package io.hhplus.tdd.database;

import io.hhplus.tdd.point.PointHistory;
import io.hhplus.tdd.point.TransactionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static io.hhplus.tdd.point.TransactionType.*;

@DisplayName("PointHistoryTable 테스트")
class PointHistoryTableTest {

    private final PointHistoryTable pointHistoryTable = new PointHistoryTable();

    @Nested
    @DisplayName("insert 테스트")
    class InsertTest {
        @Test
        @DisplayName("충전 이력이 정상적으로 저장된다")
        void insertChargeHistory() {
            // given
            long userId = 1L;
            long amount = 1000L;
            TransactionType type = CHARGE;
            long updateMillis = System.currentTimeMillis();

            // when
            PointHistory result = pointHistoryTable.insert(userId, amount, type, updateMillis);

            // then
            assertEquals(userId, result.userId());
            assertEquals(amount, result.amount());
            assertEquals(type, result.type());
            assertEquals(updateMillis, result.updateMillis());
        }

        @Test
        @DisplayName("사용 이력이 정상적으로 저장된다")
        void insertUseHistory() {
            // given
            long userId = 1L;
            long amount = 1000L;
            TransactionType type = USE;
            long updateMillis = System.currentTimeMillis();

            // when
            PointHistory result = pointHistoryTable.insert(userId, amount, type, updateMillis);

            // then
            assertEquals(userId, result.userId());
            assertEquals(amount, result.amount());
            assertEquals(type, result.type());
            assertEquals(updateMillis, result.updateMillis());
        }

        @Test
        @DisplayName("이력 저장시 ID가 순차적으로 증가한다")
        void cursorIncreasesSequentially() {
            // given
            long userId = 1L;
            long amount = 1000L;
            TransactionType type = CHARGE;
            long updateMillis = System.currentTimeMillis();

            // when
            PointHistory first = pointHistoryTable.insert(userId, amount, type, updateMillis);
            PointHistory second = pointHistoryTable.insert(userId, amount, type, updateMillis);

            // then
            assertEquals(first.id() + 1, second.id());
        }
    }

    @Nested
    @DisplayName("selectAllByUserId 테스트")
    class SelectAllByUserIdTest {
        @Test
        @DisplayName("사용자의 모든 포인트 이력을 조회할 수 있다")
        void returnAllHistoriesForUser() {
            // given
            long userId = 1L;
            long amount = 1000L;
            TransactionType type = CHARGE;
            long updateMillis = System.currentTimeMillis();
            pointHistoryTable.insert(userId, amount, type, updateMillis);

            // when
            List<PointHistory> result = pointHistoryTable.selectAllByUserId(userId);

            // then
            assertEquals(1, result.size());
            assertEquals(userId, result.get(0).userId());
            assertEquals(amount, result.get(0).amount());
            assertEquals(type, result.get(0).type());
            assertEquals(updateMillis, result.get(0).updateMillis());
        }

        @Test
        @DisplayName("저장되지 않은 사용자의 이력 조회시 빈 리스트를 반환한다")
        void returnEmptyListForNonExistentUser() {
            // given
            long userId = 1L;
            // when
            List<PointHistory> histories = pointHistoryTable.selectAllByUserId(userId);
            // then
            assertTrue(histories.isEmpty());
        }

        @Test
        @DisplayName("여러 사용자의 이력이 섞여있어도 특정 사용자의 이력만 정확히 조회된다")
        void returnOnlyTargetUserHistories() {
            // given
            long userId = 1L;
            long amount = 1000L;
            TransactionType type = CHARGE;
            long updateMillis = System.currentTimeMillis();
            pointHistoryTable.insert(userId, amount, type, updateMillis);
            pointHistoryTable.insert(2L, amount, type, updateMillis);

            // when
            List<PointHistory> histories = pointHistoryTable.selectAllByUserId(userId);
            // then
            assertEquals(1, histories.size());
            assertEquals(userId, histories.get(0).userId());
        }

        @Test
        @DisplayName("이력이 저장된 순서대로 조회된다")
        void maintainInsertionOrder() {
            // given
            long userId = 1L;
            long amount = 1000L;
            TransactionType type = CHARGE;
            long updateMillis = System.currentTimeMillis();
            pointHistoryTable.insert(userId, amount++, type, updateMillis);
            pointHistoryTable.insert(userId, amount, type, updateMillis);

            // when
            List<PointHistory> histories = pointHistoryTable.selectAllByUserId(userId);

            // then
            assertEquals(2, histories.size());
            assertEquals(1000, histories.get(0).amount());
            assertEquals(1001, histories.get(1).amount());


        }
    }
}