# **포인트 관리시스템**

이 프로젝트는 사용자 포인트 관리를 위한 백엔드 애플리케이션입니다. 
사용자는 포인트를 충전하거나 사용할 수 있으며, 현재 잔여포인트와 포인트 변동 이력을 조회할 수 있습니다. 
동시성 제어를 통해 다수의 사용자가 동시에 포인트를 요청을 처리할 때 발생할 수 있는 문제를 방지합니다.

## **1.  요구사항**

### **1.1 포인트 조회**
- 사용자별 **현재 포인트 조회** 가능
- 존재하지 않는 사용자는 **0포인트**로 초기화

### **1.2 포인트 이력 조회**
- 사용자별 **포인트 변동 이력** 조회 가능
- 존재하지 않는 사용자는 **빈 리스트** 반환

### **1.3 포인트 충전**
- **충전 조건**:
    - 0보다 큰 포인트만 충전 가능. (최소 충전 포인트: 1점)
    - 최대 100,000점까지 충전 가능
    - 현재 포인트와 충전 포인트의 합이 **100,000점을 초과할 수 없음**
- **포인트 이력 기록**:
    - 충전 시 **포인트 변동 이력**이 기록되어야 함

### **1.4 포인트 사용**
- **사용 조건**:
    - 0보다 큰 포인트만 사용 가능 (최소 사용 포인트: 1점)
    - 현재 보유한 포인트보다 **많은 포인트는 사용할 수 없음**
- **포인트 이력 기록**:
    - 사용 시 **포인트 변동 이력**이 기록되어야 함

### **1.5 동시성 제어**
- **동일 사용자의 포인트 변동**은 **순차적으로(서비스 레이어에 도달한 순서대로) 처리**
- **서로 다른 사용자**의 포인트 변동은 **동시 처리** 가능 (사용자별로 **독립적인 락** 관리)


---

## **2. 동시성 제어 방식에 대한 분석**

`PointService`에서 **포인트 충전 및 사용 시,
사용자별 포인트 정보를 관리하는 UserPoint 객체가 공유 자원으로 동시 접근이 가능**하여 동시성 문제가 발생할 수 있습니다.  
예를 들어, 여러 요청이 동시에 처리될 경우 다음과 같은 문제가 발생할 수 있습니다:

### **예시 상황**
- **A 유저의 잔여 포인트**: 5,000
1. 첫 번째 요청: **포인트 3,000 사용**
2. 두 번째 요청: **포인트 2,000 사용**

### **정상 시나리오**
- 첫 번째 요청 처리 후: 잔여 포인트 = 2,000
- 두 번째 요청 처리 후: 잔여 포인트 = 0

### **문제 발생 시나리오**
- 두 요청이 동시에 잔여포인트(5,000)를 조회.
- 첫 번째 요청 처리 후 잔여 포인트가 2,000이 되었지만, 두 번째 요청이 여전히 **5,000으로 조회된 값**을 기준으로 계산.
- 결과적으로 잔여 포인트가 3,000이 되는 문제가 발생.

---

## **3. 동시성 제어 전략**

### **1. synchronized 키워드의 한계**
- **장점**: 한 번에 하나의 스레드만 접근하도록 제한 가능.
- **단점**: 모든 요청을 순차적으로 처리하므로, **다른 사용자**의 요청도 대기해야 함.
  - 예: A 유저의 포인트 처리가 끝나야 B 유저와 C 유저의 요청도 처리됨.
- 따라서, 모든 사용자에 대해 동시 처리의 유연성을 확보할 수 없으므로 제외.

### **2. ConcurrentHashMap과 ReentrantLock 활용**
- 사용자별로 **ReentrantLock**을 관리하여 동시성을 제어.
- **ConcurrentHashMap**은 여러 스레드에서 **동시에 안전하게 관리할 수 있는 Thread-safe 컬렉션**으로, 락을 효율적으로 저장하고 관리.
- 동일 사용자의 요청은 같은 **ReentrantLock**을 통해 순차적으로 처리하고, 서로 다른 사용자는 다른 **ReentrantLock**으로 동시에 처리 가능.

---

## **4. 동시성 제어 동작 원리**

1. **ConcurrentHashMap에서 사용자 **`userId`로 기존 락을 조회.**
2. **락이 없으면 `computeIfAbsent`를 사용해 새로운 락을 생성.**
3. **생성된 락은 `fair=true`로 설정**하여, **요청이 서비스 레이어에 도달한 시간을 기준으로** 스레드 대기 시간이 긴 순서대로 락을 획득하도록 보장

### **결과**
- **동일 사용자의 요청:** 동일한 `ReentrantLock`으로 순차 처리.
- **서로 다른 사용자의 요청:** 서로 다른 `ReentrantLock`으로 동시 처리 가능.

---

## **5. 동시성 제어 처리 로직**

```java
public UserPoint charge(long userId, long chargeAmount) {
    ReentrantLock lock = lockManager.getLock(userId);  // 1. 락 획득 시도
    lock.lock();
    try {
        UserPoint userPoint = selectById(userId);      // 2. 포인트 조회
        // ... 포인트 처리 로직
    } finally {
        lock.unlock();                                 // 3. 락 해제
    }
}
```
### **세부 동작 설명**
#### 1.	락 획득 시도
- lockManager.getLock(userId)를 통해 사용자별 락을 가져오고, 락을 획득.
- 락이 이미 다른 스레드에 의해 사용 중이라면, 대기열에 들어가 순서를 기다림.
#### 2.	포인트 처리
- 사용자 포인트 정보를 조회한 뒤, 비즈니스 로직(충전 또는 사용)을 처리.
#### 3.	락 해제
- 작업이 완료되면 락을 해제하여 다음 스레드가 작업을 수행할 수 있도록 함.
