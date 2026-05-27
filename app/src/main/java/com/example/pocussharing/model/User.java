package com.example.pocussharing.model;

import com.google.firebase.Timestamp;
import java.util.HashMap;
import java.util.Map;

/**
 * User: 앱 사용자의 프로필 및 설정 정보를 담는 모델 클래스
 */
public class User {
    private String userId;           // Firebase UID
    private String kakaoId;          // 카카오 고유 식별자
    private String email;            // 사용자 이메일
    private String nickname;         // 사용자 닉네임
    private String profileImageUrl;  // 프로필 이미지 URL
    private String currentStatus;    // 현재 실시간 상태 (OFFLINE, FOCUS, REST)
    private Timestamp createdAt;     // 계정 생성 일시
    private UserSettings settings;   // 사용자 개별 설정

    // Firebase 연동을 위한 기본 생성자
    public User() {}

    /**
     * 새로운 사용자 객체를 기본값과 함께 생성합니다.
     * @param userId Firebase UID
     * @param kakaoId 카카오 ID
     * @param nickname 닉네임
     */
    public User(String userId, String kakaoId, String nickname) {
        this.userId = userId;
        this.kakaoId = kakaoId;
        this.nickname = nickname;
        this.currentStatus = "OFFLINE";
        this.createdAt = Timestamp.now();
        this.settings = new UserSettings();
    }

    // Getter 및 Setter 메서드들
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getKakaoId() { return kakaoId; }
    public void setKakaoId(String kakaoId) { this.kakaoId = kakaoId; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getProfileImageUrl() { return profileImageUrl; }
    public void setProfileImageUrl(String profileImageUrl) { this.profileImageUrl = profileImageUrl; }
    public String getCurrentStatus() { return currentStatus; }
    public void setCurrentStatus(String currentStatus) { this.currentStatus = currentStatus; }
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
    public UserSettings getSettings() { return settings; }
    public void setSettings(UserSettings settings) { this.settings = settings; }

    /**
     * UserSettings: 알람, 화면 유지 등 사용자의 앱 설정 정보를 담는 내부 클래스
     */
    public static class UserSettings {
        private boolean muteAlarms = false;    // 알람 무음 여부
        private boolean keepScreenOn = true;   // 화면 켜짐 유지 여부
        private boolean preventExit = false;    // 앱 종료 방지 모드 활성화 여부
        private Timestamp updatedAt;           // 마지막 설정 변경 일시

        public UserSettings() {
            this.updatedAt = Timestamp.now();
        }

        public boolean isMuteAlarms() { return muteAlarms; }
        public void setMuteAlarms(boolean muteAlarms) { this.muteAlarms = muteAlarms; }
        public boolean isKeepScreenOn() { return keepScreenOn; }
        public void setKeepScreenOn(boolean keepScreenOn) { this.keepScreenOn = keepScreenOn; }
        public boolean isPreventExit() { return preventExit; }
        public void setPreventExit(boolean preventExit) { this.preventExit = preventExit; }
        public Timestamp getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
    }
}