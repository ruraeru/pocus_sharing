package com.example.pocussharing.repository;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import java.util.HashMap;
import java.util.Map;

/**
 * RtdbRepository: Firebase Realtime Database(RTDB)와의 데이터 통신을 담당하는 레포지토리 클래스
 * 
 * 주요 역할:
 * 1. 실시간 타이머 상태(모드, 남은 시간) 공유
 * 2. 그룹 내 멤버들의 활성화 상태 실시간 동기화
 */
public class RtdbRepository {
    // 실시간 DB의 특정 노드 경로를 가리키는 참조 객체
    private final DatabaseReference ref;

    public RtdbRepository() {
        // [초기화 블록]
        // 실시간 상태 공유를 위한 'group_presence' 노드 참조 초기화함
        // URL을 명시적으로 지정하여 인스턴스를 가져옴
        this.ref = FirebaseDatabase.getInstance("https://pocus-sharing-2026-default-rtdb.firebaseio.com")
                .getReference("group_presence");
    }

    /**
     * 사용자의 실시간 상태(집중 여부, 타이머 시간 등)를 업데이트함
     * 
     * [주요 로직]
     * 사용자가 타이머를 조작하거나 1초씩 줄어들 때마다 이 함수가 호출되어 
     * DB의 값을 갱신하고, 이를 지켜보는 다른 멤버들의 화면이 즉시 바뀜
     * 
     * @param groupId 현재 속한 그룹 ID
     * @param userId 내 UID
     * @param name 표시될 닉네임
     * @param isFocus 현재 집중 모드인지 여부
     * @param timeLeftMillis 타이머의 남은 밀리초
     * @param todayFocusTimeMillis 오늘 총 집중한 밀리초
     */
    public void updateUserStatus(String groupId, String userId, String name, boolean isFocus, long timeLeftMillis, long todayFocusTimeMillis) {
        if (groupId == null || userId == null) return;

        // 저장할 상태 데이터를 Map 형태로 구성함
        Map<String, Object> status = new HashMap<>();
        status.put("userId", userId);
        status.put("name", name);
        status.put("isFocus", isFocus);
        status.put("timeLeft", timeLeftMillis);
        status.put("todayFocusTime", todayFocusTimeMillis);
        status.put("timestamp", System.currentTimeMillis()); // 동기화 시간 기록

        // [중요 구문] child(groupId).child(userId)
        // 그룹별로 노드를 나누고, 그 안에 사용자별로 데이터를 배치하여 충돌을 방지함
        ref.child(groupId).child(userId).setValue(status);
    }

    /**
     * 사용자가 그룹을 나가거나 앱을 종료할 때 실시간 상태 데이터를 삭제함
     * 리스트에서 내 정보를 즉시 지워 '오프라인' 상태를 구현함
     */
    public void removeUserStatus(String groupId, String userId) {
        if (groupId == null || userId == null) return;
        ref.child(groupId).child(userId).removeValue();
    }
    
    /**
     * 특정 그룹의 전체 멤버 실시간 상태에 대한 참조를 반환함
     * GroupDetailActivity에서 이 참조를 구독(Listen)하여 실시간 랭킹을 그림
     */
    public DatabaseReference getGroupPresenceRef(String groupId) {
        return ref.child(groupId);
    }

    /**
     * 그룹이 삭제될 때 해당 그룹의 모든 실시간 상태 데이터를 제거함
     */
    public void deleteGroupPresence(String groupId) {
        ref.child(groupId).removeValue();
    }
}
