package com.example.pocussharing.model;

import com.google.firebase.Timestamp;

/**
 * Group: 사용자가 속한 집중 그룹의 정보를 담는 모델 클래스
 */
public class Group {
    private String groupId;                  // 그룹 고유 ID (Firestore 문서 ID)
    private String groupName;                // 그룹 이름
    private String groupCode;                // 그룹 참여 코드 (6자리 영숫자)
    private int maxMembers;                  // 최대 참여 가능 인원
    private String adminId;                  // 그룹 관리자(생성자) ID
    private java.util.List<String> memberIds; // 그룹에 속한 전체 사용자 ID 리스트
    private Timestamp createdAt;             // 그룹 생성 일시

    // Firebase 연동을 위한 기본 생성자
    public Group() {}

    /**
     * 새로운 그룹 객체를 초기값과 함께 생성합니다.
     * @param groupName 그룹 이름
     * @param groupCode 그룹 참여 코드
     * @param adminId 관리자 ID
     */
    public Group(String groupName, String groupCode, String adminId) {
        this.groupName = groupName;
        this.groupCode = groupCode;
        this.adminId = adminId;
        this.memberIds = new java.util.ArrayList<>();
        this.memberIds.add(adminId); // 생성자를 첫 번째 멤버로 추가
        this.maxMembers = 12;        // 기본 최대 인원 설정
        this.createdAt = Timestamp.now();
    }

    // Getter 및 Setter 메서드들
    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }
    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }
    public String getGroupCode() { return groupCode; }
    public void setGroupCode(String groupCode) { this.groupCode = groupCode; }
    public int getMaxMembers() { return maxMembers; }
    public void setMaxMembers(int maxMembers) { this.maxMembers = maxMembers; }
    public String getAdminId() { return adminId; }
    public void setAdminId(String adminId) { this.adminId = adminId; }
    public java.util.List<String> getMemberIds() { return memberIds; }
    public void setMemberIds(java.util.List<String> memberIds) { this.memberIds = memberIds; }
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}