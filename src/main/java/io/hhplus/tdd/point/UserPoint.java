package io.hhplus.tdd.point;

public record UserPoint(
        long id,
        long point,
        long updateMillis
) {
    private static final long MAX_POINT = 100000;

    public static UserPoint empty(long id) {
        return new UserPoint(id, 0, System.currentTimeMillis());
    }

    public void validate(TransactionType transactionType, long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("충전/사용 포인트는 0보다 커야합니다.");
        }

        switch (transactionType) {
            case CHARGE -> {
                if (point + amount > MAX_POINT) {
                    throw new IllegalArgumentException("포인트가 최대치를 초과했습니다.");
                }
            }
            case USE -> {
                if (point - amount < 0) {
                    throw new IllegalArgumentException("포인트가 부족합니다.");
                }
            }
        }

    }
}
