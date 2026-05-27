package com.example.pocussharing.model;

import com.google.firebase.Timestamp;

/**
 * DailyStats: 사용자의 일일 집중 및 휴식 통계 데이터를 담는 모델 클래스
 */
public class DailyStats {
    private String userId;        // 사용자 고유 ID
    private String targetDate;    // 대상 날짜 (포맷: YYYY-MM-DD)
    private int totalFocusSec;    // 총 집중 시간 (초 단위)
    private int totalRestSec;     // 총 휴식 시간 (초 단위)
    private Timestamp updatedAt;  // 마지막 업데이트 시간

    // Firebase 연동을 위한 기본 생성자
    public DailyStats() {}

    /**
     * 새로운 일일 통계 객체를 초기값과 함께 생성합니다.
     * @param userId 사용자 ID
     * @param targetDate 통계 날짜
     */
    public DailyStats(String userId, String targetDate) {
        this.userId = userId;
        this.targetDate = targetDate;
        this.totalFocusSec = 0;
        this.totalRestSec = 0;
        this.updatedAt = Timestamp.now();
    }

    // Getter 및 Setter 메서드들
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