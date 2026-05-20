package com.example.pocussharing.model;

import com.google.firebase.Timestamp;

public class DailyStats {
    private String userId;
    private String targetDate; // YYYY-MM-DD
    private int totalFocusSec;
    private int totalRestSec;
    private Timestamp updatedAt;

    public DailyStats() {}

    public DailyStats(String userId, String targetDate) {
        this.userId = userId;
        this.targetDate = targetDate;
        this.totalFocusSec = 0;
        this.totalRestSec = 0;
        this.updatedAt = Timestamp.now();
    }

    // Getters and Setters
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getTargetDate() { return targetDate; }
    public void setTargetDate(String targetDate) { this.targetDate = targetDate; }
    public int getTotalFocusSec() { return totalFocusSec; }
    public void setTotalFocusSec(int totalFocusSec) { this.totalFocusSec = totalFocusSec; }
    public int getTotalRestSec() { return totalRestSec; }
    public void setTotalRestSec(int totalRestSec) { this.totalRestSec = totalRestSec; }
    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
}