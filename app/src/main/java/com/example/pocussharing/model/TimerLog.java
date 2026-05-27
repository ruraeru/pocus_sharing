package com.example.pocussharing.model;

import com.google.firebase.Timestamp;

/**
 * TimerLog: 개별 집중 또는 휴식 세션의 기록을 담는 모델 클래스
 */
public class TimerLog {
    private String logId;           // 로그 고유 ID (Firestore 문서 ID)
    private String user_id;         // 사용자 ID (Firestore 필드명 user_id와 매칭)
    private String groupId;         // 세션이 진행된 그룹 ID (있을 경우)
    private String logType;         // 로그 유형 (FOCUS: 집중, REST: 휴식)
    private int durationSeconds;    // 세션 지속 시간 (초 단위)
    private Timestamp startTime;    // 세션 시작 일시
    private Timestamp endTime;      // 세션 종료 일시
    private Timestamp createdAt;    // 로그 생성 일시

    // Firebase 연동을 위한 기본 생성자
    public TimerLog() {}

    /**
     * 새로운 타이머 로그 객체를 생성합니다.
     * @param user_id 사용자 ID
     * @param logType 세션 유형
     * @param durationSeconds 지속 시간
     * @param startTime 시작 시간
     * @param endTime 종료 시간
     */
    public TimerLog(String user_id, String logType, int durationSeconds, Timestamp startTime, Timestamp endTime) {
        this.user_id = user_id;
        this.logType = logType;
        this.durationSeconds = durationSeconds;
        this.startTime = startTime;
        this.endTime = endTime;
        this.createdAt = Timestamp.now();
    }

    // Getter 및 Setter 메서드들
    public String getLogId() { return logId; }
    public void setLogId(String logId) { this.logId = logId; }
    public String getUser_id() { return user_id; }
    public void setUser_id(String user_id) { this.user_id = user_id; }
    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }
    public String getLogType() { return logType; }
    public void setLogType(String logType) { this.logType = logType; }
    public int getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(int durationSeconds) { this.durationSeconds = durationSeconds; }
    public Timestamp getStartTime() { return startTime; }
    public void setStartTime(Timestamp startTime) { this.startTime = startTime; }
    public Timestamp getEndTime() { return endTime; }
    public void setEndTime(Timestamp endTime) { this.endTime = endTime; }
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}