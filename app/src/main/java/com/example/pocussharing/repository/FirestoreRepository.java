/**
 * FirestoreRepository.java
 * Firebase Firestore와의 모든 데이터 통신을 담당하는 리포지토리 클래스
 * 
 * 주요 기능:
 * 1. 사용자 프로필(User) 관리 (생성, 조회)
 * 2. 그룹(Group) 데이터 관리 (생성, 참여, 탈퇴, 삭제)
 * 3. 타이머 로그(TimerLog) 저장 및 실시간 리스너 등록
 * 4. 일일 통계(Daily Stats) 업데이트 및 조회
 */
package com.example.pocussharing.repository;

import com.example.pocussharing.model.*;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class FirestoreRepository {
    // Firestore DB 접근을 위한 최상위 인스턴스 객체
    private FirebaseFirestore db;

    // [코드 컨벤션] DB 컬렉션 이름들의 오타를 방지하고 유지보수성을 높이기 위해 상수로 관리
    private static final String USERS_COLLECTION = "users";          
    private static final String GROUPS_COLLECTION = "groups";        
    private static final String TIMER_LOGS_COLLECTION = "timer_logs"; 

    /**
     * 생성자
     * Repository가 생성될 때 Firestore 인스턴스를 초기화하여 언제든 DB에 접근할 수 있게 준비
     */
    public FirestoreRepository() {
        this.db = FirebaseFirestore.getInstance();
    }

    /* =================================================================================
     * [사용자(User) 관련 데이터베이스 작업 블록]
     * ================================================================================= */

    /**
     * 사용자 정보를 Firestore에 저장하거나 업데이트함
     * 
     * [중요 구문]
     * .document(user.getUserId()).set(user)
     * -> 랜덤한 문서 ID를 발급받지 않고, Firebase Auth에서 부여받은 고유 식별자(UID)를 
     *    문서의 ID로 강제 지정. 이렇게 하면 나중에 데이터를 찾을 때 매우 직관적임.
     */
    public Task<Void> saveUser(User user) {
        return db.collection(USERS_COLLECTION).document(user.getUserId()).set(user);
    }

    /**
     * 특정 사용자의 문서를 가져옴 (단일 읽기 연산)
     */
    public Task<DocumentSnapshot> getUser(String userId) {
        return db.collection(USERS_COLLECTION).document(userId).get();
    }

    /* =================================================================================
     * [그룹(Group) 관련 데이터베이스 작업 블록]
     * ================================================================================= */

    /**
     * 새로운 그룹을 생성함
     * 
     * [주요 로직: WriteBatch]
     * 그룹을 만들 때는 1) 그룹 자체의 정보(메타데이터)를 저장하고 2) 그 그룹의 하위 컬렉션(members)에 
     * 방장을 등록해야 함. 둘 중 하나만 성공하면 데이터가 꼬이게 되므로, WriteBatch를 사용하여 
     * '두 작업이 모두 성공해야만 DB에 반영(commit)'되도록 원자성을 보장함.
     */
    public Task<DocumentReference> createGroup(Group group) {
        // 1. 저장할 위치의 빈 문서(Reference)를 먼저 확보하여 자동 생성된 ID를 얻음
        DocumentReference groupRef = db.collection(GROUPS_COLLECTION).document();
        String groupId = groupRef.getId();
        group.setGroupId(groupId); // 생성된 랜덤 ID를 그룹 모델에 세팅

        // 2. 방장(ADMIN) 권한을 가진 멤버 객체 생성
        GroupMember admin = new GroupMember(group.getAdminId(), "ADMIN");
        
        // 3. WriteBatch(일괄 쓰기) 트랜잭션 준비
        WriteBatch batch = db.batch();
        batch.set(groupRef, group); // (1) 상위 문서: 그룹 정보 저장
        batch.set(groupRef.collection("members").document(group.getAdminId()), admin); // (2) 하위 문서: 멤버 저장

        // 4. commit()을 호출하여 두 작업을 동시에 DB에 반영
        return batch.commit().continueWith(task -> groupRef);
    }

    /**
     * 기존 그룹에 사용자를 일반 멤버(MEMBER)로 추가함
     * 
     * [중요 구문: FieldValue.arrayUnion]
     * 다수의 사람이 동시에 가입 버튼을 누를 때 데이터 덮어쓰기를 방지하기 위해 
     * 기존 배열에 안전하게 원소를 추가하는 arrayUnion 연산자를 사용함. 중복 요소가 들어가도 무시됨.
     */
    public Task<Void> joinGroup(String groupId, String userId) {
        GroupMember member = new GroupMember(userId, "MEMBER");
        WriteBatch batch = db.batch();
        
        DocumentReference groupRef = db.collection(GROUPS_COLLECTION).document(groupId);
        batch.set(groupRef.collection("members").document(userId), member); // 하위 컬렉션에 멤버 기록
        batch.update(groupRef, "memberIds", FieldValue.arrayUnion(userId)); // 멤버 ID를 배열에 안전하게 병합
        
        return batch.commit();
    }

    /**
     * 사용자가 속한 모든 그룹의 목록을 찾음
     * [중요 구문: whereArrayContains]
     * Firestore의 강력한 배열 검색 쿼리로, memberIds라는 배열 안에 userId가 하나라도 포함되어 있는 
     * 모든 그룹 문서를 한 번에 찾아줌.
     */
    public Task<QuerySnapshot> getUserGroups(String userId) {
        return db.collection(GROUPS_COLLECTION)
                .whereArrayContains("memberIds", userId)
                .get();
    }

    /**
     * 사용자가 속한 그룹 목록의 '실시간' 변경 사항을 감시함
     * 다른 사람이 그룹 이름을 바꾸거나 내가 새로운 그룹에 초대받았을 때 화면을 새로고침하지 않아도 
     * 리스너가 콜백을 발생시켜 즉각 반영하게 해줌.
     */
    public ListenerRegistration getUserGroupsListener(String userId, EventListener<QuerySnapshot> listener) {
        return db.collection(GROUPS_COLLECTION)
                .whereArrayContains("memberIds", userId)
                .addSnapshotListener(listener);
    }

    /**
     * 6자리 초대 코드를 사용하여 그룹을 검색함
     */
    public Task<QuerySnapshot> findGroupByCode(String code) {
        return db.collection(GROUPS_COLLECTION)
                .whereEqualTo("groupCode", code)
                .limit(1)
                .get();
    }

    /**
     * 사용자가 그룹에서 나갈 때(탈퇴) 멤버 정보를 삭제함
     * [중요 구문: FieldValue.arrayRemove]
     * arrayUnion의 반대 기능으로, memberIds 배열에서 특정 요소(userId)만 쏙 빼줌.
     */
    public Task<Void> leaveGroup(String groupId, String userId) {
        WriteBatch batch = db.batch();
        DocumentReference groupRef = db.collection(GROUPS_COLLECTION).document(groupId);
        batch.delete(groupRef.collection("members").document(userId)); // 서브컬렉션에서 멤버 삭제
        batch.update(groupRef, "memberIds", FieldValue.arrayRemove(userId)); // 배열에서 UID 삭제
        return batch.commit();
    }

    /**
     * 그룹 정보를 업데이트함 (이름, 최대 인원 등)
     */
    public Task<Void> updateGroup(String groupId, Map<String, Object> updates) {
        return db.collection(GROUPS_COLLECTION).document(groupId).update(updates);
    }

    /**
     * 그룹을 완전히 삭제함
     * [주요 로직: 서브컬렉션 선행 삭제]
     * Firestore는 상위 그룹 문서를 지운다고 해서 하위의 멤버(members) 문서들이 자동으로 삭제되지 않음.
     * 따라서 먼저 하위 문서들을 모두 가져와 삭제 뱃치에 담은 뒤, 마지막에 상위 문서를 지우는 패턴을 사용함.
     */
    public Task<Void> deleteGroup(String groupId) {
        DocumentReference groupRef = db.collection(GROUPS_COLLECTION).document(groupId);
        
        return groupRef.collection("members").get().continueWithTask(task -> {
            WriteBatch batch = db.batch();
            if (task.isSuccessful() && task.getResult() != null) {
                // 하위 문서(멤버들) 순회하며 삭제 등록
                for (DocumentSnapshot doc : task.getResult()) {
                    batch.delete(doc.getReference());
                }
            }
            // 최상위 문서(그룹) 삭제 등록
            batch.delete(groupRef);
            return batch.commit();
        });
    }

    /**
     * 특정 그룹의 상세 메타데이터 정보를 가져옴
     */
    public Task<DocumentSnapshot> getGroup(String groupId) {
        return db.collection(GROUPS_COLLECTION).document(groupId).get();
    }

    /* =================================================================================
     * [타이머 로그(TimerLog) 및 일일 통계(DailyStats) 작업 블록]
     * ================================================================================= */

    /**
     * 타이머 세션 완료 기록을 저장하고, 이와 동시에 '오늘의 집중 시간'을 누적함
     * [핵심 로직]
     * 매우 빈번하게 호출되고 덧셈 연산이 필요한 핵심 구간이므로 트랜잭션과 Atomic 연산을 조합함.
     */
    public Task<Void> saveTimerLog(TimerLog log) {
        DocumentReference logRef = db.collection(TIMER_LOGS_COLLECTION).document();
        log.setLogId(logRef.getId());
        
        WriteBatch batch = db.batch();
        batch.set(logRef, log); // 로그 기록을 뱃치에 담음

        // YYYY-MM-DD 형식의 현재 날짜 문자열 생성 (이 날짜 자체가 통계 문서의 ID 역할을 함)
        String dateStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        
        // 내 개인 통계 문서 참조 생성
        DocumentReference userStatRef = db.collection(USERS_COLLECTION).document(log.getUser_id())
            .collection("daily_stats").document(dateStr);

        int focusInc = log.getLogType().equals("FOCUS") ? log.getDurationSeconds() : 0;
        int restInc = log.getLogType().equals("REST") ? log.getDurationSeconds() : 0;

        /**
         * [중요 구문: FieldValue.increment 및 SetOptions.merge]
         * 1. increment(증가분): 기존 서버 데이터 값에 즉시 안전하게 값을 더해줌. 
         *    여러 사용자가 동시에 접속해도 데이터 레이스 컨디션(동기화 오류)이 발생하지 않음.
         * 2. merge(): 문서가 존재하지 않으면 새로 생성하고, 있으면 해당 필드만 병합(덮어쓰기)함.
         */
        batch.set(userStatRef, new HashMap<String, Object>() {{
            put("totalFocusSec", FieldValue.increment(focusInc)); // 기존 값 + 방금 집중한 초
            put("totalRestSec", FieldValue.increment(restInc));   // 기존 값 + 방금 휴식한 초
            put("updatedAt", FieldValue.serverTimestamp());       // 클라이언트 기기 시간이 아닌 정확한 서버 시간 삽입
        }}, SetOptions.merge());

        // 특정 그룹 내에서 진행된 타이머라면 그룹 통계(랭킹)용 데이터도 동일하게 누적시킴
        if (log.getGroupId() != null) {
            DocumentReference groupStatRef = db.collection(GROUPS_COLLECTION).document(log.getGroupId())
                .collection("daily_stats").document(dateStr)
                .collection("user_stats").document(log.getUser_id());
            
            batch.set(groupStatRef, new HashMap<String, Object>() {{
                put("focusSeconds", FieldValue.increment(focusInc));
                put("updatedAt", FieldValue.serverTimestamp());
            }}, SetOptions.merge());
        }

        // 최종적으로 준비된 쓰기 작업을 모두 DB에 적용
        return batch.commit();
    }

    /**
     * 특정 사용자의 오늘 총 집중 시간(초)을 가져옴
     * 홈 화면 등에 오늘 공부한 총 시간을 띄우기 위해 호출됨.
     */
    public Task<Integer> getDailyFocusTime(String userId) {
        String dateStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        return db.collection(USERS_COLLECTION).document(userId)
                .collection("daily_stats").document(dateStr)
                .get()
                .continueWith(task -> {
                    // 문서가 존재할 경우에만 캐스팅하여 리턴, 아니면 0 리턴
                    if (task.isSuccessful() && task.getResult() != null && task.getResult().exists()) {
                        Long focusSec = task.getResult().getLong("totalFocusSec");
                        return focusSec != null ? focusSec.intValue() : 0;
                    }
                    return 0;
                });
    }

    /**
     * 사용자의 타이머 로그 실시간 감시 리스너를 등록함
     */
    public ListenerRegistration getTimerLogsListener(String userId, EventListener<QuerySnapshot> listener) {
        return db.collection(TIMER_LOGS_COLLECTION)
                .whereEqualTo("user_id", userId)
                .addSnapshotListener(listener);
    }
}
