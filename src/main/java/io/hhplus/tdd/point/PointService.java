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

    public UserPoint selectById(long id) {
        return userPointTable.selectById(id);
    }

    public List<PointHistory> selectHistoriesByUserId(long id) {
        return pointHistoryTable.selectAllByUserId(id);
    }

    public UserPoint charge(long userId, long chargeAmount) {
        ReentrantLock lock = lockManager.getLock(userId);
        lock.lock();
        try {
            UserPoint userPoint = selectById(userId);

            userPoint.validate(CHARGE, chargeAmount);

            // 포인트 업데이트
            long updatedPoint = userPoint.point() + chargeAmount;
            userPoint = userPointTable.insertOrUpdate(userPoint.id(), updatedPoint);

            // 포인트 이력 기록
            recordPointHistory(userId, chargeAmount, TransactionType.CHARGE);

            return userPoint;
        } finally {
            lock.unlock();
        }
    }

    public UserPoint use(long userId, long useAmount) {
        ReentrantLock lock = lockManager.getLock(userId);
        lock.lock();
        try {
            UserPoint userPoint = selectById(userId);

            // 유효성 검증
            userPoint.validate(USE, useAmount);

            // 포인트 업데이트
            long updatedPoint = userPoint.point() - useAmount;
            userPoint = userPointTable.insertOrUpdate(userPoint.id(), updatedPoint);

            // 포인트 이력 기록
            recordPointHistory(userId, useAmount, TransactionType.USE);

            return userPoint;
        } finally {
            lock.unlock();
        }
    }


    public void recordPointHistory(long userId, long amount, TransactionType type) {
        pointHistoryTable.insert(userId, amount, type, System.currentTimeMillis());
    }

}
