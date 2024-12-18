package io.hhplus.tdd.database;

import io.hhplus.tdd.point.UserPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("UserPointTable 테스트")
class UserPointTableTest {

    private final UserPointTable userPointTable = new UserPointTable();

    @Nested
    @DisplayName("selectById 테스트")
    class SelectByIdTest {
        @Test
        @DisplayName("저장되지 않은 사용자 조회시 default value를 반환한다")
        void returnEmptyUserPoint() {
            // when
            long nonExistentId = 1L;
            UserPoint result = userPointTable.selectById(nonExistentId);

            // then
            assertEquals(nonExistentId, result.id());
            assertEquals(0L, result.point());
            assertTrue(result.updateMillis() > 0);
        }

    }

    @Nested
    @DisplayName("insertOrUpdate 테스트")
    class InsertOrUpdateTest {
        @Test
        @DisplayName("새로운 사용자 포인트 추가가 성공한다")
        void insertNewUserPoint() {
            // when
            long userId = 1L;
            long point = 1000L;
            UserPoint result = userPointTable.insertOrUpdate(userId, point);

            // then
            assertEquals(userId, result.id());
            assertEquals(point, result.point());
            assertTrue(result.updateMillis() > 0);
        }

        @Test
        @DisplayName("기존 사용자 포인트 업데이트가 성공한다")
        void updateExistingUserPoint() {
            // given
            long userId = 1L;
            long initialPoint = 1000L;
            userPointTable.insertOrUpdate(userId, initialPoint);

            // when
            long updatedPoint = 2000L;
            UserPoint updatedUserPoint = userPointTable.insertOrUpdate(userId, updatedPoint);

            // then
            assertEquals(userId, updatedUserPoint.id());
            assertEquals(updatedPoint, updatedUserPoint.point());
            assertTrue(updatedUserPoint.updateMillis() > 0);
        }

        @Test
        @DisplayName("업데이트된 포인트는 selectById로 조회할 수 있다")
        void verifyUpdatedPointWithSelect() {
            // given
            long userId = 1L;
            long point = 1000L;
            userPointTable.insertOrUpdate(userId, point);

            // when
            long updatedPoint = 2000L;
            userPointTable.insertOrUpdate(userId, updatedPoint);
            UserPoint updatedUserPoint = userPointTable.selectById(userId);

            // then
            assertEquals(userId, updatedUserPoint.id());
            assertEquals(updatedPoint, updatedUserPoint.point());
            assertTrue(updatedUserPoint.updateMillis() > 0);
        }
    }
}