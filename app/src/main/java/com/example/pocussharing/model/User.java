package com.example.pocussharing.model;

import com.google.firebase.Timestamp;
import java.util.HashMap;
import java.util.Map;

public class User {
    private String userId;
    private String kakaoId;
    private String email;
    private String nickname;
    private String profileImageUrl;
    private String currentStatus; // OFFLINE, FOCUS, REST
    private Timestamp createdAt;
    private UserSettings settings;

    public User() {}

    public User(String userId, String kakaoId, String nickname) {
        this.userId = userId;
        this.kakaoId = kakaoId;
        this.nickname = nickname;
        this.currentStatus = "OFFLINE";
        this.createdAt = Timestamp.now();
        this.settings = new UserSettings();
    }

    // Getters and Setters
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

    public static class UserSettings {
        private boolean muteAlarms = false;
        private boolean keepScreenOn = true;
        private boolean preventExit = false;
        private Timestamp updatedAt;

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