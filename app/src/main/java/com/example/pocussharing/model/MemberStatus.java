package com.example.pocussharing.model;

import com.google.firebase.database.PropertyName;

/**
 * MemberStatus: Realtime Database에서 그룹 멤버들의 실시간 상태(집중 여부, 남은 시간 등)를 공유하기 위한 DTO
 */
public class MemberStatus {
    private String userId;         // 사용자 고유 ID
    private String name;           // 사용자 이름(닉네임)

    @PropertyName("isFocus")
    private boolean isFocus;       // 현재 집중 모드 여부 (true: 집중, false: 휴식)

    private long timeLeft;         // 타이머 남은 시간 (밀리초)
    private long todayFocusTime;   // 오늘 총 집중한 누적 시간 (밀리초)
    private long timestamp;        // 상태가 업데이트된 시각 (서버 시간 기준)

    // Firebase 연동을 위한 기본 생성자
    public MemberStatus() {}

    /**
     * 실시간 상태 객체를 생성합니다.
     */
    public MemberStatus(String userId, String name, boolean isFocus, long timeLeft, long todayFocusTime, long timestamp) {
        this.userId = userId;
        this.name = name;
        this.isFocus = isFocus;
        this.timeLeft = timeLeft;
        this.todayFocusTime = todayFocusTime;
        this.timestamp = timestamp;
    }

    // Getter 메서드들
    public String getUserId() { return userId; }
    public String getName() { return name; }

    @PropertyName("isFocus")
    public boolean isFocus() { return isFocus; }

    @PropertyName("isFocus")
    public void setFocus(boolean focus) { isFocus = focus; }

    public long getTimeLeft() { return timeLeft; }
    public long getTodayFocusTime() { return todayFocusTime; }
    public long getTimestamp() { return timestamp; }
}