/**
 * FirestoreRepository.java
 * Firebase Firestore와의 모든 데이터 통신을 담당하는 리포지토리 클래스입니다.
 * 사용자 프로필 관리, 그룹 생성/참가/관리, 타이머 로그 기록 및 통계 조회를 수행합니다.
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
    private FirebaseFirestore db;
    private static final String USERS_COLLECTION = "users";          // 사용자 컬렉션 이름
    private static final String GROUPS_COLLECTION = "groups";        // 그룹 컬렉션 이름
    private static final String TIMER_LOGS_COLLECTION = "timer_logs"; // 타이머 로그 컬렉션 이름

    public FirestoreRepository() {
        this.db = FirebaseFirestore.getInstance();
    }

    // --- 사용자 관련 작업 (User Operations) ---

    /**
     * 사용자 정보를 Firestore에 저장하거나 업데이트합니다.
     */
    public Task<Void> saveUser(User user) {
        return db.collection(USERS_COLLECTION).document(user.getUserId()).set(user);
    }

    /**
     * 특정 사용자의 문서를 가져옵니다.
     */
    public Task<DocumentSnapshot> getUser(String userId) {
        return db.collection(USERS_COLLECTION).document(userId).get();
    }

    // --- 그룹 관련 작업 (Group Operations) ---

    /**
     * 새로운 그룹을 생성합니다. 그룹 문서 생성과 동시에 관리자를 멤버 서브컬렉션에 추가합니다.
     */
    public Task<DocumentReference> createGroup(Group group) {
        DocumentReference groupRef = db.collection(GROUPS_COLLECTION).document();
        String groupId = groupRef.getId();
        group.setGroupId(groupId);

        GroupMember admin = new GroupMember(group.getAdminId(), "ADMIN");
        WriteBatch batch = db.batch();
        batch.set(groupRef, group);
        batch.set(groupRef.collection("members").document(group.getAdminId()), admin);

        return batch.commit().continueWith(task -> groupRef);
    }

    /**
     * 기존 그룹에 사용자를 멤버로 추가합니다.
     */
    public Task<Void> joinGroup(String groupId, String userId) {
        GroupMember member = new GroupMember(userId, "MEMBER");
        WriteBatch batch = db.batch();
        
        DocumentReference groupRef = db.collection(GROUPS_COLLECTION).document(groupId);
        batch.set(groupRef.collection("members").document(userId), member);
        batch.update(groupRef, "memberIds", FieldValue.arrayUnion(userId)); // 멤버 ID 배열에 추가
        
        return batch.commit();
    }

    /**
     * 사용자가 속한 모든 그룹의 목록을 가져옵니다.
     */
    public Task<QuerySnapshot> getUserGroups(String userId) {
        return db.collection(GROUPS_COLLECTION)
                .whereArrayContains("memberIds", userId)
                .get();
    }

    /**
     * 사용자가 속한 그룹 목록의 실시간 변경 사항을 감시하는 리스너를 등록합니다.
     */
    public ListenerRegistration getUserGroupsListener(String userId, EventListener<QuerySnapshot> listener) {
        return db.collection(GROUPS_COLLECTION)
                .whereArrayContains("memberIds", userId)
                .addSnapshotListener(listener);
    }

    /**
     * 6자리 초대 코드를 사용하여 그룹을 검색합니다.
     */
    public Task<QuerySnapshot> findGroupByCode(String code) {
        return db.collection(GROUPS_COLLECTION)
                .whereEqualTo("groupCode", code)
                .limit(1)
                .get();
    }

    /**
     * 사용자가 그룹에서 나갈 때 멤버 정보를 삭제하고 ID 배열에서 제거합니다.
     */
    public Task<Void> leaveGroup(String groupId, String userId) {
        WriteBatch batch = db.batch();
        DocumentReference groupRef = db.collection(GROUPS_COLLECTION).document(groupId);
        batch.delete(groupRef.collection("members").document(userId));
        batch.update(groupRef, "memberIds", FieldValue.arrayRemove(userId));
        return batch.commit();
    }

    /**
     * 그룹 정보를 업데이트합니다. (이름, 최대 인원 등)
     */
    public Task<Void> updateGroup(String groupId, Map<String, Object> updates) {
        return db.collection(GROUPS_COLLECTION).document(groupId).update(updates);
    }

    /**
     * 그룹을 완전히 삭제합니다. 멤버 서브컬렉션을 먼저 비우고 그룹 문서를 삭제합니다.
     */
    public Task<Void> deleteGroup(String groupId) {
        DocumentReference groupRef = db.collection(GROUPS_COLLECTION).document(groupId);
        
        return groupRef.collection("members").get().continueWithTask(task -> {
            WriteBatch batch = db.batch();
            if (task.isSuccessful() && task.getResult() != null) {
                for (DocumentSnapshot doc : task.getResult()) {
                    batch.delete(doc.getReference());
                }
            }
            batch.delete(groupRef);
            return batch.commit();
        });
    }

    /**
     * 특정 그룹의 상세 정보를 가져옵니다.
     */
    public Task<DocumentSnapshot> getGroup(String groupId) {
        return db.collection(GROUPS_COLLECTION).document(groupId).get();
    }

    // --- 타이머 로그 및 통계 작업 (Timer Log & Stats Operations) ---

    /**
     * 타이머 세션 완료 기록을 저장하고, 사용자의 일일 통계 및 그룹 통계를 업데이트합니다.
     */
    public Task<Void> saveTimerLog(TimerLog log) {
        DocumentReference logRef = db.collection(TIMER_LOGS_COLLECTION).document();
        log.setLogId(logRef.getId());
        
        WriteBatch batch = db.batch();
        batch.set(logRef, log);

        // 오늘 날짜 문자열 생성 (yyyy-MM-dd)
        String dateStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        
        // 사용자의 일일 통계 문서 참조
        DocumentReference userStatRef = db.collection(USERS_COLLECTION).document(log.getUser_id())
            .collection("daily_stats").document(dateStr);

        int focusInc = log.getLogType().equals("FOCUS") ? log.getDurationSeconds() : 0;
        int restInc = log.getLogType().equals("REST") ? log.getDurationSeconds() : 0;

        // 원자적 연산(increment)을 사용하여 총 집중/휴식 시간 누적
        batch.set(userStatRef, new HashMap<String, Object>() {{
            put("totalFocusSec", FieldValue.increment(focusInc));
            put("totalRestSec", FieldValue.increment(restInc));
            put("updatedAt", FieldValue.serverTimestamp());
        }}, SetOptions.merge());

        // 특정 그룹 내에서의 통계도 업데이트 (그룹 ID가 있는 경우)
        if (log.getGroupId() != null) {
            DocumentReference groupStatRef = db.collection(GROUPS_COLLECTION).document(log.getGroupId())
                .collection("daily_stats").document(dateStr)
                .collection("user_stats").document(log.getUser_id());
            
            batch.set(groupStatRef, new HashMap<String, Object>() {{
                put("focusSeconds", FieldValue.increment(focusInc));
                put("updatedAt", FieldValue.serverTimestamp());
            }}, SetOptions.merge());
        }

        return batch.commit();
    }

    /**
     * 특정 사용자의 오늘 총 집중 시간(초)을 가져옵니다.
     */
    public Task<Integer> getDailyFocusTime(String userId) {
        String dateStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        return db.collection(USERS_COLLECTION).document(userId)
                .collection("daily_stats").document(dateStr)
                .get()
                .continueWith(task -> {
                    if (task.isSuccessful() && task.getResult() != null && task.getResult().exists()) {
                        Long focusSec = task.getResult().getLong("totalFocusSec");
                        return focusSec != null ? focusSec.intValue() : 0;
                    }
                    return 0;
                });
    }

    /**
     * 사용자의 최근 타이머 로그 목록을 가져옵니다.
     */
    public Task<QuerySnapshot> getRecentTimerLogs(String userId) {
        return db.collection(TIMER_LOGS_COLLECTION)
                .whereEqualTo("user_id", userId)
                .get();
    }

    /**
     * 사용자의 타이머 로그 실시간 감시 리스너를 등록합니다.
     */
    public ListenerRegistration getTimerLogsListener(String userId, EventListener<QuerySnapshot> listener) {
        return db.collection(TIMER_LOGS_COLLECTION)
                .whereEqualTo("user_id", userId)
                .addSnapshotListener(listener);
    }
}
