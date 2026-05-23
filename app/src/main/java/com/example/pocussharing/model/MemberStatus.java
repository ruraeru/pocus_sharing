package com.example.pocussharing.model;

public class MemberStatus {
    private String userId;
    private String name;
    private boolean isFocus;
    private long timeLeft;
    private long todayFocusTime;
    private long timestamp;

    public MemberStatus() {}

    public MemberStatus(String userId, String name, boolean isFocus, long timeLeft, long todayFocusTime, long timestamp) {
        this.userId = userId;
        this.name = name;
        this.isFocus = isFocus;
        this.timeLeft = timeLeft;
        this.todayFocusTime = todayFocusTime;
        this.timestamp = timestamp;
    }

    public String getUserId() { return userId; }
    public String getName() { return name; }
    public boolean isFocus() { return isFocus; }
    public long getTimeLeft() { return timeLeft; }
    public long getTodayFocusTime() { return todayFocusTime; }
    public long getTimestamp() { return timestamp; }
}