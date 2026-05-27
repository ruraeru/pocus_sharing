package com.example.pocussharing.repository;

import com.google.android.gms.tasks.Task;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import java.util.HashMap;
import java.util.Map;

/**
 * RtdbRepository: Firebase Realtime Database(RTDB)와의 데이터 통신을 담당하는 레포지토리 클래스
 */
public class RtdbRepository {
    private DatabaseReference presenceRef;

    public RtdbRepository() {
        // 실시간 상태 공유를 위한 group_presence 노드 참조 초기화
        this.presenceRef = FirebaseDatabase.getInstance("https://pocus-sharing-2026-default-rtdb.firebaseio.com")
                .getReference("group_presence");
    }

    /**
     * 사용자의 실시간 상태(집중 여부, 타이머 시간 등)를 업데이트합니다.
     */
    public void updateUserStatus(String groupId, String userId, String name, boolean isFocus, long timeLeftMillis, long todayFocusTimeMillis) {
        if (groupId == null || userId == null) return;

        Map<String, Object> status = new HashMap<>();
        status.put("userId", userId);
        status.put("name", name);
        status.put("isFocus", isFocus);
        status.put("timeLeft", timeLeftMillis);
        status.put("todayFocusTime", todayFocusTimeMillis);
        status.put("timestamp", System.currentTimeMillis());

        // 해당 그룹 내 해당 사용자의 데이터를 갱신
        presenceRef.child(groupId).child(userId).setValue(status);
    }

    /**
     * 사용자가 그룹을 나가거나 앱을 종료할 때 실시간 상태 데이터를 삭제합니다.
     */
    public void removeUserStatus(String groupId, String userId) {
        if (groupId == null || userId == null) return;
        presenceRef.child(groupId).child(userId).removeValue();
    }
    
    /**
     * 특정 그룹의 전체 멤버 실시간 상태에 대한 참조를 반환합니다.
     */
    public DatabaseReference getGroupPresenceRef(String groupId) {
        return presenceRef.child(groupId);
    }

    /**
     * 그룹이 삭제될 때 해당 그룹의 모든 실시간 상태 데이터를 제거합니다.
     */
    public Task<Void> deleteGroupPresence(String groupId) {
        return presenceRef.child(groupId).removeValue();
    }
}