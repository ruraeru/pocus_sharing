package com.example.pocussharing.model;

import com.google.firebase.Timestamp;

/**
 * UserSettings: 알람, 화면 유지 등 사용자의 앱 설정 정보를 담는 내부 클래스
 */
public class UserSettings {
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
