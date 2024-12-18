package io.hhplus.tdd.point;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import static io.hhplus.tdd.point.TransactionType.*;

@Service
@RequiredArgsConstructor
public class PointService {

    private final UserPointTable userPointTable;
    private final PointHistoryTable pointHistoryTable;
    private final UserLockManager lockManager;

    private static final long MAX_POINT = 100000;

    public UserPoint selectById(long id) {
        return userPointTable.selectById(id);
    }

    public List<PointHistory> selectHistoriesByUserId(long id) {
        return pointHistoryTable.selectAllByUserId(id);
    }

    public UserPoint adjustUserPoint(TransactionType transactionType, long userId, long adjustAmount) {
        ReentrantLock lock = lockManager.getLock(userId);
        lock.lock();
        try {
            UserPoint userPoint = selectById(userId);
            validate(transactionType, userPoint.point(), adjustAmount);

            long updatedPoint = userPoint.point() + (transactionType.equals(CHARGE) ? adjustAmount : -adjustAmount);
            userPoint = userPointTable.insertOrUpdate(userPoint.id(), updatedPoint);

            recordPointHistory(userId, adjustAmount, transactionType);
            return userPoint;
        } catch (Exception e) {
            throw e;
        } finally {
            lock.unlock();
        }
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
