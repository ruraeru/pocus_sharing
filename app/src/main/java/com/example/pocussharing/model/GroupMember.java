package com.example.pocussharing.model;

import com.google.firebase.Timestamp;

/**
 * GroupMember: 특정 그룹 내의 멤버 정보와 역할을 담는 모델 클래스
 */
public class GroupMember {
    private String userId;     // 사용자 고유 ID
    private String role;       // 역할 (ADMIN: 관리자, MEMBER: 일반 멤버)
    private Timestamp joinedAt; // 그룹 가입 일시

    // Firebase 연동을 위한 기본 생성자
    public GroupMember() {}

    /**
     * 새로운 그룹 멤버 객체를 생성합니다.
     * @param userId 사용자 ID
     * @param role 역할
     */
    public GroupMember(String userId, String role) {
        this.userId = userId;
        this.role = role;
        this.joinedAt = Timestamp.now();
    }

    // Getter 및 Setter 메서드들
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public Timestamp getJoinedAt() { return joinedAt; }
    public void setJoinedAt(Timestamp joinedAt) { this.joinedAt = joinedAt; }
}