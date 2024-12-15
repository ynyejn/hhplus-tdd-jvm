package io.hhplus.tdd.point;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PointService {

    private final UserPointTable userPointTable;
    private final PointHistoryTable pointHistoryTable;

    public UserPoint selectById(long id) {
        // 없는경우 empty 객체를 반환
        return userPointTable.selectById(id);
    }

    public List<PointHistory> selectHistoriesByUserId(long id) {
        // 이것도 없는경우 확인
        return pointHistoryTable.selectAllByUserId(id);
    }
    public UserPoint adjustUserPoint(TransactionType transactionType, long id, long amount) {
        UserPoint userPoint = selectById(id);

        validate(transactionType, userPoint.point(), amount);
        userPoint = userPointTable.insertOrUpdate(userPoint.id(), userPoint.point() + (transactionType.equals(TransactionType.CHARGE) ? amount : -amount));

        pointHistoryTable.insert(userPoint.id(), amount, transactionType, System.currentTimeMillis());
        return userPoint;
    }

    private void validate(TransactionType transactionType, long currentPoint, long amount) {
        if (transactionType.equals(TransactionType.CHARGE)){
            if (amount < 0) {
                throw new IllegalArgumentException("충전 포인트는 0보다 커야합니다.");
            }
            if (currentPoint + amount > 1000000) {
                throw new IllegalArgumentException("포인트가 최대치를 초과했습니다.");
            }
        } else {    // USE
            if (amount < 0) {
                throw new IllegalArgumentException("사용 포인트는 0보다 커야합니다.");
            }
            if (currentPoint - amount < 0) {
                throw new IllegalArgumentException("포인트가 부족합니다.");
            }
        }
    }

}
