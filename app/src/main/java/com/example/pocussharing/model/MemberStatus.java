package com.example.pocussharing.model;

/**
 * MemberStatus: Realtime Database에서 그룹 멤버들의 실시간 상태(집중 여부, 남은 시간 등)를 공유하기 위한 DTO
 */
public class MemberStatus {
    private String userId;
    private String name;
    private boolean isFocus;
    private long timeLeft;
    private long todayFocusTime;
    private long timestamp;

    // Firebase 연동을 위한 기본 생성자
    public MemberStatus() {}

    // Getter 및 Setter 메서드들
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public boolean isFocus() { return isFocus; }
    public void setFocus(boolean focus) { isFocus = focus; }
    public long getTimeLeft() { return timeLeft; }
    public void setTimeLeft(long timeLeft) { this.timeLeft = timeLeft; }
    public long getTodayFocusTime() { return todayFocusTime; }
    public void setTodayFocusTime(long todayFocusTime) { this.todayFocusTime = todayFocusTime; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}
