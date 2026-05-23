package com.example.pocussharing.model;

import com.google.firebase.Timestamp;

public class TimerLog {
    private String logId;
    private String user_id;
    private String groupId; // Can be null
    private String logType; // FOCUS, REST
    private int durationSeconds;
    private Timestamp startTime;
    private Timestamp endTime;
    private Timestamp createdAt;

    public TimerLog() {}

    public TimerLog(String user_id, String logType, int durationSeconds, Timestamp startTime, Timestamp endTime) {
        this.user_id = user_id;
        this.logType = logType;
        this.durationSeconds = durationSeconds;
        this.startTime = startTime;
        this.endTime = endTime;
        this.createdAt = Timestamp.now();
    }

    // Getters and Setters
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