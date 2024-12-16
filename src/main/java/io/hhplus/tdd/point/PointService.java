package io.hhplus.tdd.point;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

import static io.hhplus.tdd.point.TransactionType.*;

@Service
@RequiredArgsConstructor
public class PointService {

    private final UserPointTable userPointTable;
    private final PointHistoryTable pointHistoryTable;
    private static final long MAX_POINT = 100000;

    public UserPoint selectById(long id) {
        return userPointTable.selectById(id);
    }

    public List<PointHistory> selectHistoriesByUserId(long id) {
        return pointHistoryTable.selectAllByUserId(id);
    }

    public UserPoint adjustUserPoint(TransactionType transactionType, long id, long adjustAmount) {
        UserPoint userPoint = selectById(id);
        validate(transactionType, userPoint.point(), adjustAmount);

        long updatedPoint = userPoint.point() + (transactionType.equals(CHARGE) ? adjustAmount : -adjustAmount);
        userPoint = userPointTable.insertOrUpdate(userPoint.id(), updatedPoint);

        recordPointHistory(id, adjustAmount, transactionType);
        return userPoint;
    }

    public void validate(TransactionType transactionType, long currentPoint, long adjustAmount) {
        if (adjustAmount <= 0) {
            throw new IllegalArgumentException("충전/사용 포인트는 0보다 커야합니다.");
        }

        switch (transactionType) {
            case CHARGE -> {
                if (currentPoint + adjustAmount > MAX_POINT) {
                    throw new IllegalArgumentException("포인트가 최대치를 초과했습니다.");
                }
            }
            case USE -> {
                if (currentPoint - adjustAmount < 0) {
                    throw new IllegalArgumentException("포인트가 부족합니다.");
                }
            }
        }
    }

    public void recordPointHistory(long userId, long amount, TransactionType type) {
        pointHistoryTable.insert(userId, amount, type, System.currentTimeMillis());
    }

}
